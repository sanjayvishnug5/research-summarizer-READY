# Research Summarizer Agent
### Java Spring Boot · Multi-Agent System · OpenAI MCP Integration

A production-quality multi-agent research summarizer built with **Java 21 + Spring Boot 3.3**.  
Accepts a research topic via REST API, routes it through three specialised agents, and returns a structured report.

---

## Architecture

```
POST /api/research/summarize
           │
           ▼
┌──────────────────────────────────────────────────────────────┐
│               ResearchPipelineService (Orchestrator)         │
│                                                              │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐  │
│  │   Agent 1       │  │    Agent 2        │  │  Agent 3   │  │
│  │  SearchAgent    │─►│ InsightExtractor  │─►│  Report    │  │
│  │                 │  │     Agent         │  │ Generator  │  │
│  └────────┬────────┘  └──────────────────┘  └─────┬──────┘  │
│           │                                        │         │
│    ┌──────▼──────┐                                 │         │
│    │   Routing   │                                 │         │
│    │   Logic     │                                 │         │
│    └──────┬──────┘                                 │         │
│           │                                        │         │
│   OPENAI_DEV?    GENERAL?                          │         │
│       │              │                             │         │
│  ┌────▼────┐   ┌─────▼──────┐                     │         │
│  │ OpenAI  │   │  Tavily    │                      │         │
│  │   MCP   │   │ Web Search │                      │         │
│  │ Server  │   │            │                      │         │
│  └────┬────┘   └─────┬──────┘                     │         │
│       │insufficient?  │                            │         │
│       └──► fallback ──┘                            │         │
└───────────────────────────────────────────────────────────── ┘
                                                     │
                                                     ▼
                                          ResearchResponse (JSON)
```

---

## Agent Descriptions

### Agent 1 — SearchAgent (`agent/SearchAgent.java`)
- **Classifies** the query as `OPENAI_DEV` or `GENERAL` using deterministic keyword matching
- **If OPENAI_DEV** → queries OpenAI Docs MCP server via Streamable HTTP
- **If MCP returns < 2 results** → falls back to Tavily web search
- **If GENERAL** → goes directly to Tavily web search
- Hands off `SearchAgentOutput` to Agent 2

### Agent 2 — InsightExtractorAgent (`agent/InsightExtractorAgent.java`)
- Receives raw search results from Agent 1
- Uses **Claude Haiku** (fast + cost-efficient) to extract structured insights
- Categories: `fact`, `statistic`, `definition`, `quote`
- Hands off `InsightExtractorOutput` (5–10 insights) to Agent 3

### Agent 3 — ReportGeneratorAgent (`agent/ReportGeneratorAgent.java`)
- Receives structured insights from Agent 2
- Uses **Claude Sonnet** (higher quality) for final report generation
- Produces: Executive Summary, Key Findings (3–7), Details paragraph, Sources
- Returns `ResearchResponse` — the final REST API payload

---

## Routing Logic (Critical Requirement)

### Algorithm

```java
// SearchAgent.java — classifyQuery()
public String classifyQuery(String topic) {
    String lower = topic.toLowerCase();
    for (String keyword : AppConfig.OPENAI_KEYWORDS) {
        if (lower.contains(keyword.toLowerCase())) {
            return "OPENAI_DEV";   // → OpenAI MCP Server
        }
    }
    return "GENERAL";              // → Tavily Web Search
}
```

### Why Deterministic (Not LLM-Based)?
| Approach | Latency | Cost | Reliability | Testable |
|---|---|---|---|---|
| ✅ Keyword matching (our approach) | ~0ms | Free | 100% deterministic | Fully unit-testable |
| ❌ LLM classification | +500ms | API cost | Risk of hallucination | Hard to assert |

### OpenAI Keywords (in `AppConfig.java`)
| Group | Keywords |
|---|---|
| Brand | `openai`, `chatgpt`, `dall-e`, `whisper` |
| Models | `gpt`, `gpt-4`, `gpt-3`, `gpt4o`, `o1`, `o3`, `o4` |
| APIs | `openai api`, `completions api`, `responses api`, `assistants api`, `batch api` |
| SDK/Tools | `agents sdk`, `openai sdk`, `function calling`, `structured outputs` |
| Techniques | `fine-tuning`, `embeddings`, `vector store`, `moderation api` |

### Routing Decision Table

