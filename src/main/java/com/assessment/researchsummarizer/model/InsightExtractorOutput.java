package com.assessment.researchsummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full output of Agent 2 (InsightExtractorAgent).
 * Handed off to Agent 3 (ReportGeneratorAgent).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightExtractorOutput {

    private String topic;
    private List<Insight> insights;
}
