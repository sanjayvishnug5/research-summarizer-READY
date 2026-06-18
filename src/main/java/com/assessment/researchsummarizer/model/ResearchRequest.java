package com.assessment.researchsummarizer.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound REST request body for POST /api/research/summarize
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchRequest {

    @NotBlank(message = "topic must not be blank")
    @Size(min = 3, max = 500, message = "topic must be between 3 and 500 characters")
    private String topic;

    @Min(value = 1, message = "maxSources must be at least 1")
    @Max(value = 20, message = "maxSources must not exceed 20")
    @Builder.Default
    private int maxSources = 5;
}