| Query | Classification | Primary | Fallback |
|---|---|---|---|
| "OpenAI GPT-4o capabilities" | OPENAI_DEV | MCP Server | Tavily |
| "OpenAI assistants API tutorial" | OPENAI_DEV | MCP Server | Tavily |
| "fine-tuning a language model" | OPENAI_DEV | MCP Server | Tavily |
| "climate change 2025" | GENERAL | Tavily | — |
| "Spring Boot microservices" | GENERAL | Tavily | — |

---

## Project Structure

```
research-summarizer-java/
├── src/
│   ├── main/
│   │   ├── java/com/skyflinx/researchsummarizer/
│   │   │   ├── ResearchSummarizerApplication.java   ← Entry point
│   │   │   ├── agent/
│   │   │   │   ├── SearchAgent.java                 ← Agent 1
│   │   │   │   ├── InsightExtractorAgent.java       ← Agent 2
│   │   │   │   └── ReportGeneratorAgent.java        ← Agent 3
│   │   │   ├── client/
│   │   │   │   ├── McpClient.java                   ← OpenAI MCP HTTP client
│   │   │   │   └── TavilyClient.java                ← Web search
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java                   ← Settings + OPENAI_KEYWORDS
│   │   │   │   └── WebClientConfig.java             ← WebClient bean
│   │   │   ├── controller/
│   │   │   │   ├── ResearchController.java          ← REST endpoints
│   │   │   │   └── GlobalExceptionHandler.java      ← Error handling
│   │   │   ├── model/
│   │   │   │   ├── ResearchRequest.java
│   │   │   │   ├── ResearchResponse.java
│   │   │   │   ├── SearchResult.java
│   │   │   │   ├── SearchAgentOutput.java
│   │   │   │   ├── Insight.java
│   │   │   │   ├── InsightExtractorOutput.java
│   │   │   │   └── ErrorResponse.java
│   │   │   └── service/
│   │   │       ├── ResearchPipelineService.java     ← Orchestrator
│   │   │       └── DemoModeService.java             ← Demo fallback
│   │   └── resources/
│   │       ├── application.properties
│   │       └── logback-spring.xml                   ← Structured JSON logging
│   └── test/
│       └── java/com/skyflinx/researchsummarizer/
│           └── ResearchSummarizerApplicationTests.java ← Unit tests
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## Setup Instructions

### Prerequisites
- Java 21+
- Maven 3.8+
- API keys: **Anthropic** and **Tavily** (free tier available)

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/research-summarizer-java.git
cd research-summarizer-java
```

### 2. Set environment variables
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...
```

### 3. Build the project
```bash
mvn clean package -DskipTests
```

### 4. Run the server
```bash
mvn spring-boot:run
# Server starts at http://localhost:8080
```

### 5. Test with curl

**OpenAI Developer Topic (MCP Route):**
```bash
curl -X POST http://localhost:8080/api/research/summarize \
  -H "Content-Type: application/json" \
  -d '{"topic": "OpenAI GPT-4o capabilities and pricing", "maxSources": 5}'
```

**General Topic (Web Search Route):**
```bash
curl -X POST http://localhost:8080/api/research/summarize \
  -H "Content-Type: application/json" \
  -d '{"topic": "electric vehicle battery technology 2025", "maxSources": 5}'
```

**Health check:**
```bash
curl http://localhost:8080/api/research/health
```

---

## Docker Setup

```bash
# Build and run with Docker Compose
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...
docker-compose up --build

# API available at http://localhost:8080
```

---

## API Contract

### `POST /api/research/summarize`

**Request:**
```json
{
  "topic": "OpenAI GPT-4o capabilities",
  "maxSources": 5
}
```

**Response (200 OK):**
```json
{
  "topic": "OpenAI GPT-4o capabilities",
  "searchSource": "MCP",
  "executiveSummary": "GPT-4o is OpenAI's flagship multimodal model...",
  "keyFindings": [
    "GPT-4o supports text, image, and audio input natively.",
    "It offers a 128K context window.",
    "Pricing is $2.50 per 1M input tokens."
  ],
  "details": "GPT-4o represents a consolidation of OpenAI's multimodal research...",
  "sources": [
    "https://platform.openai.com/docs/models/gpt-4o",
    "https://openai.com/api/pricing"
  ]
}
```

**Error (400/500):**
```json
{
  "error": "Validation failed",
  "detail": "topic must not be blank",
  "traceId": "a3f9c12b4d1e"
}
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- ✅ **14 OpenAI topics** correctly classified as `OPENAI_DEV`
- ✅ **10 general topics** correctly classified as `GENERAL`
- ✅ Case-insensitive classification
- ✅ Keyword substring matching
- ✅ JSON parsing in InsightExtractorAgent (valid, fenced, bad JSON)
- ✅ JSON parsing in ReportGeneratorAgent
- ✅ Source deduplication in ReportGeneratorAgent
- ✅ AppConfig keywords completeness validation

