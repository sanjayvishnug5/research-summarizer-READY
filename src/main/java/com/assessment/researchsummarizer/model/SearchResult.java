package com.assessment.researchsummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single raw search result from either the MCP server or Tavily web search.
 * Passed from Agent 1 (SearchAgent) to Agent 2 (InsightExtractorAgent).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String title;
    private String url;
    private String snippet;

    /** "MCP" or "WEB" */
    private String source;
}
