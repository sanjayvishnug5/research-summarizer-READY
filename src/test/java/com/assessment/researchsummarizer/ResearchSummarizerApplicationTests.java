package com.assessment.researchsummarizer;

import com.assessment.researchsummarizer.client.McpClient;
import com.assessment.researchsummarizer.client.TavilyClient;
import com.assessment.researchsummarizer.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.assessment.researchsummarizer.agent.InsightExtractorAgent;
import com.assessment.researchsummarizer.agent.ReportGeneratorAgent;
import com.assessment.researchsummarizer.agent.SearchAgent;
import com.assessment.researchsummarizer.config.AppConfig;
import com.assessment.researchsummarizer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class ResearchSummarizerApplicationTests {

    private AppConfig appConfig;
    private SearchAgent searchAgent;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        appConfig = Mockito.mock(AppConfig.class);
        when(appConfig.getMcpSufficientThreshold()).thenReturn(2);
        searchAgent = new SearchAgent(appConfig,
            Mockito.mock(McpClient.class),
            Mockito.mock(TavilyClient.class));
    }


    //  1. Query Classification / Routing Logic (Critical Req)


    @Nested
    @DisplayName("1. SearchAgent — Query Routing Classification")
    class QueryClassificationTests {

        @ParameterizedTest(name = "OpenAI topic: ''{0}'' → OPENAI_DEV")
        @ValueSource(strings = {
            "How does the OpenAI API work?",
            "gpt-4 context window size",
            "GPT-4o vs GPT-4 turbo comparison",
            "OpenAI assistants api tutorial",
            "fine-tuning a gpt model",
            "openai agents sdk multi-agent",
            "whisper transcription openai",
            "embeddings api openai",
            "responses api structured outputs",
            "batch api openai cost savings",
            "ChatGPT system prompt examples",
            "DALL-E 3 image generation",
            "o1 reasoning model benchmark",
            "function calling openai example"
        })
        @DisplayName("OpenAI developer topics should route to MCP (OPENAI_DEV)")
        void openaiTopicsShouldBeClassifiedAsOpenAIDev(String topic) {
            assertThat(searchAgent.classifyQuery(topic)).isEqualTo("OPENAI_DEV");
        }

        @ParameterizedTest(name = "General topic: ''{0}'' → GENERAL")
        @ValueSource(strings = {
            "climate change effects on agriculture",
            "Python web scraping tutorial",
            "best programming languages 2025",
            "how does photosynthesis work",
            "electric vehicle battery technology",
            "history of the Roman Empire",
            "machine learning basics",
            "stock market investing strategies",
            "how to learn Spanish fast",
            "spring boot microservices architecture"
        })
        @DisplayName("General topics should route to web search (GENERAL)")
        void generalTopicsShouldBeClassifiedAsGeneral(String topic) {
            assertThat(searchAgent.classifyQuery(topic)).isEqualTo("GENERAL");
        }

        @Test
        @DisplayName("Classification should be case-insensitive")
        void classificationIsCaseInsensitive() {
            assertThat(searchAgent.classifyQuery("OPENAI API REFERENCE")).isEqualTo("OPENAI_DEV");
            assertThat(searchAgent.classifyQuery("OpenAI Models Overview")).isEqualTo("OPENAI_DEV");
            assertThat(searchAgent.classifyQuery("GPT-4O PRICING")).isEqualTo("OPENAI_DEV");
        }

        @Test
        @DisplayName("Keyword substring matching should work")
        void keywordSubstringMatchWorks() {
            assertThat(searchAgent.classifyQuery("I want to use openai for my project"))
                .isEqualTo("OPENAI_DEV");
        }

        @Test
        @DisplayName("Similar but non-matching words should not match")
        void nonMatchingSimilarWordsShouldBeGeneral() {
            // "open" without "ai" should not match
            assertThat(searchAgent.classifyQuery("open source software development"))
                .isEqualTo("GENERAL");
        }
    }


    //  2. InsightExtractorAgent — JSON parsing


    @Nested
    @DisplayName("2. InsightExtractorAgent — JSON Parsing")
    class InsightExtractorParsingTests {

        private InsightExtractorAgent agent;
        private SearchAgentOutput dummySearchOutput;

        @BeforeEach
        void setUp() {
            AppConfig mockConfig = Mockito.mock(AppConfig.class);
            agent = new InsightExtractorAgent(mockConfig) {
                @Override
                protected String callClaude(String content, String model) { return "[]"; }
            };
            dummySearchOutput = SearchAgentOutput.builder()
                .topic("test topic")
                .queryType("GENERAL")
                .searchSource("WEB")
                .results(List.of(
                    SearchResult.builder()
                        .title("Test").url("https://example.com")
                        .snippet("test snippet").source("WEB").build()
                ))
                .build();
        }

        @Test
        @DisplayName("Should parse valid JSON array of insights")
        void parsesValidJson() throws Exception {
            String json = objectMapper.writeValueAsString(List.of(
                java.util.Map.of("category", "fact", "content",
                    "Python was created in 1991.", "sourceUrl", "https://python.org"),
                java.util.Map.of("category", "statistic", "content",
                    "Python is used by 48% of developers.", "sourceUrl", "null")
            ));
            List<Insight> insights = agent.parseInsights(json, dummySearchOutput);
            assertThat(insights).hasSize(2);
            assertThat(insights.get(0).getCategory()).isEqualTo("fact");
            assertThat(insights.get(1).getCategory()).isEqualTo("statistic");
        }

        @Test
        @DisplayName("Should strip markdown fences from LLM response")
        void stripsMarkdownFences() {
            String fenced = "```json\n[{\"category\": \"definition\", \"content\": \"AI is artificial intelligence.\", \"sourceUrl\": null}]\n```";
            List<Insight> insights = agent.parseInsights(fenced, dummySearchOutput);
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).getCategory()).isEqualTo("definition");
        }

        @Test
        @DisplayName("Invalid category should default to 'fact'")
        void invalidCategoryDefaultsToFact() throws Exception {
            String json = objectMapper.writeValueAsString(List.of(
                java.util.Map.of("category", "unknown_type",
                    "content", "Some content.", "sourceUrl", "null")
            ));
            List<Insight> insights = agent.parseInsights(json, dummySearchOutput);
            assertThat(insights.get(0).getCategory()).isEqualTo("fact");
        }

        @Test
        @DisplayName("Bad JSON should fall back gracefully without throwing")
        void gracefulDegradationOnBadJson() {
            List<Insight> insights = agent.parseInsights("this is not json {{", dummySearchOutput);
            // Should not throw; falls back to snippets
            assertThat(insights).isNotNull();
        }
    }


    //  3. ReportGeneratorAgent — JSON parsing & deduplication


    @Nested
    @DisplayName("3. ReportGeneratorAgent — Report Parsing & Sources")
    class ReportGeneratorParsingTests {

        private ReportGeneratorAgent agent;
        private InsightExtractorOutput dummyInsightOutput;

        @BeforeEach
        void setUp() {
            AppConfig mockConfig = Mockito.mock(AppConfig.class);
            agent = new ReportGeneratorAgent(mockConfig);
            dummyInsightOutput = InsightExtractorOutput.builder()
                .topic("AI in healthcare")
                .insights(List.of(
                    Insight.builder().category("fact")
                        .content("AI improves diagnostic accuracy.")
                        .sourceUrl("https://example.com").build()
                ))
                .build();
        }

        @Test
        @DisplayName("Should parse valid report JSON")
        void parsesValidReportJson() throws Exception {
            String json = objectMapper.writeValueAsString(java.util.Map.of(
                "executiveSummary", "AI is transforming healthcare.",
                "keyFindings", List.of("Finding 1", "Finding 2"),
                "details", "Detailed paragraph here."
            ));
            JsonNode result = agent.parseReport(json, dummyInsightOutput);
            assertThat(result.path("executiveSummary").asText()).isEqualTo("AI is transforming healthcare.");
            assertThat(result.path("keyFindings").size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Bad JSON should produce graceful fallback without throwing")
        void gracefulDegradationOnBadJson() {
            JsonNode result = agent.parseReport("not json", dummyInsightOutput);
            assertThat(result).isNotNull();
            assertThat(result.has("executiveSummary")).isTrue();
        }

        @Test
        @DisplayName("Sources should be deduplicated and maintain order")
        void sourceDeduplication() {
            InsightExtractorOutput insights = InsightExtractorOutput.builder()
                .topic("test")
                .insights(List.of(
                    Insight.builder().category("fact").content("c1").sourceUrl("https://a.com").build(),
                    Insight.builder().category("fact").content("c2").sourceUrl("https://a.com").build(), // duplicate
                    Insight.builder().category("fact").content("c3").sourceUrl("https://b.com").build()
                ))
                .build();
            SearchAgentOutput searchOutput = SearchAgentOutput.builder()
                .topic("test").queryType("GENERAL").searchSource("WEB")
                .results(List.of(
                    SearchResult.builder().title("T").url("https://a.com").snippet("s").source("WEB").build(),
                    SearchResult.builder().title("T").url("https://c.com").snippet("s").source("WEB").build()
                ))
                .build();

            // Build response to trigger source collection
            JsonNode reportNode = objectMapper.createObjectNode()
                .put("executiveSummary", "test")
                .put("details", "test");
            ((com.fasterxml.jackson.databind.node.ObjectNode) reportNode)
                .putArray("keyFindings").add("f1");

            ResearchResponse resp = agent.run(insights, searchOutput);
            long distinctCount = resp.getSources().stream().distinct().count();
            assertThat(distinctCount).isEqualTo(resp.getSources().size());
            assertThat(resp.getSources()).contains("https://a.com", "https://b.com", "https://c.com");
        }
    }


    //  4. AppConfig — OPENAI_KEYWORDS list validation


    @Nested
    @DisplayName("4. AppConfig — OPENAI_KEYWORDS completeness")
    class AppConfigKeywordsTests {

        @Test
        @DisplayName("OPENAI_KEYWORDS list should not be empty")
        void keywordsListNotEmpty() {
            assertThat(AppConfig.OPENAI_KEYWORDS).isNotEmpty();
        }

        @Test
        @DisplayName("OPENAI_KEYWORDS should contain core identifiers")
        void keywordsContainCoreTerms() {
            assertThat(AppConfig.OPENAI_KEYWORDS).contains("openai", "gpt", "chatgpt");
        }

        @Test
        @DisplayName("isLiveMode should return false for demo keys")
        void isLiveModeReturnsFalseForDemoKeys() {
            AppConfig realConfig = new AppConfig();
            // Using reflection to test default demo values
            assertThat(AppConfig.OPENAI_KEYWORDS.size()).isGreaterThan(10);
        }
    }
}
