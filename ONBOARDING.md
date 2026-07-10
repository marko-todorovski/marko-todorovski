# AI Diagram Generator — Developer Onboarding Guide

> **Audience**: New developer joining the project  
> **Last verified against**: Spring Boot 4.0.2, Java 21 (pom.xml target), PostgreSQL 18.3, PlantUML 1.2024.3  
> **Runtime**: The server runs on Java 24 in practice (local JDK); `pom.xml` declares `<java.version>21</java.version>`.

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [End-to-End Request Lifecycle](#2-end-to-end-request-lifecycle)
3. [Backend Deep Dive](#3-backend-deep-dive)
4. [Frontend Deep Dive](#4-frontend-deep-dive)
5. [Diagram Generation Pipeline](#5-diagram-generation-pipeline)
6. [File-by-File Reference](#6-file-by-file-reference)
7. [Dependencies & Configuration](#7-dependencies--configuration)
8. [Error Handling & Validation](#8-error-handling--validation)
9. [Known Issues & Weak Points](#9-known-issues--weak-points)
10. [Refactoring & Scalability Recommendations](#10-refactoring--scalability-recommendations)

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Browser (SPA)                             │
│  index.html — React 18 (Babel-compiled, no build step)           │
│  ┌─────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │ Textarea │  │ Type Select  │  │ Demo Picker  │               │
│  └────┬─────┘  └──────┬───────┘  └──────────────┘               │
│       └────────────────▼                                         │
│                  POST /api/diagram/generate                       │
└──────────────────────────────┬───────────────────────────────────┘
                               │ JSON
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│              Spring Boot App  (port 8080)                         │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │        PlantUML Pipeline  (PRIMARY — /api/diagram/*)    │    │
│  │                                                         │    │
│  │  PlantUmlDiagramController                              │    │
│  │       │                                                 │    │
│  │  ConfidenceDiagramService                               │    │
│  │   ├── DiagramSuggestionService  (classify input)        │    │
│  │   ├── SemanticExtractionService (extract entities)      │    │
│  │   ├── StyleProfileService       (layout config)         │    │
│  │   ├── PlantUmlGenerationService (build PlantUML code)   │    │
│  │   ├── DiagramRenderingService   (render PNG + SVG)      │    │
│  │   └── DomainDiagramRepository  (persist to DB)         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Legacy Mermaid Pipeline  (SECONDARY — /api/diagrams/*) │    │
│  │                                                         │    │
│  │  DiagramController                                      │    │
│  │       │                                                 │    │
│  │  DiagramServiceImpl                                     │    │
│  │   ├── OpenAiDiagramService  (LLM → Mermaid code)        │    │
│  │   └── RuleBasedDiagramService (deterministic fallback)  │    │
│  │       └── MermaidRenderer → PNG                         │    │
│  │   └── DiagramRepository    (entity.Diagram table)       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌───────────────────┐  ┌────────────────────────────────────┐  │
│  │  AI Layer         │  │  Other Controllers                 │  │
│  │  OpenAiService    │  │  AnalyticsController /api/analytics│  │
│  │  OllamaService    │  │  EvaluationController /api/eval... │  │
│  │  AiProviderConfig │  │  HealthController /api/health      │  │
│  └───────────────────┘  │  DiagramDrawIoController           │  │
│                          └────────────────────────────────────┘  │
└────────────────────────────────┬─────────────────────────────────┘
                                 │ JDBC / Hibernate
                                 ▼
                    ┌─────────────────────────┐
                    │  PostgreSQL 18           │
                    │  db: ai_diagrams         │
                    │  user: ai_user           │
                    │  tables:                 │
                    │   domain_diagrams (PK)   │
                    │   diagrams (legacy)      │
                    │   diagram_evaluations    │
                    │   diagram_history        │
                    └─────────────────────────┘
```

### Two Parallel Pipelines — The Most Important Architectural Fact

This codebase has **two completely separate diagram generation pipelines** that evolved over time. Understanding which one is active at any moment is the key to navigating this project.

| | PlantUML Pipeline | Legacy Mermaid Pipeline |
|---|---|---|
| **Base URL** | `/api/diagram/` (singular) | `/api/diagrams/` (plural) |
| **Controller** | `PlantUmlDiagramController` | `DiagramController` |
| **Orchestrator** | `ConfidenceDiagramService` | `DiagramServiceImpl` |
| **Output format** | PlantUML → PNG + SVG | Mermaid → PNG |
| **AI use** | Classify + extract + generate | Generate only |
| **DB entity** | `domain.Diagram` → `domain_diagrams` | `entity.Diagram` → `diagrams` |
| **Frontend uses?** | ✅ YES (main `fetch('/api/diagram/generate')`) | PDF upload only |
| **Confidence model?** | ✅ YES (3-tier) | ❌ No |

The frontend's **Generate button** always calls `/api/diagram/generate` (PlantUML pipeline). The PDF upload button calls `/api/diagrams/from-pdf` (Legacy pipeline).

---

## 2. End-to-End Request Lifecycle

### 2.1 Normal Generation (PlantUML Pipeline)

```
1. User types text + optionally selects a diagram type
2. Clicks "Generate" (or Cmd+Enter)
3. Frontend builds:
     POST /api/diagram/generate
     { "text": "...", "diagramType": "SEQUENCE" | null, "forceGenerate": false }

4. PlantUmlDiagramController.generate()
   └── calls ConfidenceDiagramServiceImpl.process(text, diagramType, seed, forceGenerate)

5. ConfidenceDiagramService branches:
   ├── IF explicit diagramType supplied (or forceGenerate=true):
   │     confidence = 100%, skip classification
   │     IF text is blank → generateFromTemplate(type)
   │     ELSE              → generateWithFallback(text, type, 100, seed, null)
   │
   └── IF auto-detect mode (diagramType=null):
         a. validateInput(text)  [throws 400 if blank]
         b. suggestion = DiagramSuggestionService.suggest(text)
         c. applyErBoostIfSignalled(text, suggestion)
         d. confidence = suggestion.getConfidenceScore()
            ├── ≥ 70% (HIGH)   → generateWithFallback(text, suggestedType, ...)
            ├── 40–69% (MEDIUM) → buildSuggestionResult() → HTTP 422 decision=SUGGEST
            └── < 40% (LOW)
                  ├── has structural signals? → promote to SUGGEST
                  └── else → buildLowConfidenceResult() → decision=REJECT

6. generateWithFallback() tries generateDiagram(); on any exception falls back to generateFromTemplate()

7. generateDiagram():
   a. SemanticExtractionService.extract(text)
      └── Try OpenAI → parse JSON → SemanticModel
          └── Fallback: NLP heuristics (regex + keyword matching)
   b. StyleProfileService.getStyleProfile(diagramType)
      └── Returns layout direction, arrow style, spacing rule
   c. PlantUmlGenerationService.generate(semanticModel, styleProfile, seed)
      └── Dispatches to appropriate method by DiagramType (switch expression)
          Each method builds PlantUML DSL string
   d. DiagramRenderingService.renderToPng(plantUml) → byte[]
      DiagramRenderingService.renderToSvg(plantUml) → byte[]
      └── Uses net.sourceforge.plantuml.SourceStringReader
          Base64-encodes PNG for JSON transport
   e. diagramRepository.save(domain.Diagram{...})
      └── Saves to domain_diagrams table

8. Returns GenerationResult wrapped in ApiResponse<GenerationResult>:
   {
     "success": true,
     "data": {
       "id": "uuid",
       "diagramType": "SEQUENCE",
       "plantUmlCode": "@startuml...",
       "pngBase64": "iVBOR...",      ← inline image
       "svgContent": "<svg>...",     ← inline SVG
       "confidenceScore": 100,
       "confirmationRequired": false,
       "decision": "AUTO",
       "generationMode": "LLM" | "RULE_BASED" | "TEMPLATE" | "TEMPLATE_FALLBACK",
       "entityCount": 3,
       "relationshipCount": 2,
       "actionCount": 5,
       "modelUsed": "GPT-4o",
       "generatedAt": "...",
       "explanation": { ... },
       "message": "Diagram generated successfully"
     }
   }

9. Frontend renders:
   ├── If svgContent: renders inline SVG (preferred, scales cleanly)
   ├── If pngBase64: renders as <img src="data:image/png;base64,...">
   └── If neither: shows error
```

### 2.2 Auto-Detect SUGGEST Flow

When confidence is 40–69%, the backend returns:
```json
{
  "data": {
    "decision": "SUGGEST",
    "diagramType": "SEQUENCE",
    "confidenceScore": 55,
    "confirmationRequired": true,
    "message": "Your description appears to describe interactions. A Sequence Diagram may be appropriate. Do you want to proceed?"
  }
}
```

Frontend shows a blue suggestion card with "Proceed" / "Dismiss". Clicking **Proceed** re-calls `handleGenerate(enumType, true)` which sends `forceGenerate: true`, bypassing classification.

### 2.3 PDF Upload Flow (Legacy Pipeline)

```
1. User picks a PDF file, clicks "Generate from PDF"
2. Frontend:
     POST /api/diagrams/from-pdf
     Content-Type: multipart/form-data
     Body: file=<pdf binary>

3. DiagramController.generateFromPdf()
   └── PdfExtractionService.extractText(file)
       └── Apache PDFBox: PDDocument.load() → PDFTextStripper.getText()
   └── Trims extracted text to 2000 chars
   └── DiagramCreationService.generateAndSave(TextDiagramRequest{text, AUTO_DETECT})

4. DiagramCreationService → DiagramServiceImpl.generateWithFallback(request)
   └── Try: OpenAiDiagramService (calls GPT with prompt → Mermaid code)
   └── Fallback: RuleBasedDiagramService (keyword-based Mermaid generation)
   └── MermaidRenderer.render(mermaidCode) → PNG bytes

5. Saves entity.Diagram to diagrams table
6. Returns DiagramResponse{id, mermaidCode, ...}
7. Frontend constructs /api/diagrams/{id}/png to display image
```

---

## 3. Backend Deep Dive

### 3.1 Package Structure

```
com.example.aidiagramgenerator/
├── AiDiagramGeneratorApplication.java     ← Entry point (@SpringBootApplication)
│
├── ai/                                    ← AI provider abstraction
│   ├── AiModelService.java (interface)    ← callLLM(prompt), callLLMStructured(prompt), getModelName()
│   ├── OpenAiService.java                 ← OpenAI REST calls (GPT-4o-mini / GPT-4o)
│   ├── OllamaService.java                 ← Local Ollama REST calls
│   ├── LlmResult.java                     ← Wrapper: success(content) | failure()
│   └── AiServiceException.java            ← Thrown on 429 / auth failure
│
├── config/
│   ├── AiProviderConfig.java              ← @Bean AiModelService (openai | ollama switch)
│   ├── RestClientConfig.java              ← @Bean RestClient.Builder
│   ├── JacksonConfig.java                 ← ObjectMapper customisation
│   ├── OpenApiConfig.java                 ← Swagger info bean
│   └── DevDataSourceConfig.java           ← Dev-profile datasource overrides
│
├── controller/
│   ├── PlantUmlDiagramController.java     ← /api/diagram/* (PlantUML pipeline)
│   ├── DiagramController.java             ← /api/diagrams/* (legacy Mermaid pipeline)
│   ├── DiagramDrawIoController.java       ← /api/diagram/{id}/drawio export
│   ├── AnalyticsController.java           ← /api/analytics/*
│   ├── EvaluationController.java          ← /api/evaluation/*
│   └── HealthController.java              ← /api/health
│
├── domain/                                ← Domain model (PlantUML pipeline)
│   ├── Diagram.java                       ← NOT a JPA entity — plain domain class
│   ├── DiagramType.java                   ← Second DiagramType enum (11 values used by PlantUML pipeline)
│   ├── DiagramEvaluation.java
│   ├── DiagramSuggestion.java             ← confidenceScore, suggestedDiagramType, reasoningMessage, source
│   ├── SemanticModel.java                 ← entities: List<EntityNode>, relationships, actions
│   ├── EntityNode.java                    ← name, type, attributes
│   ├── Relationship.java                  ← source, target, type, multiplicity
│   ├── StyleProfile.java                  ← diagramType, layoutDirection, arrowStyle, spacingRule
│   ├── LayoutProfile.java                 ← direction, nodeSpacing, rankSpacing, arrowStyle, groupingStyle, notePosition
│   ├── ClassificationDecision.java
│   ├── ClassificationResponse.java
│   └── ClassificationResult.java
│
├── dto/
│   ├── request/
│   │   ├── GenerationRequest.java         ← text, diagramType, seed, forceGenerate
│   │   ├── TextDiagramRequest.java        ← text, diagramType
│   │   ├── UrlDiagramRequest.java         ← url
│   │   ├── XmlDiagramRequest.java         ← xml
│   │   ├── DiagramEvaluationRequest.java
│   │   ├── EvaluationRequest.java
│   │   ├── StructuredDiagramRequest.java
│   │   └── DiagramRequest.java            ← rawText, entities, relationships, diagramTypeHint
│   └── response/
│       ├── ApiResponse<T>.java            ← { success, message, data, timestamp }
│       ├── GenerationResult.java          ← id, diagramType, plantUmlCode, pngBase64, svgContent, ...
│       ├── DiagramResponse.java           ← legacy Mermaid response
│       ├── DiagramSuggestionResponse.java
│       ├── DiagramExplanation.java        ← detectedEntities, relationships, reasoning text
│       ├── EvaluationResponse.java
│       ├── GenerationResult.java          ← note: same name as above (different package level)
│       └── OpenAiDiagramResponse.java
│
├── entity/                                ← JPA entities (legacy Mermaid pipeline)
│   ├── Diagram.java                       ← @Entity "diagrams" table
│   ├── DiagramEvaluation.java             ← @Entity "diagram_evaluations"
│   └── DiagramHistory.java                ← @Entity "diagram_history"
│
├── enums/
│   ├── DiagramType.java                   ← 13 values (used by legacy pipeline + @JsonCreator)
│   └── InputType.java                     ← TEXT, XML, URL, PDF
│
├── exception/
│   ├── GlobalExceptionHandler.java        ← @ControllerAdvice
│   ├── DiagramGenerationException.java    ← → 500
│   ├── DiagramNotFoundException.java      ← → 404
│   └── InvalidDiagramRequestException.java ← → 400
│
├── repository/
│   ├── DomainDiagramRepository.java       ← Spring Data JPA for domain.Diagram (PlantUML pipeline)
│   ├── DomainDiagramEvaluationRepository.java
│   ├── DiagramRepository.java             ← Spring Data JPA for entity.Diagram (legacy)
│   ├── DiagramEvaluationRepository.java
│   └── DiagramHistoryRepository.java
│
└── service/
    ├── ConfidenceDiagramService.java (interface) + ConfidenceDiagramServiceImpl.java
    ├── DiagramSuggestionService.java (interface) + DiagramSuggestionServiceImpl.java
    ├── DiagramClassificationService.java (interface) + DiagramClassificationServiceImpl.java
    ├── SemanticExtractionService.java (interface) + SemanticExtractionServiceImpl.java
    ├── StyleProfileService.java (interface) + StyleProfileServiceImpl.java
    ├── PlantUmlGenerationService.java (interface) + PlantUmlGenerationServiceImpl.java
    ├── DiagramService.java (interface) + DiagramServiceImpl.java     ← legacy
    ├── DiagramCreationService.java (interface) + impl                ← legacy orchestrator
    ├── DiagramGenerationService.java                                 ← legacy
    ├── OpenAiDiagramService.java                                     ← legacy LLM call
    ├── RuleBasedDiagramService.java                                  ← legacy fallback
    ├── MermaidRenderer.java                                          ← legacy Mermaid→PNG
    ├── ActivityDiagramGeneratorService.java + impl
    ├── StateDiagramGeneratorService.java + impl
    ├── ObjectDiagramGeneratorService.java + impl
    ├── CollaborationDiagramGeneratorService.java + impl
    ├── ComponentDiagramGeneratorService.java + impl
    ├── DeploymentDiagramGeneratorService.java + impl
    ├── PdfExtractionService.java (interface) + PdfExtractionServiceImpl.java
    ├── DiagramAnalyticsService.java
    ├── EvaluationService.java, GenerationEvaluationService.java, ClassificationMetricsService.java
    ├── DiagramExplanationService.java + impl
    │
    ├── render/
    │   ├── DiagramRenderingService.java (interface)
    │   ├── DiagramRenderingServiceImpl.java    ← SourceStringReader → PNG/SVG bytes
    │   └── DiagramRenderingException.java
    │
    ├── export/
    │   ├── DrawIoExportService.java + impl
    │   └── DrawIoXmlBuilder.java
    │
    └── generation/
        ├── DiagramGenerator.java (interface)   ← supports(): DiagramType, generate(ParsedInput): String
        ├── DiagramGeneratorRegistry.java       ← Map<String, DiagramGenerator> built at startup
        ├── InputParser.java (interface)
        ├── InputParserRegistry.java
        ├── DiagramTypeClassifier.java (interface)
        ├── classifier/
        │   └── KeywordBasedDiagramTypeClassifier.java
        ├── model/
        │   ├── ParsedInput.java                ← entities, relationships, actions, rawContent
        │   ├── ExtractedEntity.java, ExtractedRelationship.java, ExtractedAction.java
        │   ├── CollaborationParticipant.java, CollaborationConnection.java, CollaborationMessage.java
        │   └── NlpParseResult.java
        ├── parser/
        │   ├── NaturalLanguageParser.java
        │   ├── TextInputParser.java
        │   ├── UrlInputParser.java
        │   └── XmlInputParser.java
        └── generator/
            ├── ActivityDiagramGenerator.java
            ├── StateDiagramGenerator.java
            ├── ObjectDiagramGenerator.java
            ├── MicroservicesDiagramGenerator.java
            ├── ArchitectureDiagramGenerator.java
            ├── C4DiagramGenerator.java
            ├── ClassDiagramGenerator.java
            ├── CollaborationDiagramGenerator.java
            ├── ComponentDiagramGenerator.java
            ├── DeploymentDiagramGenerator.java
            ├── ErDiagramGenerator.java
            ├── SequenceDiagramGenerator.java
            └── UseCaseDiagramGenerator.java
```

### 3.2 Controllers — All Endpoints

#### PlantUML Pipeline (`/api/diagram/`)
| Method | Path | Handler | Description |
|--------|------|---------|-------------|
| POST | `/api/diagram/generate` | `ConfidenceDiagramService.process()` | **Main endpoint** — generates PlantUML diagram |
| POST | `/api/diagram/suggest` | `DiagramSuggestionService.suggest()` | Returns type suggestion with confidence |
| GET | `/api/diagram/{id}` | `DomainDiagramRepository.findById()` | Fetch saved diagram by UUID |
| GET | `/api/diagram/{id}/png` | `DiagramRenderingService.renderToPng()` | Download PNG |
| GET | `/api/diagram/{id}/svg` | `DiagramRenderingService.renderToSvg()` | Download SVG |
| GET | `/api/diagram/{id}/drawio` | `DrawIoExportService.export()` | Download Draw.io XML |

#### Legacy Mermaid Pipeline (`/api/diagrams/`)
| Method | Path | Handler | Description |
|--------|------|---------|-------------|
| POST | `/api/diagrams/from-text` | `DiagramCreationService.generateAndSave()` | Generate from text (Mermaid) |
| POST | `/api/diagrams/from-xml` | Extract XML → same | Generate from XML |
| POST | `/api/diagrams/from-url` | Fetch URL → same | Generate from URL content |
| POST | `/api/diagrams/from-pdf` | `PdfExtractionService` → same | **Used by PDF upload in UI** |
| GET | `/api/diagrams/{id}/png` | `DiagramRepository.findById()` | PNG for legacy diagram |

#### Other
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/analytics/*` | Diagram analytics |
| GET/POST | `/api/evaluation/*` | Evaluation metrics endpoints |
| GET | `/swagger-ui.html` | Swagger UI |
| GET | `/api-docs` | OpenAPI JSON spec |

### 3.3 The Confidence Classification System

`DiagramClassificationServiceImpl` applies a **5-layer classification cascade**:

```
Layer 1: EXPLICIT MENTION (confidence 95–100%)
   Regex patterns detect "sequence diagram", "use case", "state machine", etc.
   If matched → return immediately, no AI call.

Layer 2: AI CLASSIFICATION (confidence 80–90%)
   Calls AiModelService.callLLMStructured(prompt) with a JSON schema prompt.
   Parses response: { "diagramType": "SEQUENCE", "confidence": 85, "reasoning": "..." }
   Falls back to Layer 3 if AI is unavailable or quota exceeded.

Layer 3: SEMANTIC PATTERN DETECTION (confidence 50–75%)
   Checks presence of category-specific word sets:
   - INTERACTION_VERBS → SEQUENCE
   - STRUCTURAL_WORDS → CLASS or ER
   - ER_INDICATORS → ER (disambiguates from CLASS)
   - INFRASTRUCTURE_TERMS → DEPLOYMENT
   - COMPONENT_TERMS → COMPONENT
   - USE_CASE_TERMS → USE_CASE
   - ACTIVITY_TERMS → ACTIVITY
   - STATE_TERMS → STATE
   etc.

Layer 4: KEYWORD SCORING (confidence 20–55%)
   Each DiagramType has a weighted keyword set.
   Scores all types → picks highest score.
   If score < VAGUE_INPUT_THRESHOLD → falls through to Layer 5.

Layer 5: AI FALLBACK (plain text prompt)
   Calls AiModelService.callLLM() with a simpler prompt.
   Parses plain text response for diagram type name.
```

`DiagramSuggestionServiceImpl` mirrors this logic independently (there is duplication — see Section 9).

### 3.4 Semantic Extraction

`SemanticExtractionServiceImpl.extract(text)`:

1. **AI Path**: Sends structured prompt to AI. Prompt asks the model to return JSON: `{ "entities": [...], "relationships": [...], "actions": [...] }`. Parses into `SemanticModel`.

2. **NLP Heuristic Fallback** (`extractByHeuristics`):
   - **Entity extraction**: Regex matches capitalized words and PascalCase identifiers. Filters out `EXCLUDED_WORDS` and `IMPERATIVE_VERBS`. Filters out common English verbs and stop words.
   - **Relationship extraction**: Scans sentences for `RELATIONSHIP_KEYWORDS` (ordered longest-first). Extracts `source → target` pairs. Detects `MULTIPLICITY_PATTERNS` (e.g., "one or more", "0..*").
   - **Action extraction**: Matches words from `ACTION_VERBS` and `USE_CASE_ACTION_VERBS`.
   - Returns a `SemanticModel{entities: List<EntityNode>, relationships: List<Relationship>, actions: List<String>}`.

### 3.5 PlantUML Generation

`PlantUmlGenerationServiceImpl.generate(model, style, seed)`:

1. Creates a `Random` seeded by the `seed` param (or random if null) → **deterministic outputs** when seed is fixed.
2. Calls `generateRandomLayoutProfile(random, seed, style)` → `LayoutProfile` with randomised direction/spacing/arrows/grouping.
3. Dispatches via **Java switch expression** to one of 11 `generateXxx()` methods:

| Type | Method | Notes |
|------|--------|-------|
| `CLASS` | `generateClassDiagram()` | Generates class boxes from EntityNodes; relationships from SemanticModel |
| `ER` | `generateErDiagram()` | Entity blocks with PK/FK fields; Chen notation via PlantUML |
| `SEQUENCE` | `generateSequenceDiagram()` | Participants + messages; uses actions as method calls |
| `USE_CASE` | `generateUseCaseDiagram()` | Actors + use cases in rectangle boundary |
| `COMPONENT` | `generateComponentDiagram()` | Packages/layers; component dependencies |
| `DEPLOYMENT` | `generateDeploymentDiagram()` | Nodes, artifacts, connections |
| `ACTIVITY` | `generateActivityDiagram(model)` | Delegates to `ActivityDiagramGeneratorService` |
| `STATE` | `generateStateDiagram(model)` | Delegates to `StateDiagramGeneratorService` |
| `OBJECT` | `generateObjectDiagram(model)` | Delegates to `ObjectDiagramGeneratorService` |
| `MICROSERVICES` | `generateMicroservicesDiagram(model)` | Uses `rectangle` blocks; `queue` for broker |
| `COLLABORATION` | `generateCollaborationDiagram(model)` | Object nodes + numbered messages |

All methods return a String beginning with `@startuml` and ending with `@enduml`.

Note: There also exists a `DiagramGeneratorRegistry` pattern (`service/generation/generator/`) with 13 `DiagramGenerator` bean implementations. These appear to be a parallel/alternative registration mechanism — it is unclear from the code whether `PlantUmlGenerationServiceImpl` routes through the registry or calls its own methods directly. From reading the code, `PlantUmlGenerationServiceImpl` uses its own internal `generateXxx()` methods, **not** the registry. The registry beans may be used in a different code path or are vestigial.

### 3.6 Rendering

`DiagramRenderingServiceImpl` wraps the PlantUML library:
```java
SourceStringReader reader = new SourceStringReader(plantUmlCode);
reader.outputImage(outputStream, new FileFormatOption(FileFormat.PNG));
reader.outputImage(outputStream, new FileFormatOption(FileFormat.SVG));
```
- Output size is validated (max 10 MB).
- Basic syntax validation: warns if the string doesn't start with `@start`.
- `DiagramRenderingException` has a `RenderingErrorType` enum: `INVALID_SYNTAX`, `RENDERING_ERROR`, `OUTPUT_ERROR`.

### 3.7 AI Provider Configuration

`AiProviderConfig` reads `ai.provider` (default: `openai`) and creates exactly one `@Primary` `AiModelService` bean:

```properties
ai.provider=openai          # cloud OpenAI
ai.provider=ollama          # local Ollama
```

| Property | Default | Description |
|----------|---------|-------------|
| `openai.api.key` | (blank) | OpenAI API key |
| `openai.api.url` | `https://api.openai.com/v1/chat/completions` | API endpoint |
| `openai.model` | `gpt-4o-mini` | Model for classification/extraction |
| `openai.diagram.model` | `gpt-4o` | Model for diagram generation |
| `ollama.api.url` | `http://localhost:11434/api/generate` | Local Ollama endpoint |
| `ollama.model` | `llama3` | Ollama model name |

Both `OpenAiService` and `OllamaService` implement `AiModelService`:
- `callLLM(prompt)` → returns `LlmResult.success(content)` or `LlmResult.failure()` (never throws)
- `callLLMStructured(prompt)` → returns JSON string or null

`OpenAiService` uses two different models: `model` (e.g., gpt-4o-mini) for fast classification calls, and `diagramModel` (e.g., gpt-4o) for slower, higher-quality generation calls.

### 3.8 Database

**Table: `domain_diagrams`** (PlantUML pipeline, mapped by `domain.Diagram` JPA entity)

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | Auto-generated |
| `created_at` | timestamp | Auto-set |
| `diagram_type` | varchar | One of 13 enum values |
| `input_text` | text | The user's description |
| `model_used` | varchar | e.g., "GPT-4o" |
| `plant_uml_code` | text | The generated PlantUML source |

CHECK constraint `domain_diagrams_diagram_type_check` enforces all 13 valid values:
`CLASS, SEQUENCE, ER, USE_CASE, ARCHITECTURE, C4, OBJECT, ACTIVITY, STATE, COLLABORATION, COMPONENT, DEPLOYMENT, MICROSERVICES`

> **Important**: Hibernate `ddl-auto=update` will NOT reconcile CHECK constraints. If you add a new `DiagramType` enum value, you must manually update this constraint in the database. See the fix applied in this session.

**Table: `diagrams`** (legacy Mermaid pipeline, mapped by `entity.Diagram`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | Long PK (auto-increment) | Auto-generated |
| `created_at` | timestamp | |
| `diagram_type` | varchar | Values from `enums.DiagramType` |
| `mermaid_code` | text | Mermaid syntax |
| `input_text` | text | |
| `png_data` | bytea | Stored PNG binary |

---

## 4. Frontend Deep Dive

The entire frontend is a **single HTML file**: `src/main/resources/static/index.html`.

### 4.1 Technology

- **React 18** loaded via CDN (UMD build)
- **Babel Standalone** compiles JSX in the browser at runtime (no build step, no `npm`, no `package.json`)
- **Mermaid.js 10** for rendering legacy Mermaid diagrams
- All styles are inline `<style>` in the `<head>`

### 4.2 State Variables

```javascript
const [text, setText]             // The textarea content
const [diagramType, setDiagramType] // Selected from <select> ("" = auto-detect, "CLASS", etc.)
const [selectedDemo, setSelectedDemo] // Active demo example key
const [result, setResult]         // GenerationResult from backend (successful generation)
const [suggestion, setSuggestion] // DiagramSuggestion (SUGGEST decision from backend)
const [error, setError]           // Error message string
const [loading, setLoading]       // Generate button spinner
const [pdfFile, setPdfFile]       // File object from PDF picker
const [pdfLoading, setPdfLoading] // PDF button spinner
const [showFullPdfText, setShowFullPdfText] // Toggle for truncated PDF preview
```

### 4.3 Diagram Type Dropdown

The `<select>` has these options (values sent to backend):

| Display Label | Value sent to API |
|---------------|------------------|
| Auto-detect | `""` (null in body) |
| Class Diagram | `CLASS` |
| Sequence Diagram | `SEQUENCE` |
| ER Diagram | `ER` |
| Component Diagram | `COMPONENT` |
| Deployment Diagram | `DEPLOYMENT` |
| Use Case Diagram | `USE_CASE` |
| Object Diagram | `OBJECT` |
| Activity Diagram | `ACTIVITY` |
| State Diagram | `STATE` |
| Collaboration Diagram | `COLLABORATION` |
| Microservices Architecture | `MICROSERVICES_ARCHITECTURE` |

Note: `MICROSERVICES_ARCHITECTURE` is the frontend's value. The backend `ConfidenceDiagramServiceImpl.DIAGRAM_TYPE_ALIASES` maps `MICROSERVICES_ARCHITECTURE` → `DiagramType.MICROSERVICES`.

### 4.4 Request Building

```javascript
async function handleGenerate(overrideType = null, forceGenerate = false) {
    const body = { text: text || '' };
    if (resolvedType) body.diagramType = resolvedType;
    if (forceGenerate) body.forceGenerate = true;
    
    const res = await fetch('/api/diagram/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    ...
}
```

The `diagramType` field is only included when a type is explicitly selected. When absent, the backend defaults to `null` and runs auto-detect.

### 4.5 Response Routing

```javascript
const decision = data.decision;
if (decision === 'SUGGEST') → setSuggestion(...) → show blue suggestion card
if (decision === 'REJECT')  → setError(data.message)
else                        → setResult(data)     → render diagram
```

When rendering a `result`:
1. Try `result.svgContent` → render inline SVG
2. Try `result.pngBase64` → render as `<img src="data:image/png;base64,...">`
3. If legacy (PDF) result with `diagramImageUrl` → fetch from `/api/diagrams/{id}/png`
4. Show `result.plantUmlCode` in a `<pre>` code block

### 4.6 Demo Examples

`DEMO_EXAMPLES` is a hardcoded array of 35+ example scenarios. When the user selects one, `handleDemoSelect` sets both `text` and `diagramType` state simultaneously, then the user clicks Generate.

### 4.7 PDF Upload

```
User clicks "Choose File" → hidden <input type="file"> → setPdfFile()
User clicks "Generate from PDF" → handlePdfUpload()
  → FormData with file
  → POST /api/diagrams/from-pdf
  → On success, result.diagramImageUrl = /api/diagrams/{id}/png
  → Frontend fetches image from that URL separately
```

Note: The PDF path goes through the **legacy Mermaid pipeline**, not the PlantUML pipeline. The resulting image comes from the `diagrams` table (not `domain_diagrams`). This is the only UI feature that uses the legacy pipeline.

### 4.8 Download Actions

Three download buttons appear after a successful generation:
- **Download PNG**: `GET /api/diagram/{id}/png` → triggers browser download
- **Download SVG**: `GET /api/diagram/{id}/svg` → triggers browser download
- **Download Draw.io**: `GET /api/diagram/{id}/drawio` → exports diagram as Draw.io XML

### 4.9 Keyboard Shortcut

`Cmd+Enter` (or `Ctrl+Enter`) in the textarea triggers `handleGenerate()`.

---

## 5. Diagram Generation Pipeline

### 5.1 Step-by-Step for Each Diagram Type

Here is exactly what happens inside `PlantUmlGenerationServiceImpl` for each type:

#### CLASS Diagram
- Iterates `SemanticModel.entities` → generates PlantUML `class EntityName { }` blocks
- Uses entity attributes (if any) as fields
- Iterates `SemanticModel.relationships` → generates `-->`, `--|>`, `*--`, `o--` lines
- Relationship type from `Relationship.type` string: inheritance, composition, aggregation, dependency, association
- Adds multiplicity labels (e.g., `"1" o-- "0..*"`)

#### SEQUENCE Diagram
- Entities become `participant` declarations
- Actions become messages: `EntityA -> EntityB : actionName()`
- Relationships can trigger `alt`/`else` blocks for conditional flows
- Layout direction from `LayoutProfile`

#### ER Diagram
- Uses `entity EntityName { * id <<PK>> -- fields }` PlantUML syntax
- Relationships: `Customer ||--o{ Order : places` (Chen notation)
- ER confidence boost: If text contains "has many", "belongs to", "foreign key", or "primary key" + comma-separated entities → `ER_CONFIDENCE_BOOST = 30` added to confidence score in `ConfidenceDiagramServiceImpl.applyErBoostIfSignalled()`

#### USE CASE Diagram
- Actors from entities whose name contains role-like words (User, Admin, Customer, etc.)
- Use cases from actions in `SemanticModel.actions`
- Wrapped in `rectangle "System" { ... }`
- `include` and `extend` relationships from relationship list

#### COMPONENT Diagram
- Entities become `[Component]` notations in packages
- Relationships become `-->` with labels
- Groups entities by their relationship clusters into packages

#### DEPLOYMENT Diagram
- Entities become `node "..."` blocks
- Databases detected by naming pattern → `database "..."` syntax
- Connections shown as arrows

#### ACTIVITY Diagram
- Delegates to `ActivityDiagramGeneratorServiceImpl`
- Detects sequential steps, conditionals (`if/then/else`), loops, swimlanes
- Generates `start`, `:action;`, `if (condition?) then (yes)`, `stop` blocks

#### STATE Diagram
- Delegates to `StateDiagramGeneratorServiceImpl`
- Detects states and transitions from text
- Generates `[*] --> State`, `State --> NextState : event` blocks
- Handles entry/exit actions and composite states

#### OBJECT Diagram
- Delegates to `ObjectDiagramGeneratorServiceImpl`
- Shows concrete instances with `object "name : Type" { field = value }` blocks
- Links between objects with `-->` and relationship label

#### MICROSERVICES Diagram
- Uses `rectangle "[ServiceName]"` for each service
- Uses `queue "[MessageBroker]"` for messaging components
- Generates `-->` arrows between services
- Detects API Gateway, auth, product, order, payment, notification service patterns

#### COLLABORATION Diagram
- Uses `object Node` blocks
- Messages as numbered arrows: `1.1: Browser -> OrderController : method()`
- Extracts participants and their communication order

### 5.2 Template Mode

When `text` is blank but a diagram type is explicitly selected, `ConfidenceDiagramServiceImpl.generateFromTemplate()` returns one of 13 hardcoded default PlantUML strings from the `DEFAULT_TEMPLATES` map. These are realistic examples (e-commerce, banking, etc.) that give users a useful starting point.

### 5.3 Fallback Mode (`TEMPLATE_FALLBACK`)

When `generateDiagram()` throws any exception (AI unavailable, rendering failure, empty output), `generateWithFallback()` catches it and returns the template for that type with `generationMode = "TEMPLATE_FALLBACK"` and a user-facing message explaining what happened.

### 5.4 Generation Mode Labels

The `generationMode` field in `GenerationResult` tells you which code path ran:

| Value | Meaning |
|-------|---------|
| `LLM` | AI classification source was `AI_PROVIDER` |
| `RULE_BASED` | AI classification source was rule-based / heuristic |
| `TEMPLATE` | No text provided — used default template |
| `TEMPLATE_FALLBACK` | Generation failed — fell back to default template |

---

## 6. File-by-File Reference

### Key Entry Points

| File | Role |
|------|------|
| [AiDiagramGeneratorApplication.java](src/main/java/com/example/aidiagramgenerator/AiDiagramGeneratorApplication.java) | `@SpringBootApplication` — starts everything |
| [application.properties](src/main/resources/application.properties) | Main config: port, DB URL, AI keys, thresholds |
| [application-dev.properties](src/main/resources/application-dev.properties) | Dev overrides (activate with `-Dspring.profiles.active=dev`) |
| [index.html](src/main/resources/static/index.html) | The entire frontend (React SPA, ~800 lines) |

### Configuration

| File | Role |
|------|------|
| [AiProviderConfig.java](src/main/java/com/example/aidiagramgenerator/config/AiProviderConfig.java) | Selects OpenAI vs Ollama at startup via `ai.provider` property |
| [RestClientConfig.java](src/main/java/com/example/aidiagramgenerator/config/RestClientConfig.java) | Creates `RestClient.Builder` bean |
| [JacksonConfig.java](src/main/java/com/example/aidiagramgenerator/config/JacksonConfig.java) | ObjectMapper settings (e.g., snake_case, null handling) |
| [OpenApiConfig.java](src/main/java/com/example/aidiagramgenerator/config/OpenApiConfig.java) | Swagger title, version, description |

### Core Services

| File | Role |
|------|------|
| [ConfidenceDiagramServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/ConfidenceDiagramServiceImpl.java) | **Heart of the system** — 3-tier confidence orchestration |
| [DiagramClassificationServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/DiagramClassificationServiceImpl.java) | 5-layer classification cascade |
| [DiagramSuggestionServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/DiagramSuggestionServiceImpl.java) | Wraps classification with confidence scoring |
| [SemanticExtractionServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/SemanticExtractionServiceImpl.java) | AI + NLP heuristic entity/relationship extraction |
| [PlantUmlGenerationServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/PlantUmlGenerationServiceImpl.java) | Dispatches to per-type PlantUML generators |
| [DiagramRenderingServiceImpl.java](src/main/java/com/example/aidiagramgenerator/service/render/DiagramRenderingServiceImpl.java) | Renders PlantUML string → PNG/SVG bytes |

### AI Layer

| File | Role |
|------|------|
| [AiModelService.java](src/main/java/com/example/aidiagramgenerator/ai/AiModelService.java) | Interface: `callLLM()`, `callLLMStructured()`, `getModelName()` |
| [OpenAiService.java](src/main/java/com/example/aidiagramgenerator/ai/OpenAiService.java) | OpenAI REST calls; uses `RestClient`; never throws (returns `LlmResult.failure()`) |
| [OllamaService.java](src/main/java/com/example/aidiagramgenerator/ai/OllamaService.java) | Local Ollama via `WebClient` |
| [LlmResult.java](src/main/java/com/example/aidiagramgenerator/ai/LlmResult.java) | Result wrapper: `success(content)` / `failure()` |

### Domain Model

| File | Role |
|------|------|
| [domain/DiagramType.java](src/main/java/com/example/aidiagramgenerator/domain/DiagramType.java) | Enum with 11 values used by PlantUML pipeline; has `getValue()`, `getDisplayName()` |
| [enums/DiagramType.java](src/main/java/com/example/aidiagramgenerator/enums/DiagramType.java) | Enum with 13 values used by legacy pipeline; has `@JsonCreator`/`@JsonValue` |
| [domain/SemanticModel.java](src/main/java/com/example/aidiagramgenerator/domain/SemanticModel.java) | `entities`, `relationships`, `actions` — the central data structure |
| [domain/Diagram.java](src/main/java/com/example/aidiagramgenerator/domain/Diagram.java) | Domain Diagram (not JPA) — has `id`, `inputText`, `diagramType`, `plantUmlCode`, `modelUsed`, `createdAt` |

---

## 7. Dependencies & Configuration

### Maven Dependencies (`pom.xml`)

| Artifact | Version | Purpose |
|----------|---------|---------|
| `spring-boot-starter-webmvc` | 4.0.2 | Spring MVC, Tomcat |
| `spring-boot-starter-data-jpa` | 4.0.2 | Hibernate + Spring Data |
| `spring-boot-starter-validation` | 4.0.2 | Bean Validation (`@Valid`) |
| `spring-boot-starter-webflux` | 4.0.2 | `WebClient` for Ollama |
| `postgresql` | managed | PostgreSQL JDBC driver |
| `h2` | managed (runtime) | H2 in-memory DB (for tests) |
| `plantuml` | 1.2024.3 | PlantUML rendering library |
| `pdfbox` | 3.0.3 | PDF text extraction |
| `stanford-corenlp` | 4.5.7 | NLP pipeline (tokenize/POS/NER/depparse) |
| `stanford-corenlp:models` | 4.5.7 | NLP model files (large — ~500 MB) |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | Swagger UI |
| `lombok` | managed | Boilerplate reduction (`@Data`, `@Builder`, etc.) |

> **Note**: Stanford CoreNLP models are large. First build / startup will be slow if they need to be downloaded.

### Running the Application

```bash
# Start (from project root)
./mvnw spring-boot:run

# Or with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test
# Or use the helper script:
./run-tests.sh

# Test the API manually:
./test-api.sh
```

### Required Environment

1. **PostgreSQL** running on `localhost:5432`
   - DB name: `ai_diagrams`
   - User: `ai_user`
   - Password: in `application.properties`

2. **OpenAI API key** in `application.properties`:
   ```properties
   openai.api.key=sk-...
   ```
   If blank, the system falls back to NLP heuristics entirely.

3. **Java 21+** (pom.xml target; currently running on Java 24).

### Key Properties

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/ai_diagrams
spring.datasource.username=ai_user
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=update

ai.provider=openai
openai.api.key=sk-...
openai.model=gpt-4o-mini
openai.diagram.model=gpt-4o

# Confidence thresholds
diagram.classification.high=70
diagram.classification.medium=40

# LLM timeout for legacy pipeline
diagram.generation.timeout-seconds=30
```

---

## 8. Error Handling & Validation

### 8.1 Global Exception Handler

`GlobalExceptionHandler` (`@ControllerAdvice`) maps all exceptions to `ApiResponse.error()`:

| Exception | HTTP Status | When |
|-----------|-------------|------|
| `DiagramNotFoundException` | 404 | `GET /api/diagram/{id}` with unknown UUID |
| `InvalidDiagramRequestException` | 400 | Vague/empty text in auto-detect mode |
| `IllegalArgumentException` | 400 | Null/blank input to services |
| `HttpMessageNotReadableException` | 400 | Malformed JSON body |
| `MethodArgumentNotValidException` | 400 | `@Valid` bean validation failures |
| `DiagramGenerationException` | 500 | Generation produced no output |
| `DiagramRenderingException` | 500 | PlantUML rendering failed |
| `NoResourceFoundException` | 404 | Unknown URL path |
| `Exception` (catch-all) | 500 | Any other unexpected error |

All error responses follow:
```json
{
  "success": false,
  "message": "Description of error",
  "data": null,
  "timestamp": "2025-01-01T00:00:00Z"
}
```

### 8.2 Service-Level Resilience

| Service | Strategy |
|---------|---------|
| `ConfidenceDiagramServiceImpl.generateWithFallback()` | Wraps `generateDiagram()` in try/catch → returns template on any failure |
| `DiagramServiceImpl.generateWithFallback()` | Uses `ExecutorService.submit()` + `Future.get(timeout)` → falls back to `RuleBasedDiagramService` |
| `OpenAiService.callLLM()` | Never throws — catches all exceptions, returns `LlmResult.failure()` |
| `SemanticExtractionServiceImpl.extract()` | AI failure → NLP heuristic fallback |
| `DiagramClassificationServiceImpl` | 5-layer cascade — always returns a result |

### 8.3 Frontend Error Handling

```javascript
// In handleGenerate():
try {
    const res = await fetch('/api/diagram/generate', ...);
    if (!res.ok && res.status !== 422) throw new Error(data.message);
    // Route by data.decision
} catch (e) {
    setError(e.message);
} finally {
    setLoading(false);
}

// In downloadDiagram():
// Checks res.ok, reads error text, throws descriptive Error
// Catches and calls setError()
```

The frontend shows a red error box (`<div className="error">`) for `error` state, and a blue suggestion card (`<div className="suggestion">`) for `suggestion` state.

### 8.4 Logging

All services use SLF4J with Logback:
- `logger.info()` for normal flow events (classification result, entities extracted, diagram saved)
- `logger.warn()` for recoverable failures (rendering failed, LLM returned null, template fallback)
- `logger.error()` for unexpected failures with stack traces
- `logger.debug()` for detailed diagnostic data (PlantUML output, entity names)
- `logger.trace()` for very low-level data (rendering format)

Key log markers to grep for when debugging:
- `"LLM_USED"` — AI was called and responded
- `"Using NLP heuristic fallback"` — AI was unavailable
- `"falling back to default template"` — generation failed
- `"Saved diagram with ID:"` — successful persistence
- `"Classification result:"` — what the classifier decided

---

## 9. Known Issues & Weak Points

### 9.1 Dual DiagramType Enums ⚠️

There are **two** `DiagramType` enums in the same project:
- `com.example.aidiagramgenerator.domain.DiagramType` — used by PlantUML pipeline, 11 values
- `com.example.aidiagramgenerator.enums.DiagramType` — used by legacy pipeline, 13 values

This causes confusion and risks mismatches. If you add a new diagram type, you must add it to both enums, update the DB constraint, update `DEFAULT_TEMPLATES`, update the frontend `DIAGRAM_TYPES` array, add a generator method in `PlantUmlGenerationServiceImpl`, and (optionally) add a `DiagramGenerator` bean.

### 9.2 DB CHECK Constraint Not Managed by Hibernate ⚠️

`spring.jpa.hibernate.ddl-auto=update` does **not** add, modify, or remove CHECK constraints. Adding a new `DiagramType` enum value will cause `DataIntegrityViolationException` on every save attempt until the constraint is manually updated in the database:

```sql
ALTER TABLE domain_diagrams DROP CONSTRAINT domain_diagrams_diagram_type_check;
ALTER TABLE domain_diagrams ADD CONSTRAINT domain_diagrams_diagram_type_check
  CHECK (diagram_type = ANY (ARRAY[
    'CLASS','SEQUENCE','ER','USE_CASE','ARCHITECTURE','C4',
    'OBJECT','ACTIVITY','STATE','COLLABORATION','COMPONENT',
    'DEPLOYMENT','MICROSERVICES'
  ]));
```

### 9.3 Duplicate Classification Logic ⚠️

`DiagramClassificationServiceImpl` and `DiagramSuggestionServiceImpl` both implement very similar classification cascades with duplicated keyword sets, pattern lists, and confidence scoring. There is no shared base class or utility. Changes to one must be mirrored to the other.

### 9.4 DiagramGeneratorRegistry Is Unused

The `DiagramGeneratorRegistry` and 13 `DiagramGenerator` beans in `service/generation/generator/` appear to not be called from `PlantUmlGenerationServiceImpl`. The generation switch-case in `PlantUmlGenerationServiceImpl` calls its own private methods directly. The registry may be leftover from an earlier design or intended for a not-yet-implemented refactoring.

### 9.5 Frontend Has No Build Step

The frontend is plain HTML with Babel compiling JSX in the browser at runtime. This is fine for a demo/prototype but has implications:
- No TypeScript, no linting, no bundling
- Babel standalone adds ~800 KB to page load
- Slow initial render on first load (Babel compilation)
- No hot reload; you must hard-refresh after editing `index.html`

### 9.6 PDF Upload Uses Legacy Pipeline

PDF uploads go to `/api/diagrams/from-pdf` (legacy Mermaid pipeline), producing Mermaid output. The result is displayed differently from PlantUML results. The frontend handles this with a special `diagramImageUrl` path. If you want PDF to use the PlantUML pipeline, you'd need a new endpoint or to route through `ConfidenceDiagramService`.

### 9.7 Stanford CoreNLP Dependency Is Heavy

The `stanford-corenlp` models artifact is ~500 MB. This significantly increases build time, Docker image size, and startup time (NLP pipeline initialization on first use). If AI availability is high (OpenAI key configured), the NLP fallback is rarely exercised.

### 9.8 No Authentication or Rate Limiting

All endpoints are publicly accessible with no authentication, rate limiting, or CORS restrictions beyond what Spring Boot's defaults provide. Do not expose this service publicly without adding auth.

### 9.9 `domain.Diagram` Is Not a JPA Entity

Despite living in the `domain/` package and being saved via `DomainDiagramRepository`, `domain.Diagram` is not a direct JPA `@Entity`. There's an intermediary mapping. Make sure the `@Entity` annotation lives on the correct class when tracing persistence issues.

### 9.10 `generation/generator/` May Have C4 and ARCHITECTURE Types Not in `domain.DiagramType`

`domain.DiagramType` has `C4` and `ARCHITECTURE` values, but `PlantUmlGenerationServiceImpl`'s switch expression may not include them all (depends on current state of the file). If a generator is missing from the switch, a `MatchException` will be thrown at runtime.

---

## 10. Refactoring & Scalability Recommendations

### 10.1 Merge the Two DiagramType Enums

**Priority: High**

Consolidate `domain.DiagramType` and `enums.DiagramType` into a single canonical enum in a shared package (e.g., `com.example.aidiagramgenerator.model.DiagramType`). Add `@JsonCreator`/`@JsonValue` once. Both pipelines use the same enum.

### 10.2 Wire the DiagramGeneratorRegistry

**Priority: Medium**

Replace `PlantUmlGenerationServiceImpl`'s private `generateXxx()` switch with calls through `DiagramGeneratorRegistry`. This makes adding new diagram types a matter of adding a single Spring bean without modifying the generation service:

```java
// Instead of: switch(type) { case CLASS -> generateClassDiagram(...) ... }
DiagramGenerator generator = registry.getGenerator(type); // lookup by type
return generator.generate(parsedInput);
```

### 10.3 Consolidate Classification Services

**Priority: Medium**

`DiagramClassificationServiceImpl` and `DiagramSuggestionServiceImpl` should share a single keyword/pattern store and classification algorithm. Extract a `ClassificationStrategy` interface or a shared utility class. Currently changes to classification rules must be made in two places.

### 10.4 Add a DB Migration Tool

**Priority: High**

Replace `ddl-auto=update` with **Flyway** or **Liquibase** for schema management:
- CHECK constraints will be tracked in version-controlled migration files
- No more manual SQL ALTER TABLE when adding enum values
- Safe for production deployments

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

### 10.5 Replace Frontend with a Proper Build

**Priority: Medium (if frontend complexity grows)**

Migrate `index.html` to a Vite + React + TypeScript project. The current setup works but doesn't scale:
- Add type safety to API response handling
- Enable tree-shaking (remove Mermaid.js if unused)
- Enable hot reload during development
- The backend can serve the built assets from `src/main/resources/static/`

### 10.6 Add API Rate Limiting

**Priority: Medium**

AI calls are expensive. Add Spring's rate limiter or a custom `RateLimiter` (Guava/Resilience4j) on the `/api/diagram/generate` endpoint to prevent quota exhaustion from excessive or accidental requests.

### 10.7 Replace Stanford CoreNLP with Lightweight Alternative

**Priority: Low-Medium**

If the NLP fallback is the only use of CoreNLP, replace it with a lightweight regex/rule-based extractor or a faster NLP library (e.g., OpenNLP, Apache Lucene analyzers). This reduces the Docker image by ~500 MB and startup time by several seconds.

### 10.8 Separate the Legacy Pipeline or Remove It

**Priority: Medium (long-term)**

The legacy Mermaid pipeline (`/api/diagrams/*`) is used only for PDF uploads in the current UI. Consider:
- Option A: Route PDF uploads through `ConfidenceDiagramService` (extract text from PDF → PlantUML pipeline)
- Option B: Mark legacy endpoints as deprecated and document migration path
- The dual-table, dual-enum, dual-pipeline architecture is a significant maintenance burden

### 10.9 Adding a New Diagram Type (Checklist)

When you need to add a new diagram type (e.g., `TIMING`):

1. **Add to `domain.DiagramType` enum** — add `TIMING` value
2. **Add to `enums.DiagramType` enum** — add `TIMING` value with `@JsonValue`
3. **Add generator** — create `TimingDiagramGenerator.java` in `service/generation/generator/` implementing `DiagramGenerator`
4. **Register in `PlantUmlGenerationServiceImpl`** — add `case TIMING -> generateTimingDiagram(model, style, layout, random)` in the switch
5. **Add default template** — add entry to `DEFAULT_TEMPLATES` map in `ConfidenceDiagramServiceImpl`
6. **Add to alias map** — add entries to `DIAGRAM_TYPE_ALIASES` in `ConfidenceDiagramServiceImpl`
7. **Add classification signals** — add keyword set to `DiagramClassificationServiceImpl` and `DiagramSuggestionServiceImpl`
8. **Add explicit type patterns** — add regex to `EXPLICIT_TYPE_PATTERNS` in both classification services
9. **Update DB constraint** — run the ALTER TABLE SQL to add `'TIMING'` to the CHECK constraint
10. **Update frontend** — add to `DIAGRAM_TYPES` array in `index.html`
11. **Update `StyleProfileServiceImpl`** — add a style profile for the new type

### 10.10 Debugging Generation Failures

When a diagram generates incorrectly or fails:

1. **Check the logs** — look for `"Classification result:"`, `"Extracted semantic model:"`, `"PlantUML generated successfully"` — trace which step failed
2. **Check `generationMode` in response** — `TEMPLATE_FALLBACK` means the pipeline threw an exception
3. **Check `decision` in response** — `REJECT` means classification confidence was too low
4. **Test the PlantUML directly** — copy `plantUmlCode` from the response and paste into [PlantUML online editor](https://www.plantuml.com/plantuml/uml/) to verify syntax
5. **Test classification** — use `POST /api/diagram/suggest` with your text to see what type and confidence the classifier returns
6. **Check AI availability** — if OpenAI key is missing or quota exceeded, all AI calls return `LlmResult.failure()` and the NLP heuristic runs. The log line `"Using NLP heuristic fallback"` confirms this.
7. **Force a type** — add `"diagramType": "SEQUENCE"` to the request body to skip classification entirely
8. **Use the seed** — add `"seed": 42` to get deterministic output for reproducible debugging

---

*This document was generated during a development session on the project. To regenerate or update it, re-run the exploration agent with the latest codebase state.*
