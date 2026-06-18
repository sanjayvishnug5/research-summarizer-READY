package com.assessment.researchsummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full output of Agent 1 (SearchAgent).
 * Handed off to Agent 2 (InsightExtractorAgent).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchAgentOutput {

    private String topic;

    /** "OPENAI_DEV" or "GENERAL" */
    private String queryType;

    /** "MCP" or "WEB" — which source was actually used */
    private String searchSource;

    private List<SearchResult> results;
}
