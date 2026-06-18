package com.assessment.researchsummarizer.agent;

import com.assessment.researchsummarizer.client.McpClient;
import com.assessment.researchsummarizer.client.TavilyClient;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.SearchAgentOutput;
import com.assessment.researchsummarizer.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**

 *  AGENT 1 — Search Agent
 *
 *  Responsibilities:
 *    1. Classify the query as OPENAI_DEV or GENERAL using
 *       deterministic keyword matching (no LLM call needed).
 *    2. If OPENAI_DEV → query OpenAI Docs MCP server first.
 *       If MCP returns fewer than threshold results → fall back
 *       to Tavily web search.
 *    3. If GENERAL → use Tavily web search directly.
 *    4. Return SearchAgentOutput to Agent 2.

 *  Routing Logic (CRITICAL REQUIREMENT):

 *  The classification is purely keyword-based:
 *    - Lowercase the topic string
 *    - Check if any keyword from AppConfig.OPENAI_KEYWORDS appears
 *    - If yes → OPENAI_DEV (MCP preferred)
 *    - If no  → GENERAL   (web search)

 *  This is deterministic, fast, testable, and requires no LLM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchAgent {

    private final AppConfig config;
    private final McpClient mcpClient;
    private final TavilyClient tavilyClient;

    /**
     * Run Agent 1.
     *
     * @param topic      The research topic from the API request
     * @param maxSources Maximum number of search results to return
     * @return SearchAgentOutput containing raw results + metadata
     */
    public SearchAgentOutput run(String topic, int maxSources) {
        MDC.put("stage", "Agent1-SearchAgent");
        log.info("SearchAgent started | topic='{}' | maxSources={}", topic, maxSources);

        String queryType = classifyQuery(topic);
        log.info("SearchAgent classified | queryType={} | topic='{}'", queryType, topic);

        List<SearchResult> results;
        String searchSource;

        if ("OPENAI_DEV".equals(queryType)) {
            var searchOutput = searchWithMcpFallback(topic, maxSources);
            results = searchOutput.results();
            searchSource = searchOutput.source();
        } else {
            results = tavilyClient.search(topic, maxSources);
            searchSource = "WEB";
        }

        // Trim to maxSources
        if (results.size() > maxSources) {
            results = results.subList(0, maxSources);
        }

        log.info("SearchAgent completed | source={} | results={}", searchSource, results.size());
        MDC.remove("stage");

        return SearchAgentOutput.builder()
            .topic(topic)
            .queryType(queryType)
            .searchSource(searchSource)
            .results(results)
            .build();
    }

    //  Query Classification

    /**
     * Deterministic keyword-based query classification.
     *
     * Algorithm:
     *   1. Lowercase the topic.
     *   2. For each keyword in OPENAI_KEYWORDS:
     *      - If keyword is a substring of the topic → return OPENAI_DEV
     *   3. If no match found → return GENERAL
     *
     * No LLM call is made. This is intentional:
     *   - Zero latency overhead on classification
     *   - 100% deterministic (no hallucination risk)
     *   - Fully unit-testable
     */
    public String classifyQuery(String topic) {
        String lower = topic.toLowerCase();
        for (String keyword : AppConfig.OPENAI_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                log.debug("SearchAgent keyword match | keyword='{}' | topic='{}'", keyword, topic);
                return "OPENAI_DEV";
            }
        }
        return "GENERAL";
    }

    //  MCP with Tavily fallback

    private record SearchOutput(List<SearchResult> results, String source) {}

    private SearchOutput searchWithMcpFallback(String topic, int maxSources) {
        List<SearchResult> mcpResults = mcpClient.search(topic, maxSources);

        if (mcpResults.size() >= config.getMcpSufficientThreshold()) {
            log.info("SearchAgent MCP sufficient | count={}", mcpResults.size());
            return new SearchOutput(mcpResults, "MCP");
        }

        // MCP returned too few results — fall back to web
        log.info("SearchAgent MCP insufficient (count={}, threshold={}) — falling back to web",
            mcpResults.size(), config.getMcpSufficientThreshold());

        List<SearchResult> webResults = tavilyClient.search(topic, maxSources);

        // Merge: MCP results first, then web
        List<SearchResult> merged = new ArrayList<>(mcpResults);
        merged.addAll(webResults);

        // If MCP had any results, source is still MCP; otherwise WEB
        String source = mcpResults.isEmpty() ? "WEB" : "MCP";
        return new SearchOutput(merged, source);
    }
}
