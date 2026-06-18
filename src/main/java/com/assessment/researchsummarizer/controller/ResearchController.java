package com.assessment.researchsummarizer.controller;

import com.assessment.researchsummarizer.model.ErrorResponse;
import com.assessment.researchsummarizer.model.ResearchRequest;
import com.assessment.researchsummarizer.model.ResearchResponse;
import com.assessment.researchsummarizer.service.ResearchPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Research Summarizer", description = "Multi-Agent Research Summarizer API")
public class ResearchController {

    private final ResearchPipelineService pipelineService;

    @Operation(
        summary = "Summarize a research topic",
        description = """
            Accepts a research topic and routes it through a 3-agent pipeline:
            
            **Agent 1 — Search Agent**
            Classifies the query. If OpenAI-related → queries OpenAI Docs MCP server.
            Otherwise → Tavily web search. Falls back to web if MCP is insufficient.
            
            **Agent 2 — Insight Extractor**
            Uses Claude Haiku to extract facts, statistics, definitions, and quotes.
            
            **Agent 3 — Report Generator**
            Uses Claude Sonnet to produce a structured report with Executive Summary,
            Key Findings, Details, and Sources.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Research report generated successfully",
            content = @Content(schema = @Schema(implementation = ResearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Pipeline error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/research/summarize")
    public ResponseEntity<?> summarize(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Research topic to summarize",
            content = @Content(examples = {
                @ExampleObject(name = "OpenAI Topic (MCP Route)",
                    value = "{\"topic\": \"OpenAI GPT-4o capabilities and pricing\", \"maxSources\": 5}"),
                @ExampleObject(name = "General Topic (Web Route)",
                    value = "{\"topic\": \"electric vehicle battery technology 2025\", \"maxSources\": 5}")
            })
        )
        @Valid @RequestBody ResearchRequest request) {

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("traceId", traceId);

        log.info("Request received | topic='{}' | maxSources={} | traceId={}",
            request.getTopic(), request.getMaxSources(), traceId);

        try {
            ResearchResponse response = pipelineService.execute(request);
            log.info("Request completed | topic='{}' | source={}", response.getTopic(), response.getSearchSource());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Pipeline error | detail={} | traceId={}", e.getMessage(), traceId);
            return ResponseEntity.internalServerError().body(
                ErrorResponse.builder()
                    .error("Pipeline error")
                    .detail(e.getMessage())
                    .traceId(traceId)
                    .build()
            );
        } finally {
            MDC.remove("traceId");
            MDC.remove("stage");
        }
    }

    @Operation(summary = "Health check", description = "Returns service status")
    @GetMapping("/api/research/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "research-summarizer-agent",
            "version", "1.0.0"
        ));
    }

    @Operation(summary = "Root info", description = "Service information and available endpoints")
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of(
            "service", "Research Summarizer Agent",
            "version", "1.0.0",
            "endpoint", "POST /api/research/summarize",
            "swagger", "http://localhost:8080/swagger-ui.html",
            "health", "http://localhost:8080/api/research/health"
        ));
    }
}
