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
import com.assessment.researchsummarizer.model.SearchAgentOutput;
import com.assessment.researchsummarizer.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**

 *  AGENT 2 — Insight Extractor Agent

 *
 *  Responsibilities:
 *    1. Receive raw search results from Agent 1.
 *    2. Use Claude (Haiku — fast + cost-efficient) to extract
 *       structured insights: facts, statistics, definitions, quotes.
 *    3. Return InsightExtractorOutput to Agent 3.
 *
 *  Tools: Claude Haiku via Anthropic Java SDK (LLM extraction)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightExtractorAgent {

    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a research insight extractor. Your job is to read raw search
        results and extract the most valuable, distinct insights.

        For each insight, classify it as one of:
        - "fact"       : a verifiable statement of fact
        - "statistic"  : a numerical or measurable data point
        - "definition" : an explanation of what something is
        - "quote"      : a direct or paraphrased quote worth highlighting

        Return ONLY a valid JSON array. No markdown fences, no preamble, no commentary.

        Format:
        [
          {
            "category": "fact" | "statistic" | "definition" | "quote",
            "content": "The insight text here.",
            "sourceUrl": "https://source.url or null"
          }
        ]

        Rules:
        - Extract 5 to 10 insights maximum.
        - Each insight must be self-contained and meaningful.
        - Do not duplicate insights.
        - Prefer specific, actionable, or surprising insights.
        - If a snippet is too vague, skip it.
        """;

    /**
     * Run Agent 2.
     *
     * @param searchOutput Output from Agent 1 (SearchAgent)
     * @return InsightExtractorOutput with structured insights
     */
    public InsightExtractorOutput run(SearchAgentOutput searchOutput) {
        MDC.put("stage", "Agent2-InsightExtractor");
        log.info("InsightExtractorAgent started | topic='{}' | results={}",
            searchOutput.getTopic(), searchOutput.getResults().size());

        if (searchOutput.getResults().isEmpty()) {
            log.warn("InsightExtractorAgent: no search results to process");
            MDC.remove("stage");
            return InsightExtractorOutput.builder()
                .topic(searchOutput.getTopic())
                .insights(List.of())
                .build();
        }

        String userContent = buildUserPrompt(searchOutput);
        String rawJson = callClaude(userContent, config.getAnthropicModel());
        List<Insight> insights = parseInsights(rawJson, searchOutput);

        log.info("InsightExtractorAgent completed | insights={}", insights.size());
        MDC.remove("stage");

        return InsightExtractorOutput.builder()
            .topic(searchOutput.getTopic())
            .insights(insights)
            .build();
    }

    //  Prompt construction

    private String buildUserPrompt(SearchAgentOutput searchOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("Research topic: ").append(searchOutput.getTopic()).append("\n");
        sb.append("Source type: ").append(searchOutput.getSearchSource()).append("\n\n");
        sb.append("Search results to extract insights from:\n\n");

        int i = 1;
        for (SearchResult result : searchOutput.getResults()) {
            sb.append("[").append(i++).append("] Title: ").append(result.getTitle()).append("\n");
            sb.append("    URL: ").append(result.getUrl()).append("\n");
            sb.append("    Snippet: ").append(result.getSnippet()).append("\n\n");
        }
        sb.append("Extract structured insights from the above results.");
        return sb.toString();
    }

    // Claude API call

    protected String callClaude(String userContent, String model) {
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
                .orElse("[]");

        } catch (Exception e) {
            log.error("InsightExtractorAgent Claude call failed | detail={}", e.getMessage());
            return "[]";
        }
    }

    //  JSON parsing

    public List<Insight> parseInsights(String rawJson, SearchAgentOutput searchOutput) {
        // Strip markdown fences if present
        String clean = rawJson.trim();
        if (clean.startsWith("```")) {
            String[] parts = clean.split("```");
            if (parts.length > 1) {
                clean = parts[1];
                if (clean.toLowerCase().startsWith("json")) {
                    clean = clean.substring(4);
                }
                clean = clean.trim();
            }
        }

        try {
            JsonNode array = objectMapper.readTree(clean);
            if (!array.isArray()) throw new IllegalArgumentException("Expected JSON array");

            List<Insight> insights = new ArrayList<>();
            for (JsonNode item : array) {
                String category = item.path("category").asText("fact");
                if (!List.of("fact", "statistic", "definition", "quote").contains(category)) {
                    category = "fact";
                }
                insights.add(Insight.builder()
                    .category(category)
                    .content(item.path("content").asText(""))
                    .sourceUrl(item.has("sourceUrl") && !item.path("sourceUrl").isNull()
                        ? item.path("sourceUrl").asText() : null)
                    .build());
            }
            return insights;

        } catch (Exception e) {
            log.error("InsightExtractorAgent parse failed | detail={} | raw={}",
                e.getMessage(), rawJson.substring(0, Math.min(rawJson.length(), 200)));

            // Graceful degradation: build insights from raw snippets
            return searchOutput.getResults().stream()
                .limit(5)
                .filter(r -> r.getSnippet() != null && !r.getSnippet().isBlank())
                .map(r -> Insight.builder()
                    .category("fact")
                    .content(r.getSnippet().substring(0, Math.min(r.getSnippet().length(), 300)))
                    .sourceUrl(r.getUrl())
                    .build())
                .toList();
        }
    }
}
