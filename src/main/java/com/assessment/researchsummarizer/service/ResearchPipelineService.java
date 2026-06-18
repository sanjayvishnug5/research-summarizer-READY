package com.assessment.researchsummarizer.service;

import com.assessment.researchsummarizer.agent.InsightExtractorAgent;
import com.assessment.researchsummarizer.agent.ReportGeneratorAgent;
import com.assessment.researchsummarizer.agent.SearchAgent;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.InsightExtractorOutput;
import com.assessment.researchsummarizer.model.ResearchRequest;
import com.assessment.researchsummarizer.model.ResearchResponse;
import com.assessment.researchsummarizer.model.SearchAgentOutput;
import com.assessment.researchsummarizer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Research Pipeline Orchestrator
 *
 * Wires the three agents into a sequential pipeline:
 *   Agent 1 (SearchAgent) → Agent 2 (InsightExtractorAgent) → Agent 3 (ReportGeneratorAgent)
 *
 * Flow:
 *   ResearchRequest
 *            |
 *          SearchAgent          (fetch raw search results)
 *                   |
 *            InsightExtractorAgent  (extract structured insights)
 *                               |
 *                  ReportGeneratorAgent  (generate final report)
 *                                 |
 *                           ResearchResponse (REST response)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchPipelineService {

    private final AppConfig config;
    private final SearchAgent searchAgent;
    private final InsightExtractorAgent insightExtractorAgent;
    private final ReportGeneratorAgent reportGeneratorAgent;
    private final DemoModeService demoModeService;

    /**
     * Execute the full three-agent pipeline.
     *
     * @param request The incoming REST request
     * @return Final ResearchResponse
     */
    public ResearchResponse execute(ResearchRequest request) {
        log.info("Pipeline started | topic='{}' | maxSources={} | mode={}",
            request.getTopic(), request.getMaxSources(),
            config.isLiveMode() ? "LIVE" : "DEMO");

        if (!config.isLiveMode()) {
            log.info("Running in DEMO mode (no real API keys configured)");
            return demoModeService.generateDemoResponse(request);
        }

        // Stage 1: Search Agent
        SearchAgentOutput searchOutput = searchAgent.run(request.getTopic(), request.getMaxSources());

        // Stage 2: Insight Extractor
        InsightExtractorOutput insightOutput = insightExtractorAgent.run(searchOutput);

        //  Stage 3: Report Generator
        ResearchResponse response = reportGeneratorAgent.run(insightOutput, searchOutput);

        log.info("Pipeline completed | topic='{}' | source={}", response.getTopic(), response.getSearchSource());
        return response;
    }
}