---

## Sample API Responses

### Sample 1 — OpenAI Developer Topic (MCP Routed)

**Request:**
```bash
curl -X POST http://localhost:8080/api/research/summarize \
  -H "Content-Type: application/json" \
  -d '{"topic": "OpenAI Assistants API file search tool", "maxSources": 5}'
```

**Response:**
```json
{
  "topic": "OpenAI Assistants API file search tool",
  "searchSource": "MCP",
  "executiveSummary": "The OpenAI Assistants API includes a built-in file search tool that enables retrieval-augmented generation over uploaded documents. It supports multiple file formats and automatically chunks, indexes, and retrieves relevant content at query time.",
  "keyFindings": [
    "The file search tool allows assistants to retrieve information from up to 10,000 files per assistant.",
    "Supported file types include PDF, DOCX, TXT, HTML, and Markdown.",
    "Files are automatically chunked and embedded when uploaded to a vector store.",
    "Vector stores persist independently and can be shared across multiple assistants.",
    "The tool uses semantic search to find the most relevant chunks for each query."
  ],
  "details": "The file search tool in the Assistants API is a managed retrieval system built on top of OpenAI vector stores...",
  "sources": [
    "https://platform.openai.com/docs/assistants/tools/file-search",
    "https://platform.openai.com/docs/api-reference/vector-stores"
  ]
}
```

---

### Sample 2 — General Topic (Web Search Routed)

**Request:**
```bash
curl -X POST http://localhost:8080/api/research/summarize \
  -H "Content-Type: application/json" \
  -d '{"topic": "electric vehicle battery technology 2025", "maxSources": 5}'
```

**Response:**
```json
{
  "topic": "electric vehicle battery technology 2025",
  "searchSource": "WEB",
  "executiveSummary": "The electric vehicle market reached an inflection point in 2025, with global sales exceeding 18 million units and battery costs falling below the critical $100/kWh threshold.",
  "keyFindings": [
    "Global EV sales exceeded 18 million units in 2024, with China representing 60% of worldwide demand.",
    "Battery pack costs have fallen below $100/kWh — the threshold for ICE price parity.",
    "Solid-state batteries from Toyota, Samsung SDI, and QuantumScape have demonstrated over 400 Wh/kg in labs.",
    "Global temperatures are 1.2°C above pre-industrial levels, driving EV adoption.",
    "Fast-charging infrastructure supporting 350 kW+ speeds is expanding across major markets."
  ],
  "details": "In 2025, EV battery technology is characterised by rapidly declining costs and improving technology...",
  "sources": [
    "https://about.bnef.com/electric-vehicle-outlook",
    "https://www.reuters.com/technology/solid-state-batteries-2025"
  ]
}
```

---

## Design Decisions

### LLM Choice: Anthropic Claude
- **Claude Haiku** for insight extraction — fast, cost-efficient, handles JSON well
- **Claude Sonnet** for report generation — higher quality final output
- Chosen over OpenAI to demonstrate provider-agnostic design

### Framework: Spring Boot 3.3 with WebClient
- `WebClient` (reactive) used for MCP and Tavily HTTP calls — non-blocking, timeout-safe
- `@Valid` + Bean Validation for request validation
- MDC (Mapped Diagnostic Context) for trace ID propagation across all log lines

### MCP Transport: Streamable HTTP (JSON-RPC 2.0)
- POST to `https://developers.openai.com/mcp` with JSON-RPC 2.0 payload
- Handles both plain JSON and SSE (Server-Sent Events) response formats
- Graceful fallback when MCP is unreachable or returns insufficient results

### Structured Logging with Trace IDs
- Every request generates a unique 12-char hex trace ID
- Injected into MDC at the controller level
- Flows through all three agents via Spring's request context
- Returned in error responses for debugging

### Graceful Degradation
- MCP insufficient results → fallback to Tavily (no hard failure)
- LLM returns bad JSON → parsed with fallback to raw snippets
- Every agent independently fault-tolerant

### Demo Mode
- Runs when `ANTHROPIC_API_KEY` or `TAVILY_API_KEY` is not set
- Still attempts the real OpenAI MCP server
- Returns realistic mock data for demo/presentation purposes
