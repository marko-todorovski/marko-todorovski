# AI Diagram Generator — UML Architecture Diagrams
### Chapter 4 Figures — PlantUML Source

All component names, class names, field names, and package structures correspond directly
to the production source code in `src/main/java/com/example/aidiagramgenerator/`.

> **Rendering:** Paste any `@startuml` … `@enduml` block into
> [plantuml.com/plantuml/uml](https://www.plantuml.com/plantuml/uml)
> or run `java -jar plantuml.jar THESIS_DIAGRAMS.md` with PlantUML 1.2024.3+.

---

## Figure 4.1 — High-Level System Architecture

Presents the three-tier architecture (Client · Application · Data) showing both the primary
PlantUML pipeline and the legacy Mermaid pipeline, together with their external AI service
dependencies. The dashed fallback arrow from `SemanticExtractionServiceImpl` to
`Stanford CoreNLP` activates whenever the OpenAI API returns an error or quota-exceeded response.

```plantuml
@startuml Figure_4_1_System_Architecture

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam shadowing false
skinparam roundcorner 8
skinparam arrowThickness 1.5
skinparam packageStyle rectangle
skinparam package {
  BackgroundColor #FAFAFA
  BorderColor #5D6D7E
  FontStyle bold
  FontSize 12
}
skinparam component {
  BackgroundColor white
  BorderColor #2471A3
  FontSize 11
}

title Figure 4.1 — AI Diagram Generator: High-Level System Architecture\nSpring Boot 4.0.2 · Java 24 · PostgreSQL 18.3 · PlantUML 1.2024.3

package "Client Tier" as CLIENT_TIER {
  [React 18 SPA\n(static/index.html)] as SPA #D6EAF8
  note right of SPA
    CDN Babel Standalone (JSX transpilation)
    CDN Mermaid 10 (legacy rendering)
    No frontend build step required
  end note
}

package "Application Tier  ·  Spring Boot 4.0.2  (port 8080)" as APP_TIER {

  package "REST Layer  (6 Controllers)" as REST_LAYER {
    [PlantUmlDiagramController\n/api/diagram/*] as PUML_CTRL #D6EAF8
    [DiagramController\n/api/diagrams/*] as DIAG_CTRL #D6EAF8
    [DiagramDrawIoController\n/api/diagram/{id}/drawio] as DRAWIO_CTRL #D6EAF8
    [HealthController\nAnalyticsController\nEvaluationController] as SUPPORT_CTRL #D6EAF8
  }

  package "Primary PlantUML Pipeline  (active)" as PRIMARY {
    [ConfidenceDiagramServiceImpl\n<<orchestrator>>] as CDS #D5F5E3
    [DiagramClassificationServiceImpl\n<<5-layer cascade>>] as CLASSIFY #FCF3CF
    [DiagramSuggestionServiceImpl] as SUGGEST #FCF3CF
    [SemanticExtractionServiceImpl] as EXTRACT #FCF3CF
    [PlantUmlGenerationServiceImpl\n<<11-case switch>>] as GEN #FDEBD0
    [DiagramRenderingServiceImpl\n<<SourceStringReader>>] as RENDER #FDEBD0
  }

  package "Legacy Mermaid Pipeline  (backward-compatible)" as LEGACY {
    [DiagramServiceImpl] as DIAG_SVC #D6EAF8
    [DiagramGeneratorRegistry\n<<strategy · 13 generators>>] as REGISTRY #D6EAF8
  }

  package "AI Provider Layer" as AI_LAYER {
    [AiModelService\n<<interface>>] as AI_IFACE #EAD9F7
    [OpenAiService\nGPT-4o · GPT-4o-mini] as OPENAI_SVC #EAD9F7
    [OllamaService\n<<optional local>>] as OLLAMA_SVC #EAD9F7
    [Stanford CoreNLP 4.5.7\n<<NLP fallback>>] as CORENLP #EAD9F7
  }

  package "Persistence Layer" as PERSIST_LAYER {
    [DomainDiagramRepository] as DOM_REPO #F2F3F4
    [DiagramRepository] as LEGACY_REPO #F2F3F4
    [DomainDiagramEvaluationRepository] as EVAL_REPO #F2F3F4
  }
}

package "Data Tier  ·  PostgreSQL 18.3" as DATA_TIER {
  database "ai_diagrams\n(localhost:5432)" as PG {
    [domain_diagrams\n(primary pipeline)] as T_DOMAIN #FDEBD0
    [diagrams\n(legacy pipeline)] as T_LEGACY #FDEBD0
  }
}

cloud "External Services" as EXTERNAL {
  [OpenAI API\ngpt-4o · gpt-4o-mini] as OPENAI_API #EBEBEB
  [Ollama Server\n<<optional — local>>] as OLLAMA_API #EBEBEB
}

' --- Client to REST ---
SPA --> PUML_CTRL : POST /api/diagram/generate\nGET /api/diagram/{id}/png|svg
SPA --> DIAG_CTRL  : POST /api/diagrams/from-pdf\nPOST /api/diagrams/from-url
SPA --> DRAWIO_CTRL : GET /api/diagram/{id}/drawio

' --- REST to Services (Primary) ---
PUML_CTRL --> CDS

' --- Primary Pipeline internal ---
CDS --> CLASSIFY
CDS --> SUGGEST
CDS --> EXTRACT
CDS --> GEN
CDS --> RENDER
CDS --> DOM_REPO
SUGGEST --> CLASSIFY

' --- REST to Services (Legacy) ---
DIAG_CTRL --> DIAG_SVC
DIAG_SVC --> REGISTRY

' --- DrawIO dual-table fallback ---
DRAWIO_CTRL --> LEGACY_REPO : query first
DRAWIO_CTRL --> DOM_REPO : fallback if absent

' --- AI Provider ---
AI_IFACE <|.. OPENAI_SVC
AI_IFACE <|.. OLLAMA_SVC
CLASSIFY --> AI_IFACE
EXTRACT --> AI_IFACE
EXTRACT ..> CORENLP : <<fallback on HTTP 429>>

OPENAI_SVC --> OPENAI_API : HTTPS
OLLAMA_SVC --> OLLAMA_API  : HTTP (local)

' --- Repositories to DB ---
DOM_REPO    ..> T_DOMAIN  : JDBC / HikariCP
LEGACY_REPO ..> T_LEGACY  : JDBC / HikariCP

@enduml
```

---

## Figure 4.2 — Backend Component Diagram

Details all packages within the Spring Boot application tier. The dashed note on
`DiagramGeneratorRegistry` marks it as unreachable dead code in the primary pipeline:
the registry auto-discovers thirteen generators at startup but is never called by
`PlantUmlGenerationServiceImpl`, which performs all generation via an inline switch.

```plantuml
@startuml Figure_4_2_Backend_Components

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 10
skinparam shadowing false
skinparam roundcorner 6
skinparam arrowThickness 1.2
skinparam packageStyle rectangle
skinparam package {
  BackgroundColor #FEFEFE
  BorderColor #7F8C8D
  FontStyle bold
}
skinparam component {
  BorderColor #2471A3
  FontSize 10
}

title Figure 4.2 — Backend Component Diagram\ncom.example.aidiagramgenerator

package "controller" {
  [PlantUmlDiagramController] as C1 #D6EAF8
  [DiagramController] as C2 #D6EAF8
  [DiagramDrawIoController] as C3 #D6EAF8
  [AnalyticsController] as C4 #D6EAF8
  [EvaluationController] as C5 #D6EAF8
  [HealthController] as C6 #D6EAF8
}

package "service  (orchestration)" {
  [ConfidenceDiagramService\n<<interface>>] as SVC_CONF
  [ConfidenceDiagramServiceImpl] as CDS #D5F5E3
  [MermaidRenderer\n<<interface>>] as SVC_MR
}

package "service  (intelligence)" {
  [DiagramClassificationService\n<<interface>>] as SVC_CLASS
  [DiagramClassificationServiceImpl] as CLASS_IMPL #FCF3CF
  [DiagramSuggestionService\n<<interface>>] as SVC_SUGG
  [DiagramSuggestionServiceImpl] as SUGG_IMPL #FCF3CF
  [SemanticExtractionService\n<<interface>>] as SVC_EXT
  [SemanticExtractionServiceImpl] as EXT_IMPL #FCF3CF
}

package "service  (generation)" {
  [PlantUmlGenerationService\n<<interface>>] as SVC_GEN
  [PlantUmlGenerationServiceImpl] as GEN_IMPL #FDEBD0
  note bottom of GEN_IMPL
    11-case switch (CLASS, SEQUENCE, ER,
    USE_CASE, COMPONENT, DEPLOYMENT,
    ACTIVITY, STATE, OBJECT,
    MICROSERVICES, COLLABORATION)
    Switch duplicated in two overloads.
  end note
}

package "service  (generation  — dead code)" {
  [ActivityDiagramGeneratorServiceImpl] as DEAD1 #F5B7B1
  [CollaborationDiagramGeneratorServiceImpl] as DEAD2 #F5B7B1
  [ComponentDiagramGeneratorServiceImpl] as DEAD3 #F5B7B1
  [DeploymentDiagramGeneratorServiceImpl] as DEAD4 #F5B7B1
  [ObjectDiagramGeneratorServiceImpl] as DEAD5 #F5B7B1
  [StateDiagramGeneratorServiceImpl] as DEAD6 #F5B7B1
  note top of DEAD1
    <<dead code>>
    Compiled but never injected.
    Abandoned refactoring artefacts.
  end note
}

package "service/render" {
  [DiagramRenderingService\n<<interface>>] as SVC_REND
  [DiagramRenderingServiceImpl] as REND_IMPL #FDEBD0
}

package "service/generation  (legacy strategy)" {
  [DiagramGenerator\n<<interface>>] as GEN_IFACE
  [DiagramGeneratorRegistry] as REGISTRY #F9E79F
  [ClassDiagramGenerator] as GEN_CLS
  [SequenceDiagramGenerator] as GEN_SEQ
  [ErDiagramGenerator ... +10] as GEN_OTHERS
  note bottom of REGISTRY
    <<unused by primary pipeline>>
    Auto-discovers 13 generators
    via Spring injection.
    Never called by
    PlantUmlGenerationServiceImpl.
  end note
}

package "ai" {
  [AiModelService\n<<interface>>] as AI_IFACE #EAD9F7
  [OpenAiService] as OPENAI #EAD9F7
  [OllamaService] as OLLAMA #EAD9F7
  [LlmResult] as LLM_RES
  [AiServiceException] as AI_EX
}

package "config" {
  [AiProviderConfig\n<<@Configuration>>] as AI_CFG #EAD9F7
  note right of AI_CFG
    Reads ai.provider property.
    Registers OpenAiService or
    OllamaService as @Primary.
  end note
}

package "exception" {
  [GlobalExceptionHandler\n<<@ControllerAdvice>>] as GEX #F9E79F
  [InvalidDiagramRequestException] as EX_INV
  [DiagramNotFoundException] as EX_NF
  [DiagramGenerationException] as EX_GEN
}

package "repository" {
  [DomainDiagramRepository] as R_DOM #F2F3F4
  [DiagramRepository] as R_LEG #F2F3F4
  [DomainDiagramEvaluationRepository] as R_EVAL #F2F3F4
}

package "dto/request" {
  [GenerationRequest\n(@JsonAlias text|description|inputText)] as DTO_GEN
  [EvaluationRequest] as DTO_EVAL
}

package "domain" {
  [Diagram\n@Table(domain_diagrams)] as DOM_DIAG
  [DiagramType\n<<enum · 11 values>>] as DOM_TYPE
  [SemanticModel] as SEM_MOD
  [StyleProfile] as STYLE
  [LayoutProfile] as LAYOUT
}

package "entity" {
  [Diagram\n@Table(diagrams)] as ENT_DIAG
}

package "enums" {
  [DiagramType\n<<enum · 13 values>>] as ENUM_TYPE
  note right of ENUM_TYPE
    Incompatible with domain.DiagramType.
    Adds ARCHITECTURE and C4_CONTEXT.
    Used exclusively by legacy pipeline.
  end note
}

' Interfaces
SVC_CONF <|.. CDS
SVC_CLASS <|.. CLASS_IMPL
SVC_SUGG <|.. SUGG_IMPL
SVC_EXT <|.. EXT_IMPL
SVC_GEN <|.. GEN_IMPL
SVC_REND <|.. REND_IMPL
AI_IFACE <|.. OPENAI
AI_IFACE <|.. OLLAMA
GEN_IFACE <|.. GEN_CLS
GEN_IFACE <|.. GEN_SEQ
GEN_IFACE <|.. GEN_OTHERS

' Controller wiring
C1 --> CDS
C1 --> SVC_SUGG
C1 --> SVC_REND
C1 --> R_DOM
C1 --> R_LEG
C1 --> SVC_MR
C1 --> R_EVAL
C2 --> REGISTRY
C3 --> R_LEG
C3 --> R_DOM

' Primary pipeline wiring
CDS --> CLASS_IMPL
CDS --> SUGG_IMPL
CDS --> EXT_IMPL
CDS --> GEN_IMPL
CDS --> REND_IMPL
CDS --> R_DOM
SUGG_IMPL --> CLASS_IMPL
CLASS_IMPL --> AI_IFACE
EXT_IMPL --> AI_IFACE
EXT_IMPL ..> [Stanford CoreNLP\n<<NLP fallback>>] : activate on failure

' Generation
GEN_IMPL --> SEM_MOD
GEN_IMPL --> DOM_TYPE

' Legacy
REGISTRY --> GEN_IFACE

' Config
AI_CFG --> OPENAI
AI_CFG --> OLLAMA

' Data
R_DOM --> DOM_DIAG
R_LEG --> ENT_DIAG

@enduml
```

---

## Figure 4.3 — Frontend–Backend Interaction Sequence Diagram

Illustrates the complete client–server interaction for a standard diagram generation request,
including the MEDIUM-confidence suggestion branch (where the system returns a suggestion
card and waits for user confirmation before generating) and the subsequent asset download flow.

```plantuml
@startuml Figure_4_3_Frontend_Backend_Sequence

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam shadowing false
skinparam roundcorner 6
skinparam sequenceMessageAlign center
skinparam responseMessageBelowArrow true

skinparam actor {
  BackgroundColor white
  BorderColor #2C3E50
}
skinparam participant {
  BackgroundColor #D6EAF8
  BorderColor #2471A3
  FontColor #1A252F
}
skinparam database {
  BackgroundColor #FDEBD0
  BorderColor #E67E22
}

autonumber

title Figure 4.3 — Frontend–Backend Interaction Sequence Diagram

actor "End User" as USER
participant "React 18 SPA\n(index.html)" as SPA
participant "PlantUmlDiagramController" as CTRL
participant "ConfidenceDiagramServiceImpl" as CDS
database "PostgreSQL\n(domain_diagrams)" as DB

== Standard Generation Flow ==

USER -> SPA : Types description text,\nselects diagram type from dropdown
SPA -> SPA : mapTypeToEnum()\n"Class Diagram" → "CLASS"

SPA -> CTRL : POST /api/diagram/generate\nContent-Type: application/json\n{\n  "text": "A User has many Orders...",\n  "diagramType": "CLASS"\n}

CTRL -> CDS : process(text, diagramType)
note right of CDS
  Classification → confidence score
  evaluated against thresholds:
  ≥ 70%: HIGH (generate)
  40–69%: MEDIUM (suggest)
  < 40%: LOW (reject)
end note

alt HIGH confidence (score ≥ 70)
  CDS -> DB : save(DomainDiagram entity)
  DB --> CDS : id = UUID
  CDS --> CTRL : GenerationResult { id, plantUmlCode,\n  diagramType, confidence }
  CTRL --> SPA : 200 OK\n{ id, plantUmlCode, diagramType, confidence }
  SPA -> SPA : render PlantUML code in <pre>\nshow Download PNG · SVG buttons
  SPA --> USER : Diagram displayed

else MEDIUM confidence (40 ≤ score < 70)
  CDS --> CTRL : SuggestionResult { suggestedType,\n  confidence, message }
  CTRL --> SPA : 200 OK\n{ suggestion: { suggestedType, confidence, message } }
  SPA --> USER : Suggestion card:\n"We suggest SEQUENCE diagram (55%).\nProceed?"

  USER -> SPA : Clicks "Proceed Anyway"
  SPA -> CTRL : POST /api/diagram/generate\n{\n  "text": "...",\n  "diagramType": "SEQUENCE",\n  "forceGenerate": true\n}
  CTRL -> CDS : process(text, SEQUENCE, forceGenerate=true)
  note right of CDS
    forceGenerate=true bypasses
    confidence gate entirely.
  end note
  CDS -> DB : save(DomainDiagram entity)
  DB --> CDS : id = UUID
  CDS --> CTRL : GenerationResult { id, plantUmlCode, ... }
  CTRL --> SPA : 200 OK
  SPA --> USER : Diagram displayed

else LOW confidence (score < 40) or unsupported type
  CDS --> CTRL : throws InvalidDiagramRequestException
  CTRL --> SPA : 400 Bad Request\n{ error: "...", message: "rejected type: FLOWCHART" }
  SPA --> USER : Error message displayed
end

== Asset Download ==

USER -> SPA : Clicks "Download PNG"
SPA -> CTRL : GET /api/diagram/{uuid}/png
CTRL -> DB : findById(uuid)
DB --> CTRL : DomainDiagram { plantUmlCode }
CTRL -> CTRL : DiagramRenderingService\n.renderToPng(plantUmlCode)
CTRL --> SPA : 200 OK  Content-Type: image/png\nContent-Disposition: attachment

USER -> SPA : Clicks "Export to Draw.io"
SPA -> CTRL : GET /api/diagram/{uuid}/drawio
note over CTRL
  DiagramDrawIoController queries
  diagrams table first, then
  domain_diagrams as fallback.
end note
CTRL --> SPA : 200 OK  Content-Type: application/xml\n<mxfile ...>

@enduml
```

---

## Figure 4.4 — Diagram Generation Pipeline Sequence Diagram

Illustrates the complete internal processing sequence of the primary PlantUML pipeline
for a request that reaches the HIGH-confidence generation path. The diagram shows the
five-layer classification cascade partially failing at the AI layer (HTTP 429 quota-exceeded),
the system recovering through keyword scoring (Layer 4), semantic extraction falling back
to Stanford CoreNLP, and the complete generation-through-persistence sequence.

```plantuml
@startuml Figure_4_4_Pipeline_Sequence

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 10
skinparam shadowing false
skinparam roundcorner 6
skinparam responseMessageBelowArrow true
skinparam sequenceMessageAlign left

skinparam participant {
  BackgroundColor #D5F5E3
  BorderColor #1E8449
  FontColor #1A252F
  FontSize 10
}
skinparam database {
  BackgroundColor #FDEBD0
  BorderColor #E67E22
}

autonumber

title Figure 4.4 — Diagram Generation Pipeline Sequence Diagram\nPrimary PlantUML Pipeline · HIGH-confidence path · AI fallback to CoreNLP

participant "PlantUmlDiagram\nController" as CTRL
participant "ConfidenceDiagram\nServiceImpl" as CDS
participant "DiagramClassification\nServiceImpl" as CLASSIFY
participant "OpenAiService" as OPENAI #EAD9F7
participant "DiagramSuggestion\nServiceImpl" as SUGGEST
participant "SemanticExtraction\nServiceImpl" as EXTRACT
participant "Stanford CoreNLP\n(NLP fallback)" as CORENLP #EAD9F7
participant "PlantUmlGeneration\nServiceImpl" as GEN
participant "DiagramRendering\nServiceImpl" as RENDER
database "DomainDiagram\nRepository" as REPO

[-> CTRL : POST /api/diagram/generate\n{ text, diagramType: "CLASS" }

CTRL -> CTRL : mapToEnum("CLASS") → domain.DiagramType.CLASS\nValidate @Size(max=10000) on text

CTRL -> CDS : process(text, DiagramType.CLASS)

== Step 1: Diagram Type Classification (5-Layer Cascade) ==

CDS -> CLASSIFY : classify(text)

CLASSIFY -> CLASSIFY : Layer 1 — EXPLICIT_TYPE_PATTERNS\n28 compiled regex patterns: no match

CLASSIFY -> OPENAI : Layer 2 — classify(text)\n{ "diagramType": ?, "confidence": ?, "reasoning": ? }
OPENAI --> CLASSIFY : HTTP 429 Rate Limit Exceeded\nLlmResult{ success=false }

CLASSIFY -> CLASSIFY : Layer 3 — SEMANTIC_CATEGORIES\npattern scoring per candidate type

CLASSIFY -> CLASSIFY : Layer 4 — keyword scoring\n11 term sets × weighted scores

CLASSIFY --> CDS : DiagramType.CLASS  (score: 75)

== Step 2: Confidence Evaluation ==

CDS -> SUGGEST : suggest(text, DiagramType.CLASS)
SUGGEST -> CLASSIFY : classify(text)  [delegates internally]
CLASSIFY --> SUGGEST : DiagramType.CLASS (score: 75)
SUGGEST --> CDS : SuggestionResult{ confidence: 75, tier: HIGH }

CDS -> CDS : 75 ≥ 70 → HIGH tier\n→ proceed to generateDiagram()

== Step 3: Semantic Extraction ==

CDS -> EXTRACT : extract(text, DiagramType.CLASS)

EXTRACT -> OPENAI : callLLM(extractionPrompt)
OPENAI --> EXTRACT : HTTP 429 Rate Limit Exceeded\nLlmResult{ success=false }

EXTRACT -> CORENLP : CoreNLP pipeline
note right of CORENLP
  Stage 1: tokenize
  Stage 2: ssplit
  Stage 3: pos (Part-of-Speech)
  Stage 4: lemma
  Stage 5: ner (Named Entity Recognition)
  Stage 6: depparse (Dependency Parse)
end note
CORENLP --> EXTRACT : entities=[User, Order, OrderItem]\nrelationships=[User→Order (has-many),\n  Order→OrderItem (contains)]

EXTRACT --> CDS : SemanticModel{ entities, relationships, actions }

== Step 4: PlantUML Code Generation ==

CDS -> GEN : generate(semanticModel, styleProfile, seed)
GEN -> GEN : switch(CLASS) → generateClassDiagram(semanticModel)\nBuilds @startuml ... @enduml string\nfrom entity and relationship arrays
GEN --> CDS : "@startuml\nclass User { ... }\nUser --> Order\n@enduml"

== Step 5: Rendering ==

CDS -> RENDER : renderToPng(plantUmlCode)
RENDER -> RENDER : validate: non-null ✓\nvalidate: starts with @startuml ✓\nvalidate: size < 10 MB ✓\nSourceStringReader.generateImage()
RENDER --> CDS : PNG bytes (valid)

== Step 6: Persistence ==

CDS -> REPO : save(new Diagram(\n  inputText, DiagramType.CLASS,\n  plantUmlCode, modelUsed="nlp-corenlp"\n))
REPO --> CDS : saved — id = UUID

== Step 7: Response ==

CDS --> CTRL : GenerationResult{\n  id, plantUmlCode, diagramType,\n  confidence: 75, modelUsed: "nlp-corenlp"\n}
[<-- CTRL : 200 OK\n{ id, plantUmlCode, diagramType, confidence }

@enduml
```

---

## Figure 4.5 — AI Provider Integration Class Diagram

Shows the `AiModelService` abstraction layer, its two concrete implementations, the
`AiProviderConfig` factory, and all service classes that depend on the interface.
`SemanticExtractionServiceImpl` carries an additional dependency on `StanfordCoreNLP`,
activated as a fallback whenever `LlmResult.isSuccess()` returns false.

```plantuml
@startuml Figure_4_5_AI_Provider_Integration

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam shadowing false
skinparam roundcorner 6
skinparam arrowThickness 1.5

skinparam interface {
  BackgroundColor #EAD9F7
  BorderColor #6C3483
  FontColor #1A252F
}
skinparam class {
  BackgroundColor #FAFAFA
  BorderColor #2471A3
  FontColor #1A252F
  HeaderBackgroundColor #D6EAF8
}
skinparam note {
  BackgroundColor #FFFDE7
  BorderColor #F9A825
}

title Figure 4.5 — AI Provider Integration\ncom.example.aidiagramgenerator.ai

interface AiModelService {
  + callLLM(prompt : String) : LlmResult
  + classify(text : String) : LlmResult
}

class LlmResult {
  - content : String
  - success : boolean
  + getContent() : String
  + isSuccess() : boolean
}

class OpenAiService {
  - model : String
  - diagramModel : String
  - apiKey : String
  - webClient : WebClient
  --
  + callLLM(prompt : String) : LlmResult
  + classify(text : String) : LlmResult
  - callWithJsonResponse(prompt : String) : LlmResult
}

class OllamaService {
  - baseUrl : String
  - model : String
  - webClient : WebClient
  --
  + callLLM(prompt : String) : LlmResult
  + classify(text : String) : LlmResult
}

class AiProviderConfig <<@Configuration>> {
  - provider : String  //ai.provider property
  --
  + aiModelService(\n    openAiService,\n    ollamaService\n  ) : AiModelService  <<@Bean @Primary>>
}

class AiServiceException {
  - message : String
  + AiServiceException(message : String)
}

class DiagramClassificationServiceImpl {
  - aiService : AiModelService
  - EXPLICIT_TYPE_PATTERNS : List<Pattern>
  - SEMANTIC_CATEGORIES : Map<DiagramType, List<Pattern>>
  - KEYWORD_SETS : Map<DiagramType, Set<String>>
  --
  + classify(text : String) : DiagramType
  - layer1ExplicitPatterns(text) : Optional<DiagramType>
  - layer2AiClassification(text) : Optional<DiagramType>
  - layer3SemanticPatterns(text) : Optional<DiagramType>
  - layer4KeywordScoring(text) : Optional<DiagramType>
  - layer5AiFallback(text) : DiagramType
}

class SemanticExtractionServiceImpl {
  - aiService : AiModelService
  - nlpPipeline : StanfordCoreNLP
  --
  + extract(text : String, type : DiagramType) : SemanticModel
  - extractWithAi(text, type) : SemanticModel
  - extractWithNlp(text) : SemanticModel
}

class StanfordCoreNLP <<external library>> {
  - annotators : [tokenize, ssplit,\n  pos, lemma, ner, depparse]
  --
  + annotate(document : CoreDocument)
}

class SemanticModel {
  - entities : List<EntityNode>
  - relationships : List<Relationship>
  - actions : List<String>
}

note right of OpenAiService
  **gpt-4o-mini** — classification,
  structured JSON responses
  **gpt-4o** — full diagram generation
  via openai.model /
  openai.diagram.model properties
end note

note right of OllamaService
  Configured via:
  ai.provider=ollama
  Provides fully local,
  quota-free alternative.
end note

note bottom of StanfordCoreNLP
  Loaded at application startup (~3.5 s).
  Reused across all subsequent calls.
  Activates when AiModelService
  returns isSuccess() = false.
end note

AiModelService <|.. OpenAiService
AiModelService <|.. OllamaService
AiProviderConfig --> OpenAiService : creates
AiProviderConfig --> OllamaService : creates
AiProviderConfig ..> AiModelService : registers <<@Primary>>
OpenAiService --> LlmResult : returns
OllamaService --> LlmResult : returns
OpenAiService ..> AiServiceException : throws
DiagramClassificationServiceImpl --> AiModelService : uses
SemanticExtractionServiceImpl --> AiModelService : uses
SemanticExtractionServiceImpl --> StanfordCoreNLP : fallback
SemanticExtractionServiceImpl --> SemanticModel : produces

@enduml
```

---

## Figure 4.6 — Deployment Diagram

Documents the physical and logical deployment topology for both the development environment
(single developer workstation) and the recommended production topology (load-balanced
multi-replica deployment with managed database and object storage).

```plantuml
@startuml Figure_4_6_Deployment

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam shadowing false
skinparam roundcorner 8
skinparam arrowThickness 1.5

skinparam node {
  BackgroundColor #E8F8F5
  BorderColor #1E8449
  FontStyle bold
}
skinparam artifact {
  BackgroundColor #D6EAF8
  BorderColor #2471A3
}
skinparam database {
  BackgroundColor #FDEBD0
  BorderColor #E67E22
}
skinparam cloud {
  BackgroundColor #F5EEF8
  BorderColor #7D3C98
}
skinparam note {
  BackgroundColor #FFFDE7
  BorderColor #F9A825
}

title Figure 4.6 — Deployment Diagram\nDevelopment Environment (top) · Production Topology (bottom)

' ---- Development Environment ----

node "Developer Workstation  (macOS / Linux)" as WORKSTATION {

  node "JVM Process  (Java 24.0.1)" as JVM {
    artifact "ai-diagram-generator.jar" as JAR {
      artifact "Spring Boot 4.0.2\n(Embedded Tomcat)" as SPRINGBOOT
      artifact "Stanford CoreNLP 4.5.7\n(loads at startup, ~3.5 s)" as NLP_LIB
      artifact "PlantUML 1.2024.3\n(net.sourceforge.plantuml)" as PUML_LIB
      artifact "Apache PDFBox 3.0.3\n(PDF text extraction)" as PDF_LIB
      artifact "React 18 SPA\nstatic/index.html" as SPA_STATIC
    }
  }

  database "PostgreSQL 18.3\n(localhost:5432)" as PG_DEV {
    artifact "domain_diagrams" as T1
    artifact "diagrams" as T2
  }
}

node "Client Browser" as BROWSER {
  artifact "React 18 SPA\n(CDN Babel · CDN Mermaid 10)" as SPA_BROWSER
}

cloud "OpenAI Cloud" as OPENAI_CLOUD {
  artifact "gpt-4o-mini\n(classification)" as GPT_MINI
  artifact "gpt-4o\n(generation)" as GPT4O
}

node "Ollama Server  (optional — local)" as OLLAMA_NODE {
  artifact "llama3 / mistral\n(local model)" as LOCAL_MODEL
}

BROWSER --> JVM : HTTP  :8080
JVM --> GPT_MINI : HTTPS :443
JVM --> GPT4O    : HTTPS :443
JVM --> PG_DEV   : JDBC  :5432
JVM ..> OLLAMA_NODE : HTTP :11434\n<<optional>>

note right of JVM
  Start: ./mvnw spring-boot:run
  Profile: dev (H2 for tests)
  Startup time: ~6.3 seconds
  Swagger UI: :8080/swagger-ui.html
end note

' ---- Production Topology (Recommended) ----

node "Load Balancer / Reverse Proxy" as LB {
  artifact "Nginx / AWS ALB\n(HTTPS :443)" as PROXY
}

node "Application Server  (×N replicas)" as APP_SERVER {
  artifact "ai-diagram-generator.jar" as JAR_PROD
}

database "Managed Database\n(PostgreSQL RDS / Cloud SQL)" as PG_PROD {
  artifact "domain_diagrams" as T1_PROD
  artifact "diagrams" as T2_PROD
}

cloud "OpenAI API\n(gpt-4o · gpt-4o-mini)" as OPENAI_PROD

cloud "Object Storage\n(S3 / GCS)" as OBJECT_STORE {
  artifact "Rendered images\n(PNG · SVG · PDF)" as IMAGES
}

node "Browser Client" as BROWSER_PROD
BROWSER_PROD --> PROXY   : HTTPS :443
PROXY --> JAR_PROD       : HTTP  :8080
JAR_PROD --> PG_PROD     : JDBC  :5432
JAR_PROD --> OPENAI_PROD : HTTPS :443
JAR_PROD --> OBJECT_STORE : SDK

@enduml
```

---

## Figure 4.7 — Service Dependency Diagram

Shows the Spring bean injection graph for the primary PlantUML pipeline. Each arrow
represents a constructor-injected dependency. The controller's seven constructor parameters
are shown explicitly. Dead-code service beans (those defined but never injected anywhere)
are excluded; they are documented in Figure 4.2.

```plantuml
@startuml Figure_4_7_Service_Dependencies

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 10
skinparam shadowing false
skinparam roundcorner 8
skinparam arrowThickness 1.5
skinparam linetype ortho

skinparam component {
  FontSize 10
  BorderColor #2471A3
}
skinparam package {
  BackgroundColor #FAFAFA
  BorderColor #7F8C8D
  FontStyle bold
}
skinparam interface {
  BackgroundColor #EAD9F7
  BorderColor #6C3483
  FontSize 10
}

title Figure 4.7 — Service Dependency Diagram (Spring Bean Injection Graph)\nPrimary PlantUML Pipeline

package "REST Layer" {
  [PlantUmlDiagramController] as CTRL #D6EAF8
}

package "Orchestration" {
  [ConfidenceDiagramServiceImpl] as CDS #D5F5E3
}

package "Intelligence" {
  [DiagramClassificationServiceImpl] as CLASSIFY #FCF3CF
  [DiagramSuggestionServiceImpl] as SUGGEST #FCF3CF
  [SemanticExtractionServiceImpl] as EXTRACT #FCF3CF
}

package "Generation" {
  [PlantUmlGenerationServiceImpl] as GEN #FDEBD0
  [StyleProfileServiceImpl] as STYLE #FDEBD0
}

package "Rendering" {
  [DiagramRenderingServiceImpl] as RENDER #FDEBD0
}

package "AI Provider" {
  interface "AiModelService" as AI_IFACE
  [OpenAiService\n<<@Primary when ai.provider=openai>>] as OPENAI_SVC #EAD9F7
  [OllamaService\n<<@Primary when ai.provider=ollama>>] as OLLAMA_SVC #EAD9F7
  [Stanford CoreNLP\n<<loaded at startup>>] as CORENLP #EAD9F7
}

package "Repository Layer" {
  [DomainDiagramRepository] as DOM_REPO #F2F3F4
  [DiagramRepository\n(legacy diagrams table)] as LEGACY_REPO #F2F3F4
  [DomainDiagramEvaluationRepository] as EVAL_REPO #F2F3F4
  [MermaidRenderer] as MERMAID_REND #F2F3F4
}

note as CTRL_NOTE
  PlantUmlDiagramController
  constructor (7 parameters):
  1. ConfidenceDiagramService
  2. DiagramSuggestionService
  3. DiagramRenderingService
  4. DomainDiagramRepository
  5. DiagramRepository
  6. MermaidRenderer
  7. DomainDiagramEvaluationRepository
end note

' Controller dependencies
CTRL --> CDS
CTRL --> SUGGEST
CTRL --> RENDER
CTRL --> DOM_REPO
CTRL --> LEGACY_REPO
CTRL --> MERMAID_REND
CTRL --> EVAL_REPO

' CDS (orchestrator) dependencies
CDS --> CLASSIFY
CDS --> SUGGEST
CDS --> EXTRACT
CDS --> GEN
CDS --> RENDER
CDS --> DOM_REPO

' Intelligence dependencies
SUGGEST --> CLASSIFY
CLASSIFY --> AI_IFACE
EXTRACT --> AI_IFACE
EXTRACT ..> CORENLP : <<activates on LlmResult.isSuccess()=false>>

' Generation dependencies
GEN --> STYLE

' AI provider
AI_IFACE <|.. OPENAI_SVC
AI_IFACE <|.. OLLAMA_SVC

CTRL_NOTE .. CTRL

@enduml
```

---

## Figure 4.8 — Database Entity-Relationship Diagram

Documents the relational schema of both tables. The two tables share no foreign-key
relationship; they are produced by two independent pipelines and are queried together
only by `DiagramDrawIoController`, which applies a sequential fallback lookup.
The `parent_diagram_id` column in the `diagrams` table is a nullable self-referencing
foreign key supporting diagram versioning within the legacy pipeline.

```plantuml
@startuml Figure_4_8_Database_ERD

!theme plain
skinparam backgroundColor white
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam shadowing false
skinparam roundcorner 6

skinparam entity {
  BackgroundColor #FDF2E9
  BorderColor #E67E22
  FontColor #1A252F
  HeaderBackgroundColor #F0B27A
}

title Figure 4.8 — Database Entity-Relationship Diagram\nPostgreSQL 18.3 · ai_diagrams schema

entity "domain_diagrams" as DOMAIN_DIAG {
  * id : UUID  <<PK  @GeneratedValue(UUID)>>
  --
  * input_text : TEXT  NOT NULL
  * diagram_type : VARCHAR(50)  NOT NULL
    <<CHECK: CLASS | SEQUENCE | ER | USE_CASE | ACTIVITY
     | STATE | COMPONENT | DEPLOYMENT | OBJECT
     | MICROSERVICES | COLLABORATION>>
  * plant_uml_code : TEXT  NOT NULL
    model_used : VARCHAR(255)
    <<e.g. "openai-gpt4o" | "nlp-corenlp" | "template">>
  * created_at : TIMESTAMP  NOT NULL  <<@PrePersist>>
}

note right of DOMAIN_DIAG
  JPA entity: domain.Diagram
  Repository: DomainDiagramRepository
  Pipeline: Primary PlantUML
  Managed by: ConfidenceDiagramServiceImpl
  Stores rendered PlantUML source;
  images are re-rendered on demand
  from plant_uml_code.
end note

entity "diagrams" as LEGACY_DIAG {
  * id : UUID  <<PK  @GeneratedValue(UUID)>>
  --
  * input_type : VARCHAR(50)  NOT NULL
    <<enum: InputType (TEXT | XML | URL | PDF)>>
  * input_content : TEXT  NOT NULL
  * diagram_type : VARCHAR(50)  NOT NULL
    <<enums.DiagramType (13 values)>>
  * mermaid_code : TEXT  NOT NULL
    explanation : TEXT
  * version_number : INT  NOT NULL  DEFAULT 1
    parent_diagram_id : UUID  <<FK → diagrams.id, nullable>>
  * created_at : TIMESTAMP  NOT NULL  <<@PrePersist>>
}

note right of LEGACY_DIAG
  JPA entity: entity.Diagram
  Repository: DiagramRepository
  Pipeline: Legacy Mermaid
  Managed by: DiagramServiceImpl
  Supports versioning via
  parent_diagram_id self-reference.
end note

entity "domain_diagram_evaluations" as EVAL {
  * id : UUID  <<PK>>
  --
  * diagram_id : UUID  <<FK → domain_diagrams.id>>
    input_text : TEXT
    expected_type : VARCHAR(50)
    actual_type : VARCHAR(50)
    confidence_score : INT
    evaluation_result : VARCHAR(50)
  * created_at : TIMESTAMP  NOT NULL
}

note right of EVAL
  JPA entity: domain.DiagramEvaluation
  Repository: DomainDiagramEvaluationRepository
  Used by EvaluationController for
  batch accuracy measurement against
  evaluation-dataset.json
end note

entity "diagram_history" as HISTORY {
  * id : UUID  <<PK>>
  --
  * diagram_id : UUID  <<FK → diagrams.id>>
    previous_mermaid_code : TEXT
    change_description : VARCHAR(500)
  * created_at : TIMESTAMP  NOT NULL
}

note right of HISTORY
  JPA entity: entity.DiagramHistory
  Tracks edit history for legacy
  pipeline diagrams.
end note

' Relationships
LEGACY_DIAG ||--o{ LEGACY_DIAG : "parent_diagram_id\n(versioning self-reference)"
DOMAIN_DIAG ||--o{ EVAL : "diagram_id"
LEGACY_DIAG ||--o{ HISTORY : "diagram_id"

note as PIPELINE_NOTE
  **Two-Pipeline Data Isolation**

  domain_diagrams and diagrams share
  no foreign-key relationship.
  They are produced by independent
  pipelines and use incompatible
  DiagramType enumerations:

  domain.DiagramType  → 11 values (primary)
  enums.DiagramType   → 13 values (legacy)

  DiagramDrawIoController bridges both
  tables via a sequential fallback lookup:
  1. Query diagrams by UUID
  2. If absent → query domain_diagrams
end note

@enduml
```

---

## Rendering Notes

| Diagram | Type | Key UML Notation |
|---|---|---|
| Figure 4.1 | Component / Package | `[Component]`, `package`, `cloud`, `database` |
| Figure 4.2 | Component | `[Component]`, `<\|..` (realization), `package` |
| Figure 4.3 | Sequence | `actor`, `participant`, `alt/else/end`, `autonumber` |
| Figure 4.4 | Sequence | `participant`, `database`, `==`, `note`, `autonumber` |
| Figure 4.5 | Class | `interface`, `class`, `<\|..`, `-->`, `..>` |
| Figure 4.6 | Deployment | `node`, `artifact`, `database`, `cloud` |
| Figure 4.7 | Component | `[Component]`, `interface`, `package`, `..>` |
| Figure 4.8 | Entity-Relationship | `entity`, `\|\|--o{` (one-to-many), `note` |

All diagrams use `!theme plain` with explicit `skinparam` directives to produce clean,
print-quality output suitable for thesis documentation.
