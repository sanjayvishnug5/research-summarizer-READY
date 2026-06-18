SETUP GUIDE — Research Summarizer Agent


WHAT THIS PROJECT DOES

You send a research topic -> 3 AI agents work together → You get a structured report.


You type a topic
      |
Agent 1 (SearchAgent)
  → Is it OpenAI-related? (checks keywords)
  → YES → Searches OpenAI Docs (MCP Server)
  → NO  → Searches the Web (Tavily)
      |
      
Agent 2 (InsightExtractorAgent)
  → Reads search results
  → Extracts: facts, statistics, definitions, quotes
  → Uses Claude Haiku AI
      |
      
Agent 3 (ReportGeneratorAgent)
  → Takes all insights
  → Writes: Executive Summary + Key Findings + Details
  → Uses Claude Sonnet AI
      |
      
You get a clean JSON report 


INSTALL REQUIRED SOFTWARE

1. Install Java 21    -> https://adoptium.net
2. Install Maven      -> https://maven.apache.org/download.cgi
3. Get Anthropic key  -> https://console.anthropic.com
4. Get Tavily key     -> https://app.tavily.com
Install IntelliJ IDEA -> https://www.jetbrains.com/idea/download/
5.Set Your API Keys in IntelliJ

 Environment Variables in Run Config 

1. In IntelliJ -> top right -> click the dropdown next to the Run button
2. Click  "Edit Configurations" 
3. Click the **"+"** → **"Application"**
4. Name it: `ResearchSummarizer`
5. Main class: `com.assessment.researchsummarizer.ResearchSummarizerApplication`
6. Under **"Environment variables"** -> click the folder icon
7. Add these two:

   Name: ANTHROPIC_API_KEY    Value: sk-ant-api03-your-key-here
   Name: TAVILY_API_KEY       Value: tvly-your-key-here


 BUILD AND RUN

 Build the Project

Using IntelliJ:
1. Open: `src/main/java/com/assessment/researchsummarizer/ResearchSummarizerApplication.java`
2. Click the green button next to `public static void main`
3. Or press **Shift+F10**


Server is now running at http://localhost:8080

 TEST THE API

 Using Swagger UI 

1. Open your browser
2. Go to: http://localhost:8080/swagger-ui.html
3. Click "POST /api/research/summarize"
4. Click "Try it out"
6. Paste this in the body or type what ever you need
json
{
  "topic": "OpenAI GPT-4o capabilities and pricing",
  "maxSources": 5
}

7. Click "Execute"


General topic (should show searchSource: "WEB"):
 POST http://localhost:8080/api/research/summarize 

   {
   "topic": "electric vehicle battery technology 2025",
    "maxSources": 5
    }


Health check:

http://localhost:8080/api/research/health


 EXPECTED RESPONSES SAMPLE OUTPUT

 OpenAI Topic → MCP Route
 json
{
  "topic": "OpenAI GPT-4o capabilities and pricing",
  "searchSource": "MCP",
  "executiveSummary": "GPT-4o is OpenAI's flagship multimodal model...",
  "keyFindings": [
    "GPT-4o handles text, image and audio natively",
    "128,000 token context window",
    "Priced at $2.50 per 1M input tokens"
  ],
  "details": "GPT-4o represents a consolidation of OpenAI's...",
  "sources": [
    "https://platform.openai.com/docs/models/gpt-4o",
    "https://openai.com/api/pricing"
  ]
}


 General Topic → Web Route
```json
{
  "topic": "electric vehicle battery technology 2025",
  "searchSource": "WEB",
  "executiveSummary": "The EV market reached a critical inflection point...",
  "keyFindings": [
    "Global EV sales exceeded 18 million units in 2024",
    "Battery costs fell below $100/kWh for the first time",
    "Solid-state batteries approaching commercialisation"
  ],
  "details": "In 2025, EV battery technology is defined by...",
  "sources": [
    "https://about.bnef.com/electric-vehicle-outlook",
    "https://www.reuters.com/technology/solid-state-batteries-2025"
  ]
}


PROJECT FILE STRUCTURE

research-summarizer-java/
│
├── 📄 SETUP_GUIDE.md              ← You are reading this
├── 📄 README.md                   ← Technical documentation
├── 📄 pom.xml                     ← Maven dependencies
├── 📄 Dockerfile                  ← Docker build file
├── 📄 docker-compose.yml          ← Docker run config
│
└── src/
    ├── main/
    │   ├── java/com/assessment/researchsummarizer/
    │   │   │
    │   │   ├── ResearchSummarizerApplication.java  ← START HERE (main class)
    │   │   │
    │   │   ├── agent/                              ← THE 3 AGENTS
    │   │   │   ├── SearchAgent.java                ← Agent 1: Search + Routing
    │   │   │   ├── InsightExtractorAgent.java      ← Agent 2: Extract insights
    │   │   │   └── ReportGeneratorAgent.java       ← Agent 3: Write report
    │   │   │
    │   │   ├── client/                             ← EXTERNAL API CLIENTS
    │   │   │   ├── McpClient.java                  ← OpenAI MCP server client
    │   │   │   └── TavilyClient.java               ← Web search client
    │   │   │
    │   │   ├── config/                             ← CONFIGURATION
    │   │   │   ├── AppConfig.java                  ← API keys, keywords list
    │   │   │   └── WebClientConfig.java            ← HTTP client setup
    │   │   │
    │   │   ├── controller/                         ← REST API LAYER
    │   │   │   ├── ResearchController.java         ← API endpoints
    │   │   │   └── GlobalExceptionHandler.java     ← Error handling
    │   │   │
    │   │   ├── model/                              ← DATA MODELS
    │   │   │   ├── ResearchRequest.java            ← What you send in
    │   │   │   ├── ResearchResponse.java           ← What you get back
    │   │   │   ├── SearchResult.java               ← Search result
    │   │   │   ├── SearchAgentOutput.java          ← Agent 1 output
    │   │   │   ├── Insight.java                    ← Single insight
    │   │   │   ├── InsightExtractorOutput.java     ← Agent 2 output
    │   │   │   └── ErrorResponse.java              ← Error body
    │   │   │
    │   │   └── service/                            ← BUSINESS LOGIC
    │   │       ├── ResearchPipelineService.java    ← Connects all 3 agents
    │   │       └── DemoModeService.java            ← Works without API keys
    │   │
    │   └── resources/
    │       ├── application.properties              ← App settings
    │       └── logback-spring.xml                  ← Logging config
    │
    └── test/
        └── java/com/assessment/researchsummarizer/
            └── ResearchSummarizerApplicationTests.java  ← Unit tests




QUICK START SUMMARY
1. Install Java 21     → https://adoptium.net
2. Install Maven       → https://maven.apache.org/download.cgi
3. Get Anthropic key   → https://console.anthropic.com
4. Get Tavily key      → https://app.tavily.com
5. Set API keys        → set ANTHROPIC_API_KEY=... & set TAVILY_API_KEY=...
6. Build               → mvn clean package -DskipTests
7. Run                 → mvn spring-boot:run
8. Test                → http://localhost:8080/swagger-ui.html


Built with Java 21 · Spring Boot 3.3 · Anthropic Claude · OpenAI MCP · Tavily Search*
