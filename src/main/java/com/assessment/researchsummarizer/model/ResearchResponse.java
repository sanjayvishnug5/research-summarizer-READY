package com.assessment.researchsummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Final REST API response — output of Agent 3 (ReportGeneratorAgent).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchResponse {

    private String topic;

    /** Which source was used: "MCP" or "WEB" */
    private String searchSource;

    private String executiveSummary;

    private List<String> keyFindings;

    private String details;

    private List<String> sources;
}
