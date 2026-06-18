package com.assessment.researchsummarizer.service;

import com.assessment.researchsummarizer.agent.SearchAgent;
import com.assessment.researchsummarizer.client.McpClient;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.ResearchRequest;
import com.assessment.researchsummarizer.model.ResearchResponse;
import com.assessment.researchsummarizer.model.SearchResult;
import com.assessment.researchsummarizer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Demo Mode Service
 *
 * Returns realistic mock data when no real API keys are configured.
 * Still attempts to contact the real OpenAI MCP server for
 * OpenAI-related queries — uses demo data as fallback.
 *
 * This allows the full pipeline to be demonstrated without API costs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoModeService {

    private final SearchAgent searchAgent;
    private final McpClient mcpClient;
    private final AppConfig config;

    public ResearchResponse generateDemoResponse(ResearchRequest request) {
        String topic = request.getTopic();
        String queryType = searchAgent.classifyQuery(topic);
        boolean isOpenAi = "OPENAI_DEV".equals(queryType);

        log.info("DemoMode | topic='{}' | queryType={}", topic, queryType);

        // Try real MCP server even in demo mode
        List<SearchResult> results;
        String source;

        if (isOpenAi) {
            List<SearchResult> mcpResults = mcpClient.search(topic, request.getMaxSources());
            if (mcpResults.size() >= config.getMcpSufficientThreshold()) {
                results = mcpResults;
                source = "MCP";
                log.info("DemoMode: using real MCP results | count={}", results.size());
            } else {
                results = OPENAI_DEMO_RESULTS.stream().limit(request.getMaxSources()).collect(Collectors.toList());
                source = "MCP";
                log.info("DemoMode: using mock MCP results");
            }
        } else {
            results = WEB_DEMO_RESULTS.stream().limit(request.getMaxSources()).collect(Collectors.toList());
            source = "WEB";
        }

        List<String> sources = results.stream()
            .map(SearchResult::getUrl)
            .filter(url -> url != null && !url.isBlank())
            .distinct()
            .limit(request.getMaxSources())
            .collect(Collectors.toList());

        if (isOpenAi) {
            return ResearchResponse.builder()
                .topic(topic)
                .searchSource(source)
                .executiveSummary(OPENAI_EXECUTIVE_SUMMARY)
                .keyFindings(OPENAI_KEY_FINDINGS)
                .details(OPENAI_DETAILS)
                .sources(sources)
                .build();
        } else {
            return ResearchResponse.builder()
                .topic(topic)
                .searchSource(source)
                .executiveSummary(WEB_EXECUTIVE_SUMMARY)
                .keyFindings(WEB_KEY_FINDINGS)
                .details(WEB_DETAILS)
                .sources(sources)
                .build();
        }
    }

    // OpenAI Demo Data

    private static final List<SearchResult> OPENAI_DEMO_RESULTS = List.of(
        SearchResult.builder().title("GPT-4o — OpenAI Platform Documentation")
            .url("https://platform.openai.com/docs/models/gpt-4o")
            .snippet("GPT-4o is OpenAI's most capable and cost-efficient flagship model. It accepts text, image, and audio inputs. It has a 128,000 token context window and knowledge cutoff of October 2023.")
            .source("MCP").build(),
        SearchResult.builder().title("OpenAI Models Overview")
            .url("https://platform.openai.com/docs/models")
            .snippet("OpenAI offers GPT-4o, GPT-4o mini, o1, and o3 model families. GPT-4o delivers the best performance for most tasks while GPT-4o mini provides a cost-efficient option.")
            .source("MCP").build(),
        SearchResult.builder().title("Pricing — OpenAI API")
            .url("https://openai.com/api/pricing")
            .snippet("GPT-4o is priced at $2.50 per 1M input tokens and $10.00 per 1M output tokens. Batch API requests receive a 50% discount on all models.")
            .source("MCP").build(),
        SearchResult.builder().title("OpenAI Assistants API — File Search")
            .url("https://platform.openai.com/docs/assistants/tools/file-search")
            .snippet("The file search tool augments the Assistant with knowledge from files uploaded to OpenAI. Supports up to 10,000 files per assistant with automatic chunking and vector search.")
            .source("MCP").build(),
        SearchResult.builder().title("Function Calling — OpenAI Platform")
            .url("https://platform.openai.com/docs/guides/function-calling")
            .snippet("Function calling allows you to connect GPT models to external tools. The model determines when to call a function and outputs structured JSON matching your schema.")
            .source("MCP").build()
    );

    private static final String OPENAI_EXECUTIVE_SUMMARY =
        "OpenAI's GPT-4o is the company's flagship multimodal model, natively processing text, image, and audio " +
        "inputs within a single architecture. It leads the market in capability-to-cost ratio and powers a broad " +
        "ecosystem of APIs including Assistants, function calling, and batch processing.";

    private static final List<String> OPENAI_KEY_FINDINGS = List.of(
        "GPT-4o is a truly multimodal model that handles text, image, and audio natively — not via separate pipelines.",
        "The model offers a 128,000 token context window, one of the largest available commercially.",
        "API pricing starts at $2.50/1M input tokens; the Batch API offers a 50% discount for async workloads.",
        "The Assistants API supports file search over up to 10,000 files using managed vector stores.",
        "Function calling enables reliable structured JSON output for tool and API integrations.",
        "The o1/o3 reasoning model family is optimised for complex multi-step problems in science and coding."
    );

    private static final String OPENAI_DETAILS =
        "GPT-4o represents a consolidation of OpenAI's multimodal research into a single production model. " +
        "Unlike earlier approaches where text, vision, and speech were handled by separate models, GPT-4o processes " +
        "all modalities end-to-end, reducing latency and improving cross-modal reasoning. The 128K context window " +
        "enables processing of entire codebases or lengthy documents in a single request. On pricing, at $2.50 per " +
        "million input tokens with a Batch API that halves costs for background workloads, OpenAI has made the model " +
        "competitive. The Assistants API builds managed retrieval-augmented generation on top of the base model via " +
        "the file search tool, which automatically handles chunking, embedding, and semantic retrieval from uploaded " +
        "documents. Function calling has become the standard integration pattern for agentic applications, allowing " +
        "developers to define tool schemas that the model reliably conforms to.";

    // ── Web Demo Data ─────────────────────────────────────────

    private static final List<SearchResult> WEB_DEMO_RESULTS = List.of(
        SearchResult.builder().title("Electric Vehicle Market Report 2025 — Bloomberg NEF")
            .url("https://about.bnef.com/electric-vehicle-outlook")
            .snippet("EV sales surpassed 18 million units globally in 2024. Average battery pack costs have fallen below $100/kWh for the first time. China accounts for 60% of global EV sales.")
            .source("WEB").build(),
        SearchResult.builder().title("Solid-State Batteries: The Next Frontier")
            .url("https://www.reuters.com/technology/solid-state-batteries-2025")
            .snippet("Toyota, Samsung SDI, and QuantumScape are leading solid-state battery commercialisation with energy densities exceeding 400 Wh/kg in lab conditions.")
            .source("WEB").build(),
        SearchResult.builder().title("Climate Change: Causes, Effects and Solutions 2025")
            .url("https://www.nationalgeographic.com/environment/climate-change")
            .snippet("Global average temperatures have risen by approximately 1.2°C since pre-industrial times. The IPCC warns that limiting warming to 1.5°C requires cutting global emissions by 45% by 2030.")
            .source("WEB").build(),
        SearchResult.builder().title("AI in Healthcare 2025 — Nature Medicine")
            .url("https://www.nature.com/articles/ai-healthcare-2025")
            .snippet("AI diagnostic tools have achieved accuracy rates exceeding 95% for detecting diabetic retinopathy and skin cancer. The FDA has approved over 500 AI-based medical devices as of 2024.")
            .source("WEB").build(),
        SearchResult.builder().title("Python Developer Survey 2025 — Stack Overflow")
            .url("https://stackoverflow.com/survey/2025")
            .snippet("Python remains the most popular programming language for the 4th consecutive year. 48% of respondents use Python professionally. AI and ML adoption drives continued growth.")
            .source("WEB").build()
    );

    private static final String WEB_EXECUTIVE_SUMMARY =
        "The electric vehicle market reached a critical inflection point in 2025, with global sales exceeding " +
        "18 million units and battery costs falling below the $100/kWh threshold. Next-generation solid-state " +
        "battery technology is approaching commercial deployment, promising further range and safety improvements.";

    private static final List<String> WEB_KEY_FINDINGS = List.of(
        "Global EV sales exceeded 18 million units in 2024, with China representing 60% of worldwide demand.",
        "Battery pack costs have fallen below $100/kWh — the threshold widely considered necessary for ICE price parity.",
        "Solid-state batteries from Toyota, Samsung SDI, and QuantumScape have demonstrated over 400 Wh/kg in labs.",
        "Global temperatures are 1.2°C above pre-industrial levels, driving regulatory pressure for faster EV adoption.",
        "AI diagnostic tools now exceed 95% accuracy for detecting key diseases, with 500+ FDA-approved AI medical devices.",
        "Python remains the dominant language for AI/ML development, used professionally by 48% of developers."
    );

    private static final String WEB_DETAILS =
        "The EV industry in 2025 is defined by rapidly declining costs and maturing technology. " +
        "The fall of average battery pack prices below $100/kWh marks the point at which EVs become cost-competitive " +
        "with traditional vehicles over their lifetime. This reduction is driven by scale, improved lithium iron " +
        "phosphate chemistry, and manufacturing efficiencies in Chinese gigafactories. Simultaneously, solid-state " +
        "battery technology is transitioning from laboratory curiosity to limited commercial production, with energy " +
        "densities 50-80% higher than conventional lithium-ion. The climate context remains urgent: with 1.2°C of " +
        "warming locked in and the 1.5°C carbon budget narrowing, electrification of transport is a central pillar " +
        "of national decarbonisation strategies across the EU, US, and China.";
}
