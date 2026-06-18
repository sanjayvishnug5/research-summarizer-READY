package com.assessment.researchsummarizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Research Summarizer Agent — Spring Boot Application Entry Point
 *
 * Multi-agent system that accepts a research topic, routes it through
 * a three-agent pipeline (Search → Insight Extraction → Report Generation),
 * and returns a structured summary report.
 *
 * Run with:
 *   mvn spring-boot:run
 *
 * Or with real API keys:
 *   ANTHROPIC_API_KEY=sk-ant-... TAVILY_API_KEY=tvly-... mvn spring-boot:run
 *
 * Or via Docker:
 *   docker-compose up --build
 */
@SpringBootApplication
public class ResearchSummarizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchSummarizerApplication.class, args);
    }
}
