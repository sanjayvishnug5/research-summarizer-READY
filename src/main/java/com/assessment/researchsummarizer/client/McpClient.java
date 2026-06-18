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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) Client
 *
 * Connects to https://developers.openai.com/mcp using the
 * Streamable HTTP transport as specified in the assignment.
 *
 * The server is public, read-only, and requires no authentication.
 * JSON-RPC 2.0 protocol over HTTP POST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClient {

    private final AppConfig config;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Search the OpenAI Docs MCP server.
     *
     * @param query      The research query
     * @param maxResults Maximum number of results to retrieve
     * @return List of SearchResult objects (empty if server unreachable or returns nothing)
     */
    public List<SearchResult> search(String query, int maxResults) {
        log.info("MCP search started | query='{}' | url={}", query, config.getMcpBaseUrl());

        try {
            Map<String, Object> payload = buildJsonRpcPayload(query, maxResults);
            String body = objectMapper.writeValueAsString(payload);

            String response = webClientBuilder.build()
                .post()
                .uri(config.getMcpBaseUrl())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(config.getMcpTimeoutSeconds()))
                .block();

            List<SearchResult> results = parseResponse(response);
            log.info("MCP search completed | results={}", results.size());
            return results;

        } catch (WebClientResponseException e) {
            log.warn("MCP HTTP error | status={} | detail={}", e.getStatusCode(), e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("MCP request failed | detail={}", e.getMessage());
            return List.of();
        }
    }

    //  Private helpers

    private Map<String, Object> buildJsonRpcPayload(String query, int maxResults) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);
        arguments.put("limit", maxResults);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "search");
        params.put("arguments", arguments);

        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 1);
        payload.put("method", "tools/call");
        payload.put("params", params);

        return payload;
    }

    private List<SearchResult> parseResponse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        List<SearchResult> results = new ArrayList<>();

        // Handle SSE (Server-Sent Events) stream
        if (raw.contains("data:")) {
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]") || data.isEmpty()) continue;
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        results.addAll(extractItems(chunk));
                    } catch (Exception ignored) {}
                }
            }
            return results;
        }

        // Plain JSON response
        try {
            JsonNode root = objectMapper.readTree(raw);
            return extractItems(root);
        } catch (Exception e) {
            log.warn("MCP parse failed | preview={}", raw.substring(0, Math.min(raw.length(), 200)));
            return List.of();
        }
    }

    private List<SearchResult> extractItems(JsonNode root) {
        List<SearchResult> items = new ArrayList<>();
        try {
            JsonNode content = root.path("result").path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        String text = block.path("text").asText();
                        JsonNode inner = objectMapper.readTree(text);
                        if (inner.isArray()) {
                            for (JsonNode item : inner) {
                                items.add(SearchResult.builder()
                                    .title(item.path("title").asText("OpenAI Docs"))
                                    .url(item.path("url").asText(config.getMcpBaseUrl()))
                                    .snippet(item.path("description").asText(
                                             item.path("content").asText("")))
                                    .source("MCP")
                                    .build());
                            }
                        } else if (inner.isObject()) {
                            items.add(SearchResult.builder()
                                .title(inner.path("title").asText("OpenAI Docs"))
                                .url(inner.path("url").asText(config.getMcpBaseUrl()))
                                .snippet(inner.path("description").asText(
                                         inner.path("content").asText("")))
                                .source("MCP")
                                .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MCP extract error | detail={}", e.getMessage());
        }
        return items;
    }
}
