package com.assessment.researchsummarizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Centralised application configuration.
 * Values are injected from application.properties / environment variables.
 */
@Configuration
public class AppConfig {

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${anthropic.model:claude-3-5-haiku-20241022}")
    private String anthropicModel;

    @Value("${anthropic.report-model:claude-3-5-sonnet-20241022}")
    private String anthropicReportModel;

    @Value("${anthropic.max-tokens:2048}")
    private int anthropicMaxTokens;

    @Value("${tavily.api-key}")
    private String tavilyApiKey;

    @Value("${tavily.base-url:https://api.tavily.com}")
    private String tavilyBaseUrl;

    @Value("${tavily.max-results:10}")
    private int tavilyMaxResults;

    @Value("${mcp.base-url:https://developers.openai.com/mcp}")
    private String mcpBaseUrl;

    @Value("${mcp.timeout-seconds:15}")
    private int mcpTimeoutSeconds;

    @Value("${mcp.sufficient-threshold:2}")
    private int mcpSufficientThreshold;

    // ── Getters ───────────────────────────────────────────────

    public String getAnthropicApiKey()     { return anthropicApiKey; }
    public String getAnthropicModel()      { return anthropicModel; }
    public String getAnthropicReportModel(){ return anthropicReportModel; }
    public int    getAnthropicMaxTokens()  { return anthropicMaxTokens; }
    public String getTavilyApiKey()        { return tavilyApiKey; }
    public String getTavilyBaseUrl()       { return tavilyBaseUrl; }
    public int    getTavilyMaxResults()    { return tavilyMaxResults; }
    public String getMcpBaseUrl()          { return mcpBaseUrl; }
    public int    getMcpTimeoutSeconds()   { return mcpTimeoutSeconds; }
    public int    getMcpSufficientThreshold() { return mcpSufficientThreshold; }

    /**
     * Keywords used to classify a query as OpenAI-developer-related.
     *
     * Routing decision:
     *   If ANY of these keywords appears (case-insensitive) in the topic → OPENAI_DEV → MCP server
     *   Otherwise → GENERAL → Tavily web search
     *
     * This is deterministic and requires no LLM call to classify.
     */
    public static final List<String> OPENAI_KEYWORDS = List.of(
        "openai", "gpt", "gpt-4", "gpt-3", "gpt4", "gpt3",
        "chatgpt", "dall-e", "dalle", "whisper",
        "fine-tuning", "fine tuning", "finetuning",
        "openai api", "completions api", "responses api",
        "assistants api", "agents sdk", "openai sdk",
        "openai models", "openai tools", "function calling",
        "structured outputs", "batch api", "vector store",
        "openai python", "openai node", "openai client",
        "o1", "o3", "o4", "gpt-4o", "gpt-4 turbo",
        "embeddings api", "moderation api"
    );

    /** Returns true if the API keys are real (not demo placeholders) */
    public boolean isLiveMode() {
        return !anthropicApiKey.equals("demo-mode")
            && !anthropicApiKey.startsWith("your_")
            && !tavilyApiKey.equals("demo-mode")
            && !tavilyApiKey.startsWith("your_");
    }
}
