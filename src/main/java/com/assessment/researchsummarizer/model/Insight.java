package com.assessment.researchsummarizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single extracted insight produced by Agent 2 (InsightExtractorAgent).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Insight {

    /** "fact" | "statistic" | "definition" | "quote" */
    private String category;

    private String content;

    private String sourceUrl;
}
