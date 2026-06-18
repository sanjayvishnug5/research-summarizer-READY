package com.assessment.researchsummarizer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily Web Search Client
 *
 * Calls the Tavily Search API (https://api.tavily.com/search)
 * to perform general web searches as the fallback source.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TavilyClient {

    private final AppConfig config;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Perform a web search via Tavily.
     *
     * @param query      The research query
     * @param maxResults Maximum number of results
     * @return List of SearchResult objects (empty on failure)
     */
    public List<SearchResult> search(String query, int maxResults) {
        log.info("Tavily web search | query='{}'", query);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("api_key", config.getTavilyApiKey());
            payload.put("query", query);
            payload.put("max_results", Math.min(maxResults, config.getTavilyMaxResults()));
            payload.put("search_depth", "advanced");
            payload.put("include_answer", false);
            payload.put("include_raw_content", false);

            String body = objectMapper.writeValueAsString(payload);

            String response = webClientBuilder.build()
                .post()
                .uri(config.getTavilyBaseUrl() + "/search")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .block();

            List<SearchResult> results = parseResponse(response);
            log.info("Tavily search completed | results={}", results.size());
            return results;

        } catch (WebClientResponseException e) {
            log.warn("Tavily HTTP error | status={} | detail={}", e.getStatusCode(), e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Tavily search failed | detail={}", e.getMessage());
            return List.of();
        }
    }

    private List<SearchResult> parseResponse(String raw) {
        List<SearchResult> results = new ArrayList<>();
        if (raw == null || raw.isBlank()) return results;

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    results.add(SearchResult.builder()
                        .title(item.path("title").asText("Web Result"))
                        .url(item.path("url").asText(""))
                        .snippet(item.path("content").asText(
                                 item.path("snippet").asText("")))
                        .source("WEB")
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Tavily parse failed | detail={}", e.getMessage());
        }
        return results;
    }
}
