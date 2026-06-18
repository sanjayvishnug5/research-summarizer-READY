package com.assessment.researchsummarizer.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.Insight;
import com.assessment.researchsummarizer.model.InsightExtractorOutput;
import com.assessment.researchsummarizer.model.ResearchResponse;
import com.assessment.researchsummarizer.model.SearchAgentOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**

 *  AGENT 3 — Report Generator Agent
 *  Responsibilities:
 *    1. Receive structured insights from Agent 2.
 *    2. Use Claude Sonnet (higher quality) to generate a
 *       polished report: Executive Summary, Key Findings,
 *       Details, and Sources.
 *    3. Return the final ResearchResponse (REST API payload).

 *  Tools: Claude Sonnet via Anthropic Java SDK (text generation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGeneratorAgent {

    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a professional research report writer. You receive structured
        insights about a topic and produce a concise, well-organised summary report.

        Return ONLY a valid JSON object (no markdown fences, no preamble) with exactly these keys:

        {
          "executiveSummary": "2-3 sentence high-level overview of the topic",
          "keyFindings": [
            "Clear, standalone finding 1",
            "Clear, standalone finding 2",
            "...(3 to 7 findings total)"
          ],
          "details": "A detailed paragraph (150-250 words) expanding on the most important insights."
        }

        Rules:
        - executiveSummary: neutral, factual, 2-3 sentences max.
        - keyFindings: 3 to 7 bullet-style strings, each a complete standalone sentence.
        - details: flowing prose, no bullet points, no headers.
        - Be specific — use numbers, names, and specifics from the insights where available.
        - Do not hallucinate. Only use what the insights provide.
        """;

    /**
     * Run Agent 3.
     *
     * @param insightOutput Output from Agent 2 (InsightExtractorAgent)
     * @param searchOutput  Output from Agent 1 (for source URLs)
     * @return Final ResearchResponse
     */
    public ResearchResponse run(InsightExtractorOutput insightOutput, SearchAgentOutput searchOutput) {
        MDC.put("stage", "Agent3-ReportGenerator");
        log.info("ReportGeneratorAgent started | topic='{}' | insights={}",
            insightOutput.getTopic(), insightOutput.getInsights().size());

        String userContent = buildUserPrompt(insightOutput);
        String rawJson = callClaude(userContent, config.getAnthropicReportModel());
        JsonNode reportData = parseReport(rawJson, insightOutput);

        List<String> sources = collectSources(insightOutput, searchOutput);

        List<String> keyFindings = new ArrayList<>();
        if (reportData.has("keyFindings") && reportData.path("keyFindings").isArray()) {
            for (JsonNode f : reportData.path("keyFindings")) {
                keyFindings.add(f.asText());
            }
        }

        ResearchResponse response = ResearchResponse.builder()
            .topic(insightOutput.getTopic())
            .searchSource(searchOutput.getSearchSource())
            .executiveSummary(reportData.path("executiveSummary").asText(""))
            .keyFindings(keyFindings)
            .details(reportData.path("details").asText(""))
            .sources(sources)
            .build();

        log.info("ReportGeneratorAgent completed | topic='{}'", response.getTopic());
        MDC.remove("stage");
        return response;
    }

    // Prompt construction

    private String buildUserPrompt(InsightExtractorOutput insightOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("Research topic: ").append(insightOutput.getTopic()).append("\n\n");
        sb.append("Extracted insights:\n\n");

        int i = 1;
        for (Insight insight : insightOutput.getInsights()) {
            sb.append("[").append(i++).append("] [").append(insight.getCategory().toUpperCase())
              .append("] ").append(insight.getContent()).append("\n");
            if (insight.getSourceUrl() != null) {
                sb.append("    Source: ").append(insight.getSourceUrl()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Generate a professional research summary report from the above insights.");
        return sb.toString();
    }

    //  Claude API call

    String callClaude(String userContent, String model) {
        try {
            AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(config.getAnthropicApiKey())
                .build();

            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(config.getAnthropicMaxTokens())
                .system(SYSTEM_PROMPT)
                .addUserMessage(userContent)
                .build();

            Message message = client.messages().create(params);

            return message.content().stream()
                .filter(block -> block.isText())
                .map(block -> block.text().get().text())
                .findFirst()
                .orElse("{}");

        } catch (Exception e) {
            log.error("ReportGeneratorAgent Claude call failed | detail={}", e.getMessage());
            return "{}";
        }
    }

    //  JSON parsing

    public JsonNode parseReport(String rawJson, InsightExtractorOutput insightOutput) {
        String clean = rawJson.trim();
        if (clean.startsWith("```")) {
            String[] parts = clean.split("```");
            if (parts.length > 1) {
                clean = parts[1];
                if (clean.toLowerCase().startsWith("json")) clean = clean.substring(4);
                clean = clean.trim();
            }
        }

        try {
            JsonNode node = objectMapper.readTree(clean);
            if (node.isObject()) return node;
            throw new IllegalArgumentException("Expected JSON object");
        } catch (Exception e) {
            log.error("ReportGeneratorAgent parse failed | detail={}", e.getMessage());
            // Graceful degradation
            try {
                String fallback = String.format(
                    "{\"executiveSummary\": \"Research summary for: %s\", " +
                    "\"keyFindings\": [\"%s\"], \"details\": \"%s\"}",
                    insightOutput.getTopic(),
                    insightOutput.getInsights().isEmpty() ? "No insights found."
                        : insightOutput.getInsights().get(0).getContent().replace("\"", "'"),
                    insightOutput.getInsights().stream()
                        .limit(3)
                        .map(i -> i.getContent().replace("\"", "'"))
                        .reduce("", (a, b) -> a + " " + b).trim()
                );
                return objectMapper.readTree(fallback);
            } catch (Exception ex) {
                return objectMapper.createObjectNode();
            }
        }
    }

    //  Source deduplication

    private List<String> collectSources(InsightExtractorOutput insightOutput,
                                        SearchAgentOutput searchOutput) {
        Set<String> seen = new LinkedHashSet<>();

        // Insight-level sources first
        for (Insight insight : insightOutput.getInsights()) {
            if (insight.getSourceUrl() != null && !insight.getSourceUrl().isBlank()) {
                seen.add(insight.getSourceUrl());
            }
        }
        // Search result URLs as fallback
        for (var result : searchOutput.getResults()) {
            if (result.getUrl() != null && !result.getUrl().isBlank()) {
                seen.add(result.getUrl());
            }
        }
        return new ArrayList<>(seen);
    }
}
