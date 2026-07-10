# Architecture Redesign Plan — AI Diagram Generator

> **Status**: Analysis & Recommendation Document  
> **Scope**: Full codebase — `com.example.aidiagramgenerator`  
> **Date**: May 2026  
> **Based on**: Source code at revision HEAD

---

## Table of Contents

1. [Current Architecture Problems](#1-current-architecture-problems)
2. [Proposed Improved Architecture](#2-proposed-improved-architecture)
3. [Refactoring Roadmap](#3-refactoring-roadmap)
4. [Recommended Package Structure](#4-recommended-package-structure)
5. [Recommended Service Hierarchy](#5-recommended-service-hierarchy)
6. [UML Generator Abstraction Design](#6-uml-generator-abstraction-design)
7. [Recommended Interfaces and Patterns](#7-recommended-interfaces-and-patterns)
8. [Scalability Recommendations](#8-scalability-recommendations)
9. [Performance Recommendations](#9-performance-recommendations)
10. [Maintainability Recommendations](#10-maintainability-recommendations)

---

## 1. Current Architecture Problems

### 1.1 Dual Pipeline Architecture — The Root Problem

The single most severe architectural issue is the existence of **two completely separate diagram pipelines** that share no code, no data model, and no contracts.

| Aspect | PlantUML Pipeline (Primary) | Mermaid Pipeline (Legacy) |
|---|---|---|
| Entry controller | `PlantUmlDiagramController` | `DiagramController` |
| URL namespace | `/api/diagram/*` | `/api/diagrams/*` |
| Output format | PlantUML → PNG / SVG | Mermaid → text |
| Orchestrator | `ConfidenceDiagramServiceImpl` | `DiagramServiceImpl` |
| Generator | `PlantUmlGenerationServiceImpl` (monolith) | `DiagramGeneratorRegistry` (strategy) |
| AI integration | `AiModelService` (via `OpenAiService`) | `OpenAiDiagramService` |
| Entity | `domain.Diagram` | `entity.Diagram` |
| Repository | `DomainDiagramRepository` | `DiagramRepository` |
| DB table | `domain_diagrams` | `diagrams` |
| DiagramType enum | `domain.DiagramType` (11 values) | `enums.DiagramType` (6+ values) |

These pipelines produce fundamentally different outputs (rendered images vs. text), but conceptually serve the same user need: generate a diagram from a description. The duplication costs double the maintenance effort for every future change.

---

### 1.2 Three Partial Strategy Implementations That Do Not Connect

The codebase has **three separate attempts at a strategy pattern for diagram generation**, none of which are wired together:

**Attempt A — Mermaid `DiagramGeneratorRegistry` (legacy, working)**  
Located in `service/generation/`. The `DiagramGeneratorRegistry` bean auto-discovers 13 `DiagramGenerator` implementations via Spring injection. These generators (`ClassDiagramGenerator`, `SequenceDiagramGenerator`, etc.) accept `ParsedInput` and return Mermaid syntax. This pattern works cleanly but only for the legacy pipeline.

**Attempt B — Individual `*DiagramGeneratorService` beans (dead code)**  
Located in the root `service/` package:
- `ActivityDiagramGeneratorServiceImpl`
- `CollaborationDiagramGeneratorServiceImpl`
- `ComponentDiagramGeneratorServiceImpl`
- `DeploymentDiagramGeneratorServiceImpl`
- `ObjectDiagramGeneratorServiceImpl`
- `StateDiagramGeneratorServiceImpl`

These six Spring beans exist and compile, but `PlantUmlGenerationServiceImpl` does **not inject any of them**. They are unreachable dead code in the current runtime.

**Attempt C — `PlantUmlGenerationServiceImpl` inline switch (active, monolithic)**  
The actual PlantUML generation is done by an 11-case `switch` embedded directly in `PlantUmlGenerationServiceImpl`. This switch appears **twice** — once in `generate(model, style, seed)` and again identically in `generate(model, style, layout)`. Adding a new diagram type requires editing this class in two places, and the entire generator for that type lives inline (often hundreds of lines).

The correct path is to collapse all three into a single clean strategy hierarchy.

---

### 1.3 God Class: `PlantUmlGenerationServiceImpl`

`PlantUmlGenerationServiceImpl` violates the Single Responsibility Principle severely:

- Dispatches to 11 different diagram types via a switch statement
- Implements every diagram generator inline (`generateClassDiagram`, `generateErDiagram`, `generateSequenceDiagram`, `generateUseCaseDiagram`, `generateComponentDiagram`, `generateDeploymentDiagram`, `generateActivityDiagram`, `generateStateDiagram`, `generateObjectDiagram`, `generateMicroservicesDiagram`, `generateCollaborationDiagram`)
- Also manages `LayoutProfile` generation (`generateRandomLayoutProfile`)
- The switch block is **literally duplicated** in two overloaded `generate()` methods

This makes the file enormous, untestable in isolation, and requires modifying a central class every time a diagram type is added or changed.

---

### 1.4 Duplicate DiagramType Enums

There are two enums that represent the same concept:

- **`domain.DiagramType`** — 11 values: `CLASS, ER, SEQUENCE, USE_CASE, COMPONENT, DEPLOYMENT, ACTIVITY, STATE, OBJECT, MICROSERVICES, COLLABORATION`
- **`enums.DiagramType`** — different set, used by `DiagramGeneratorRegistry` and all Mermaid generators

These are type-incompatible. Every boundary between the two pipelines requires a manual mapping or casting step. The PlantUML-side services cannot call Mermaid-side services without first mapping between enums.

---

### 1.5 Duplicate Entities, Repositories, and DB Tables

Two JPA entities represent "a diagram" with no shared hierarchy:

- **`domain.Diagram`** persisted to `domain_diagrams` via `DomainDiagramRepository`
- **`entity.Diagram`** persisted to `diagrams` via `DiagramRepository`

The `DiagramEvaluation` concept is also duplicated across `domain` and `entity` packages. This means evaluation queries, analytics, and retrieval APIs must be duplicated or written twice. It also means the DB has two tables for the same business object.

---

### 1.6 Duplicated Classification Logic in `DiagramSuggestionServiceImpl`

`DiagramSuggestionServiceImpl` hard-codes its own `EXPLICIT_TYPE_PATTERNS`, `SEMANTIC_CATEGORIES`, and `KEYWORD_SETS` that overlap with — and in many cases are older, less complete versions of — the same constants in `DiagramClassificationServiceImpl`.

The suggestion service only covers 6 diagram types (CLASS, ER, SEQUENCE, USE_CASE, COMPONENT, DEPLOYMENT), omitting the 5 newer types (ACTIVITY, STATE, OBJECT, MICROSERVICES, COLLABORATION) that were added to the PlantUML pipeline later. Any user asking for a "state machine" or "activity flow" will get misclassified by the suggestion layer even if the generation layer handles it correctly.

---

### 1.7 Wrong Semantic Extraction for Non-CLASS Diagram Types

`SemanticExtractionServiceImpl.extractWithAi()` always sends a CLASS-diagram-specific prompt to the LLM regardless of the actual target diagram type. The prompt asks the model to extract "classes, attributes, and methods" — semantics that are meaningless for a State diagram (which needs states and transitions) or a Sequence diagram (which needs actors and messages).

This means the `SemanticModel` produced for Activity/State/Sequence/Use Case diagrams is populated with class-oriented data, and the downstream generator must work around a semantically incorrect model. The NLP fallback in `extractWithNlp()` similarly uses class-oriented extraction logic for all types.

---

### 1.8 Missing Style Profiles for Five Diagram Types

`StyleProfileServiceImpl` logs the warning "No predefined style profile for type: X" for `ACTIVITY`, `STATE`, `OBJECT`, `MICROSERVICES`, and `COLLABORATION`. When no profile exists, the service falls back to a default that is tuned for CLASS diagrams.

The five newer diagram types therefore receive incorrect styling (wrong color schemes, arrow styles, and spacing rules designed for class box layouts rather than flow charts or state machines).

---

### 1.9 `LayoutProfile.Direction` String Mismatch

A hardcoded string `"top to bottom direction"` does not match any `LayoutProfile.Direction` enum constant, causing a continuous stream of log warnings about unknown direction values and forcing a random layout fallback on every generation. The effect is that diagram layout is non-deterministic even when a seed is provided, because the direction step always falls through to randomization.

---

### 1.10 `ConfidenceDiagramServiceImpl` Type Aliasing Loses Diagram Types

The `DIAGRAM_TYPE_ALIASES` map in `ConfidenceDiagramServiceImpl` maps both `ARCHITECTURE` and `C4_CONTEXT` to `COMPONENT`. Users who explicitly request an architecture or C4 diagram silently receive a component diagram instead. This is a silent data loss bug, not an intentional default.

---

### 1.11 `HttpRequestMethodNotSupportedException` Returns 500

`GlobalExceptionHandler` handles `DiagramNotFoundException` → 404, `InvalidDiagramRequestException` → 400, and generic `Exception` → 500, but does not handle `HttpRequestMethodNotSupportedException`. That exception falls through to the generic handler and returns HTTP 500 instead of the correct HTTP 405 Method Not Allowed. This also masks the real error to API consumers.

---

### 1.12 No Caching Layer

Every diagram request re-runs the full pipeline: classification → semantic extraction → AI call → PlantUML generation → PNG render. Identical input text submitted twice will perform two complete AI round-trips and two PlantUML renders. There is no content-addressable cache keyed on input hash + diagram type.

---

### 1.13 Synchronous Rendering Blocks Request Threads

`DiagramRenderingServiceImpl.render()` calls `SourceStringReader.generateImage()` synchronously on the HTTP request thread. PlantUML rendering is CPU-bound and can take hundreds of milliseconds for complex diagrams. Under load, this blocks Tomcat request threads proportionally to render time.

---

### 1.14 AI Provider Integration Is Fragile

`AiProviderConfig` selects `OpenAiService` or `OllamaService` via a single `ai.provider` property and marks one as `@Primary`. Both services implement `AiModelService`, but the abstraction leaks:

- `OpenAiService` exposes both `callLLM()` (plain text) and `callWithJsonResponse()` (structured JSON)
- `OllamaService` has a different internal structure and different defaults
- Callers that need JSON responses must directly cast to `OpenAiService` or `OllamaService`, breaking the abstraction
- There is no retry policy, circuit breaker, or timeout standardization between providers
- The 429 quota error from OpenAI causes a full silent fallback to NLP heuristics with no alerting or degraded-mode indication to the client

---

### 1.15 Frontend–Backend Contract Is Implicit and Fragile

The React SPA in `index.html` makes raw `fetch()` calls with hard-coded field names (`data.decision`, `data.diagramBase64`, `data.diagramSvg`, `data.explanation`). These field names are not validated by a schema. Any rename or restructuring of `GenerationResult` will silently break the UI with no compile-time error.

---

### 1.16 No Input Validation Pipeline

Request validation is scattered across:
- Bean validation annotations on DTOs (`@NotBlank`, `@Size`)
- Ad-hoc null checks in `ConfidenceDiagramServiceImpl`
- Ad-hoc length checks in `DiagramRenderingServiceImpl` (10MB PlantUML size limit)

There is no centralized validation pipeline, no sanitization of user input before it is sent to the LLM, and no protection against prompt-injection via diagram description fields.

---

## 2. Proposed Improved Architecture

### 2.1 Unified Single-Pipeline Architecture

Replace the dual pipeline with a single, format-agnostic pipeline:

```
HTTP Request
    │
    ▼
DiagramController (unified, single URL namespace /api/v2/diagrams)
    │
    ▼
DiagramOrchestrationService (replaces ConfidenceDiagramServiceImpl)
    │
    ├── InputValidationPipeline          (centralized validation)
    │
    ├── DiagramClassificationService     (single classifier)
    │
    ├── SemanticExtractionService        (type-aware extraction)
    │
    ├── StyleProfileService              (all 13 types covered)
    │
    ├── DiagramGeneratorStrategy         (replaces inline switch)
    │   └── PlantUmlDiagramGenerator[N] (one class per type)
    │
    ├── DiagramRenderingService          (async, format-aware)
    │
    └── DiagramRepository (unified)
            │
            ▼
        diagram table (single table)
```

### 2.2 Unified `DiagramType` Enum

Collapse `domain.DiagramType` and `enums.DiagramType` into a single canonical enum in a shared `model` package, covering all supported types. Every service operates on this single type.

```
com.example.aidiagramgenerator.model.DiagramType (canonical)
  CLASS, ER, SEQUENCE, USE_CASE, COMPONENT, DEPLOYMENT,
  ACTIVITY, STATE, OBJECT, MICROSERVICES, COLLABORATION,
  ARCHITECTURE, C4_CONTEXT
```

### 2.3 Plugin Architecture for Diagram Types

Each diagram type is self-contained in its own module:

```
DiagramTypePlugin (interface)
  ├── supports(): DiagramType
  ├── extractSemantics(text, context): SemanticModel
  ├── generatePlantUml(model, style, layout): String
  ├── defaultStyleProfile(): StyleProfile
  └── validate(request): ValidationResult

ClassDiagramPlugin    implements DiagramTypePlugin
SequenceDiagramPlugin implements DiagramTypePlugin
StateDiagramPlugin    implements DiagramTypePlugin
... (one class per type)
```

`DiagramPluginRegistry` auto-discovers all `DiagramTypePlugin` beans at startup via Spring's `List<DiagramTypePlugin>` injection. Adding a new diagram type requires **only** creating one new class that implements `DiagramTypePlugin` — no other class changes.

### 2.4 Type-Aware AI Provider Abstraction

```
AiProvider (interface)
  ├── classify(text): ClassificationResult
  ├── extractSemantics(text, DiagramType): SemanticModel
  ├── generateDiagramCode(semantics, DiagramType): String
  ├── isAvailable(): boolean
  └── getCapabilities(): ProviderCapabilities

OpenAiProvider  implements AiProvider
OllamaProvider  implements AiProvider
NlpFallback     implements AiProvider (always available)

AiProviderChain  (selects provider by availability + config, with retry)
```

The `NlpFallback` is a first-class `AiProvider` rather than ad-hoc code scattered in service implementations. The chain tries OpenAI → Ollama → NlpFallback and records which was used in the response.

### 2.5 Async Rendering Pipeline

```
DiagramRenderService
  ├── renderAsync(plantUml, format): CompletableFuture<byte[]>
  └── RenderExecutor (dedicated thread pool, configurable size)
```

Requests that trigger rendering return immediately with a job ID. Clients poll `GET /api/v2/diagrams/{id}/status` or receive a Server-Sent Event when rendering completes. Short renders (< 500ms, configurable) can optionally complete synchronously.

---

## 3. Refactoring Roadmap

Each phase is independently deployable and does not break existing behavior.

### Phase 1 — Cleanup and Stabilization (1–2 weeks)
**Goal**: Remove dead code, fix bugs, reduce noise. No behavior changes.

1. **Fix `GlobalExceptionHandler`**: Add explicit handler for `HttpRequestMethodNotSupportedException` → HTTP 405.
2. **Fix `LayoutProfile.Direction` mismatch**: Identify the actual enum constant name and replace the hardcoded string `"top to bottom direction"` with the correct enum reference.
3. **Delete dead beans**: Remove `ActivityDiagramGeneratorServiceImpl`, `CollaborationDiagramGeneratorServiceImpl`, `ComponentDiagramGeneratorServiceImpl`, `DeploymentDiagramGeneratorServiceImpl`, `ObjectDiagramGeneratorServiceImpl`, `StateDiagramGeneratorServiceImpl` (all six `*DiagramGeneratorServiceImpl` beans in the root `service/` package that are not injected anywhere).
4. **Remove `DIAGRAM_TYPE_ALIASES` silent remapping**: Replace the `ARCHITECTURE → COMPONENT` and `C4_CONTEXT → COMPONENT` aliases in `ConfidenceDiagramServiceImpl` with proper handling or explicit error messages.
5. **Add missing `StyleProfile` entries**: Implement the five missing style profiles (`ACTIVITY`, `STATE`, `OBJECT`, `MICROSERVICES`, `COLLABORATION`) in `StyleProfileServiceImpl`.
6. **Fix `SemanticExtractionServiceImpl` AI prompt**: The `extractWithAi()` method must pass the actual `DiagramType` to the prompt builder and return type-appropriate field sets.

### Phase 2 — Enum and Entity Consolidation (1 week)
**Goal**: Single source of truth for types and persistence.

1. **Merge enums**: Create `model.DiagramType` as the canonical enum. Deprecate and then remove `domain.DiagramType` and `enums.DiagramType`. Update all references.
2. **Merge entities**: Create a single `Diagram` entity in `model` with all fields from both `domain.Diagram` and `entity.Diagram`. Write a Flyway migration to merge `domain_diagrams` into `diagrams`.
3. **Merge repositories**: Single `DiagramRepository` operating on the unified entity. Remove `DomainDiagramRepository`.
4. **Merge evaluation entities** similarly.

### Phase 3 — Classifier Consolidation (1 week)
**Goal**: Single classification source of truth.

1. **Delete duplicate constants from `DiagramSuggestionServiceImpl`**: Remove `EXPLICIT_TYPE_PATTERNS`, `SEMANTIC_CATEGORIES`, and `KEYWORD_SETS` from the suggestion service. The service must delegate all classification work to `DiagramClassificationServiceImpl`.
2. **Extend `DiagramClassificationServiceImpl`** to cover the 5 missing diagram types in the suggestion path.
3. **Merge `DiagramSuggestionServiceImpl` into `ConfidenceDiagramServiceImpl`**: The suggestion service is a thin wrapper; its logic belongs in the orchestrator's MEDIUM-confidence branch.

### Phase 4 — Generator Extraction (2–3 weeks)
**Goal**: Break apart `PlantUmlGenerationServiceImpl` into one class per diagram type.

1. **Create `PlantUmlDiagramGenerator` interface** (see Section 6).
2. **Extract each inline method** from `PlantUmlGenerationServiceImpl` into its own `@Component` class:
   - `generateClassDiagram()` → `ClassPlantUmlGenerator`
   - `generateErDiagram()` → `ErPlantUmlGenerator`
   - `generateSequenceDiagram()` → `SequencePlantUmlGenerator`
   - `generateUseCaseDiagram()` → `UseCasePlantUmlGenerator`
   - `generateComponentDiagram()` → `ComponentPlantUmlGenerator`
   - `generateDeploymentDiagram()` → `DeploymentPlantUmlGenerator`
   - `generateActivityDiagram()` → `ActivityPlantUmlGenerator`
   - `generateStateDiagram()` → `StatePlantUmlGenerator`
   - `generateObjectDiagram()` → `ObjectPlantUmlGenerator`
   - `generateMicroservicesDiagram()` → `MicroservicesPlantUmlGenerator`
   - `generateCollaborationDiagram()` → `CollaborationPlantUmlGenerator`
3. **Extract `LayoutProfile` generation** from `PlantUmlGenerationServiceImpl` into a dedicated `LayoutProfileFactory` bean.
4. **Replace the switch statement** in `PlantUmlGenerationServiceImpl` with a `PlantUmlGeneratorRegistry` that dispatches by type.
5. **Delete the now-empty `PlantUmlGenerationServiceImpl`** and wire the registry directly into `ConfidenceDiagramServiceImpl`.

### Phase 5 — AI Provider Hardening (1 week)
**Goal**: Reliable, observable, provider-agnostic AI calls.

1. **Implement `AiProvider` abstraction** (see Section 7).
2. **Add retry with exponential backoff** for 429/503 responses (use Resilience4j or Spring Retry).
3. **Expose provider status** in `GET /api/v2/health` — which provider is active, last failure, fallback state.
4. **Make `NlpFallback` a named, configurable bean** rather than ad-hoc fallback code inside service methods.

### Phase 6 — Async Rendering and Caching (1–2 weeks)
**Goal**: Non-blocking rendering, no redundant work.

1. **Move rendering off the request thread** using `@Async` or a dedicated `ExecutorService` with bounded queue.
2. **Add Redis (or Caffeine for single-node) caching** keyed on `SHA-256(inputText + diagramType + seed)`.
3. **Add cache invalidation endpoint** for development/testing.

### Phase 7 — Frontend Contract Formalization (1 week)
**Goal**: Typed, versioned API contract.

1. **Add OpenAPI 3 schema** generated from `@Schema` annotations on all DTOs.
2. **Generate TypeScript client** from the schema using `openapi-generator-cli`.
3. **Migrate `index.html`** to use the generated client, eliminating hard-coded field name strings.

---

## 4. Recommended Package Structure

```
com.example.aidiagramgenerator
│
├── api                              # HTTP layer only — no business logic
│   ├── v1                          # Legacy endpoints (deprecated)
│   │   ├── DiagramController
│   │   └── PlantUmlDiagramController
│   └── v2                          # New unified API
│       ├── DiagramV2Controller
│       ├── request
│       │   ├── GenerateDiagramRequest
│       │   └── SuggestDiagramRequest
│       └── response
│           ├── DiagramResponse
│           ├── SuggestionResponse
│           └── GenerationStatusResponse
│
├── model                           # Canonical domain types (no JPA, no Spring)
│   ├── DiagramType                 # Single canonical enum (replaces both)
│   ├── Diagram                     # Domain object (not JPA entity)
│   ├── SemanticModel
│   ├── StyleProfile
│   ├── LayoutProfile
│   ├── EntityNode
│   ├── Relationship
│   └── ClassificationResult
│
├── persistence                     # JPA layer — isolated from domain
│   ├── entity
│   │   ├── DiagramEntity
│   │   └── DiagramEvaluationEntity
│   ├── repository
│   │   ├── DiagramRepository
│   │   └── DiagramEvaluationRepository
│   └── mapper
│       └── DiagramMapper           # domain.Diagram ↔ entity.DiagramEntity
│
├── ai                              # AI provider abstraction
│   ├── AiProvider                  # Interface
│   ├── AiProviderChain             # Selects + retries
│   ├── openai
│   │   └── OpenAiProvider
│   ├── ollama
│   │   └── OllamaProvider
│   └── nlp
│       ├── NlpFallbackProvider
│       └── StanfordNlpPipeline     # Singleton pipeline wrapper
│
├── classification                  # Diagram type classification
│   ├── DiagramClassifier           # Interface
│   ├── DiagramClassifierImpl       # 5-layer cascade (single source of truth)
│   └── rules                       # Term sets as static constants
│       └── ClassificationRules
│
├── extraction                      # Semantic extraction (type-aware)
│   ├── SemanticExtractor           # Interface
│   └── SemanticExtractorImpl
│
├── generation                      # PlantUML generation (plugin architecture)
│   ├── PlantUmlGenerator           # Interface (the plugin contract)
│   ├── PlantUmlGeneratorRegistry   # Auto-discovers all plugins
│   ├── LayoutProfileFactory        # Extracted from PlantUmlGenerationServiceImpl
│   └── plugin                      # One class per diagram type
│       ├── ClassPlantUmlGenerator
│       ├── ErPlantUmlGenerator
│       ├── SequencePlantUmlGenerator
│       ├── UseCasePlantUmlGenerator
│       ├── ComponentPlantUmlGenerator
│       ├── DeploymentPlantUmlGenerator
│       ├── ActivityPlantUmlGenerator
│       ├── StatePlantUmlGenerator
│       ├── ObjectPlantUmlGenerator
│       ├── MicroservicesPlantUmlGenerator
│       ├── CollaborationPlantUmlGenerator
│       ├── ArchitecturePlantUmlGenerator
│       └── C4ContextPlantUmlGenerator
│
├── style                           # Style profiles
│   ├── StyleProfileService         # Interface
│   └── StyleProfileServiceImpl     # Full coverage for all 13 types
│
├── rendering                       # Async rendering
│   ├── DiagramRenderer             # Interface
│   ├── PlantUmlRenderer            # SourceStringReader wrapper
│   ├── RenderJobService            # Job tracking for async renders
│   └── RenderExecutorConfig        # Thread pool configuration
│
├── orchestration                   # Top-level pipeline orchestration
│   ├── DiagramOrchestrationService # Interface (replaces ConfidenceDiagramService)
│   └── DiagramOrchestrationServiceImpl
│
├── validation                      # Centralized input validation
│   ├── DiagramRequestValidator
│   ├── InputSanitizer              # Prompt injection prevention
│   └── ValidationResult
│
├── cache                           # Caching layer
│   ├── DiagramCacheService
│   └── CacheKeyGenerator
│
├── exception                       # Exception hierarchy
│   ├── DiagramException            # Base
│   ├── ClassificationException
│   ├── GenerationException
│   ├── RenderingException
│   ├── AiProviderException
│   └── GlobalExceptionHandler
│
└── config                          # Spring configuration
    ├── AiProviderConfig
    ├── AsyncConfig
    ├── CacheConfig
    └── OpenApiConfig
```

---

## 5. Recommended Service Hierarchy

```
DiagramOrchestrationService
│  Owns the full pipeline. No business logic of its own.
│  Coordinates: validate → classify → extract → style → generate → render → persist
│
├── InputValidationPipeline
│     Runs all validators in sequence; fails fast on first violation.
│
├── DiagramClassifier
│     5-layer cascade. Owns ALL classification rules.
│     Produces: ClassificationResult(type, confidence, source)
│
├── SemanticExtractor
│     Delegates to AiProviderChain for AI-based extraction.
│     Falls back to type-specific NLP extraction.
│     Produces: SemanticModel (type-aware field population)
│
├── StyleProfileService
│     Returns StyleProfile for any DiagramType (all 13 covered).
│     Profiles loaded from config file, overridable per tenant.
│
├── PlantUmlGeneratorRegistry
│     Dispatches to the correct PlantUmlGenerator plugin.
│     Produces: raw PlantUML string
│
├── DiagramRenderer
│     Accepts PlantUML string + format (PNG/SVG).
│     Returns CompletableFuture<byte[]>.
│     Runs on dedicated thread pool.
│
└── DiagramRepository
      Saves/loads unified Diagram domain objects.
      Internally maps to DiagramEntity.
```

**Confidence Decision Points** (remain in `DiagramOrchestrationServiceImpl`):

```
confidence ≥ highThreshold  →  run full pipeline → return DiagramResponse
confidence ≥ medThreshold   →  return SuggestionResponse (type + explanation)
confidence < medThreshold   →  return rejection with guidance
explicit type provided      →  skip classification, confidence = 100
```

---

## 6. UML Generator Abstraction Design

### 6.1 Core Interface

```java
// com.example.aidiagramgenerator.generation.PlantUmlGenerator
public interface PlantUmlGenerator {

    /** Returns the diagram type this generator handles. */
    DiagramType supports();

    /**
     * Generate PlantUML source code from a semantic model.
     *
     * @param model   type-aware semantic model populated by SemanticExtractor
     * @param style   style profile for this diagram type
     * @param layout  layout profile (direction, spacing, arrow style)
     * @return        valid PlantUML source beginning with @startuml
     */
    String generate(SemanticModel model, StyleProfile style, LayoutProfile layout);
}
```

### 6.2 Registry (replaces the switch statement)

```java
// com.example.aidiagramgenerator.generation.PlantUmlGeneratorRegistry
@Component
public class PlantUmlGeneratorRegistry {

    private final Map<DiagramType, PlantUmlGenerator> generators;

    public PlantUmlGeneratorRegistry(List<PlantUmlGenerator> allGenerators) {
        this.generators = allGenerators.stream()
            .collect(Collectors.toUnmodifiableMap(
                PlantUmlGenerator::supports,
                Function.identity(),
                (a, b) -> { throw new IllegalStateException(
                    "Duplicate generator for " + a.supports()); }
            ));
        allGenerators.forEach(g ->
            log.info("Registered PlantUmlGenerator: {} → {}",
                g.supports(), g.getClass().getSimpleName()));
    }

    public String generate(DiagramType type, SemanticModel model,
                           StyleProfile style, LayoutProfile layout) {
        PlantUmlGenerator generator = generators.get(type);
        if (generator == null) {
            throw new GenerationException("No PlantUML generator registered for type: " + type);
        }
        return generator.generate(model, style, layout);
    }

    public Set<DiagramType> supportedTypes() {
        return generators.keySet();
    }
}
```

### 6.3 Adding a New Diagram Type (Zero-Change Extension)

To add, say, a **Gantt** diagram type:

1. Add `GANTT` to `model.DiagramType`.
2. Create `GanttPlantUmlGenerator implements PlantUmlGenerator` in `generation/plugin/`.
3. Annotate it with `@Component`.
4. Add a `StyleProfile` entry in `StyleProfileServiceImpl`.
5. Add classification keywords in `ClassificationRules`.

**No other file needs to change.** The registry auto-discovers the new bean. The classifier auto-uses the new keywords. The orchestrator routes to the new generator transparently.

### 6.4 Abstract Base Generator (Optional)

Common PlantUML boilerplate (header, footer, skin params, layout directives) should live in a base class that all generators extend:

```java
public abstract class AbstractPlantUmlGenerator implements PlantUmlGenerator {

    protected String wrapDiagram(String body, StyleProfile style, LayoutProfile layout) {
        return "@startuml\n"
            + buildSkinParams(style) + "\n"
            + buildDirectionDirective(layout) + "\n"
            + body + "\n"
            + "@enduml";
    }

    protected String buildSkinParams(StyleProfile style) { /* ... */ }

    protected String buildDirectionDirective(LayoutProfile layout) {
        return switch (layout.getDirection()) {
            case LEFT_TO_RIGHT  -> "left to right direction";
            case TOP_TO_BOTTOM  -> "top to bottom direction";
        };
    }
}
```

This eliminates duplicated header/footer code across all 11 (or more) generator classes. The `LayoutProfile.Direction` enum is the single authoritative source — no hardcoded strings.

---

## 7. Recommended Interfaces and Patterns

### 7.1 Strategy Pattern — AI Provider Selection

```java
// com.example.aidiagramgenerator.ai.AiProvider
public interface AiProvider {
    String classify(String text);
    SemanticModel extractSemantics(String text, DiagramType type);
    String generateDiagramCode(SemanticModel semantics, DiagramType type);
    boolean isAvailable();
    String providerName();
}

// AiProviderChain tries each in order
@Component
public class AiProviderChain {
    private final List<AiProvider> providers; // ordered: OpenAI, Ollama, NLP

    public <T> T execute(Function<AiProvider, T> operation, T fallback) {
        for (AiProvider provider : providers) {
            if (provider.isAvailable()) {
                try {
                    return operation.apply(provider);
                } catch (AiProviderException e) {
                    log.warn("Provider {} failed: {}, trying next", provider.providerName(), e.getMessage());
                }
            }
        }
        return fallback;
    }
}
```

### 7.2 Chain of Responsibility — Validation Pipeline

```java
public interface ValidationStep {
    ValidationResult validate(DiagramRequest request, ValidationContext ctx);
}

@Component
public class InputValidationPipeline {
    private final List<ValidationStep> steps; // Spring-injected, ordered by @Order

    public void validate(DiagramRequest request) {
        ValidationContext ctx = new ValidationContext();
        for (ValidationStep step : steps) {
            ValidationResult result = step.validate(request, ctx);
            if (!result.isValid()) {
                throw new InvalidDiagramRequestException(result.getMessage());
            }
        }
    }
}

// Individual steps:
@Component @Order(1) class BlankTextValidator     implements ValidationStep { ... }
@Component @Order(2) class TextLengthValidator    implements ValidationStep { ... }
@Component @Order(3) class InputSanitizerStep     implements ValidationStep { ... }
@Component @Order(4) class DiagramTypeValidator   implements ValidationStep { ... }
```

### 7.3 Factory Method — Layout Profile Creation

Extract `generateRandomLayoutProfile()` from `PlantUmlGenerationServiceImpl` into a standalone factory:

```java
@Component
public class LayoutProfileFactory {

    public LayoutProfile random() {
        return create(new Random());
    }

    public LayoutProfile seeded(long seed) {
        return create(new Random(seed));
    }

    public LayoutProfile fromStyleProfile(StyleProfile style) {
        // Use style.getLayoutDirection() as the base, randomize the rest
        return create(new Random(), style.getLayoutDirection());
    }

    private LayoutProfile create(Random rng, LayoutProfile.Direction direction) { ... }
}
```

### 7.4 Template Method — Diagram Type Plugins

Each `PlantUmlGenerator` implementation follows the same generation sequence, which the abstract base class enforces:

```
generate()
  ├── 1. buildHeader(style, layout)   [in AbstractPlantUmlGenerator]
  ├── 2. buildBody(model, layout)     [abstract — implemented per type]
  └── 3. buildFooter()                [in AbstractPlantUmlGenerator]
```

### 7.5 Observer Pattern — Generation Events

Publish domain events during the pipeline for analytics, evaluation, and monitoring without coupling these concerns into the orchestrator:

```java
// Events
DiagramClassifiedEvent(type, confidence, source, durationMs)
DiagramGeneratedEvent(diagramId, type, plantUmlLength, durationMs)
DiagramRenderedEvent(diagramId, format, outputSizeBytes, durationMs)
AiProviderFailedEvent(providerName, errorCode, retryCount)

// Listeners (separate classes, async)
@Component class ClassificationMetricsListener ...
@Component class DiagramAnalyticsListener      ...
@Component class AiProviderHealthListener      ...
```

This replaces the direct calls to `ClassificationMetricsServiceImpl` and `DiagramAnalyticsServiceImpl` that are currently embedded in `ConfidenceDiagramServiceImpl`.

### 7.6 Decorator Pattern — Cached Generation

```java
@Primary
@Component
public class CachingDiagramOrchestrationService implements DiagramOrchestrationService {

    private final DiagramOrchestrationServiceImpl delegate;
    private final DiagramCacheService cache;

    @Override
    public GenerationResult generate(GenerateDiagramRequest request) {
        String cacheKey = cache.keyFor(request);
        return cache.get(cacheKey)
            .orElseGet(() -> {
                GenerationResult result = delegate.generate(request);
                cache.put(cacheKey, result);
                return result;
            });
    }
}
```

---

## 8. Scalability Recommendations

### 8.1 Stateless Service Layer

All services in the proposed architecture should be stateless (no instance fields that change between requests). `PlantUmlGenerationServiceImpl` is currently stateless, but `ConfidenceDiagramServiceImpl` holds mutable threshold values via `@Value` fields — these should be encapsulated in an injected `GenerationConfig` bean.

### 8.2 Async Rendering with Back-Pressure

PlantUML rendering is CPU-bound. Under load, synchronous rendering starves the Tomcat thread pool. Recommended approach:

```java
@Configuration
public class AsyncConfig {
    @Bean("renderExecutor")
    public Executor renderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);          // bounded queue = back-pressure
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("render-");
        return executor;
    }
}
```

`CallerRunsPolicy` as the rejection handler means the HTTP thread renders directly when the queue is full, degrading gracefully rather than failing.

### 8.3 Horizontal Scaling Considerations

The current architecture stores rendered PNG bytes in the `domain_diagrams.diagram_png` column (BYTEA in PostgreSQL). For horizontal scaling:

1. Move binary storage to an object store (S3, Azure Blob, or MinIO).
2. Store only the object key in the DB column.
3. Serve diagrams via `GET /api/v2/diagrams/{id}/png` which redirects to a pre-signed URL.

This eliminates large BYTEA reads from the DB on every diagram view.

### 8.4 Classification Cache

The classifier runs a full 5-layer cascade including potential AI calls. Classification results for the same input text should be cached with a short TTL (5–15 minutes) since the same user description is often resubmitted with minor tweaks. Keying on `SHA-256(lowercase(inputText))` is sufficient.

### 8.5 NLP Pipeline Singleton

`SemanticExtractionServiceImpl` loads the Stanford CoreNLP pipeline on first use (3.5 second load time). The pipeline is already a `@Service` singleton, but multiple concurrent requests during the first warmup period may trigger multiple loads. Wrap initialization in `synchronized` with a double-checked lock, or use `@PostConstruct` to eagerly initialize.

### 8.6 Database

1. **Add indexes**: `diagram_type`, `created_at`, and `input_text_hash` (for deduplication queries).
2. **Partition `domain_diagrams` by `diagram_type`** if volume per type grows large.
3. **Archive old diagrams**: Add a `status` column (`ACTIVE`, `ARCHIVED`) and a TTL-based archiving job.

---

## 9. Performance Recommendations

### 9.1 Content-Addressable Diagram Cache

Before entering the generation pipeline, compute:

```java
String cacheKey = DigestUtils.sha256Hex(
    request.getText().toLowerCase().trim() + "|" +
    request.getDiagramType() + "|" +
    request.getSeed()
);
```

Cache hit rate for repeated identical requests will be near 100%, eliminating AI calls and rendering entirely.

Use **Caffeine** for single-node deployments:

```java
@Bean
public Cache<String, GenerationResult> diagramCache() {
    return Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .recordStats()
        .build();
}
```

Use **Redis** for multi-node deployments with Spring Cache abstraction (`@Cacheable("diagrams")`).

### 9.2 PlantUML Rendering Optimization

- Use PlantUML's `FileFormat.SVG` output by default. SVG is generated faster than PNG (no pixel rasterization) and is resolution-independent.
- Reuse the `SourceStringReader` factory rather than constructing a new one per request — `SourceStringReader` is thread-safe.
- For PNG, set the DPI and image size limits in PlantUML configuration to prevent runaway allocations from pathologically large input.

### 9.3 AI Call Batching (Future)

If multiple diagram suggestions are generated in one session, batch the classification calls into a single AI request with multiple inputs rather than N sequential API calls. OpenAI's Chat Completions API supports multiple system-user pairs in a single request.

### 9.4 Lazy CoreNLP Loading

The Stanford CoreNLP annotator pipeline (`tokenize,ssplit,pos,lemma,ner,depparse`) loads ~500 MB of model data. Use a lazy singleton with `@Lazy` on the `NlpFallbackProvider` bean so it only loads when the AI provider is unavailable, not at application startup when the AI provider may be healthy.

### 9.5 Database Query Optimization

`ConfidenceDiagramServiceImpl.generateFromTemplate()` fetches diagrams from the DB to check if templates need to be rebuilt. Ensure this uses a projection query (`SELECT id, type, updated_at`) rather than loading full `BYTEA` columns unnecessarily.

---

## 10. Maintainability Recommendations

### 10.1 Error Handling — Exception Hierarchy

Define a clear exception hierarchy. All diagram exceptions should inherit from a single base:

```
DiagramException (base, unchecked)
├── ValidationException        — user input problems (→ 400)
├── ClassificationException    — cannot determine diagram type (→ 422)
├── GenerationException        — PlantUML generation failed (→ 500)
├── RenderingException         — PlantUML rendering failed (→ 500)
├── AiProviderException        — AI call failed (→ 502 or 503)
│   ├── AiQuotaExceededException (→ 503 with Retry-After header)
│   └── AiTimeoutException       (→ 504)
└── DiagramNotFoundException   — diagram ID not found (→ 404)
```

`GlobalExceptionHandler` maps each to the correct HTTP status. No exception should fall through to the generic 500 handler.

Also add the missing handler:
```java
@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<ErrorResponse> handleMethodNotAllowed(...) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .header(HttpHeaders.ALLOW, ex.getSupportedHttpMethods().stream()
            .map(Enum::name).collect(joining(", ")))
        .body(new ErrorResponse("METHOD_NOT_ALLOWED", ex.getMessage()));
}
```

### 10.2 Frontend–Backend Contract Formalization

Replace the implicit field-name dependency in `index.html` with a versioned, documented contract:

1. Annotate all DTOs with `@Schema` (Springdoc):
   ```java
   @Schema(description = "Result of a diagram generation request")
   public record GenerationResult(
       @Schema(description = "Decision: GENERATE, SUGGEST, or REJECT")
       String decision,
       @Schema(description = "Base64-encoded PNG, present only when decision=GENERATE")
       String diagramBase64,
       ...
   ) {}
   ```
2. Generate OpenAPI spec at build time with `springdoc-openapi-maven-plugin`.
3. Generate TypeScript types with `openapi-typescript`.
4. Import the generated types in the SPA.

### 10.3 Configuration Externalization

The following values are currently hardcoded in service implementations and should be externalized to `application.properties` with documented defaults:

| Current location | Property key | Default |
|---|---|---|
| `ConfidenceDiagramServiceImpl` | `diagram.confidence.high-threshold` | `70` |
| `ConfidenceDiagramServiceImpl` | `diagram.confidence.medium-threshold` | `40` |
| `DiagramRenderingServiceImpl` | `diagram.render.max-input-size-bytes` | `10485760` |
| `AsyncConfig` | `diagram.render.thread-pool.core-size` | `4` |
| `AsyncConfig` | `diagram.render.thread-pool.max-size` | `16` |
| `OpenAiService` | `openai.timeout-seconds` | `30` |
| `CachingService` | `diagram.cache.ttl-minutes` | `30` |

### 10.4 Testing Strategy

**Current coverage gaps** (based on file structure):

- `PlantUmlGenerationServiceImpl` is currently untestable in isolation because it has no injected dependencies and its generator methods are private — they can only be tested through the public `generate()` method.
- `ConfidenceDiagramServiceImpl` has no unit tests for the confidence decision tree branches.
- The 13 `DiagramGenerator` Mermaid plugins have no tests in `src/test`.

**Recommended test structure after refactoring**:

```
src/test/java/.../
├── generation/plugin/
│   ├── ClassPlantUmlGeneratorTest    — unit tests per generator
│   ├── SequencePlantUmlGeneratorTest
│   └── ...
├── classification/
│   └── DiagramClassifierTest         — tests all 5 cascade layers
├── extraction/
│   └── SemanticExtractorTest         — tests type-aware extraction
├── orchestration/
│   └── DiagramOrchestrationServiceTest — integration test with mocked AI
├── ai/
│   ├── OpenAiProviderTest            — tests with WireMock
│   └── AiProviderChainTest           — tests fallback cascade
└── api/
    └── DiagramV2ControllerTest        — MockMvc tests for all endpoints
```

**Each `PlantUmlGenerator` plugin** should have:
- A test that verifies `@startuml` / `@enduml` bookends are present
- Tests for minimum expected output for a minimal `SemanticModel`
- Tests for edge cases (empty entity list, no relationships, etc.)

### 10.5 Logging Normalization

`PlantUmlGenerationServiceImpl` currently logs at `INFO` level things that should be `DEBUG` (full PlantUML output, entity name lists, relationship summaries). Production logs should contain:

| Level | What |
|---|---|
| `ERROR` | Generation failures, rendering failures, uncaught exceptions |
| `WARN` | AI provider failures, fallbacks activated, missing style profiles |
| `INFO` | Request received/completed, pipeline decision (GENERATE/SUGGEST/REJECT), provider used |
| `DEBUG` | Classification scores, semantic model contents, PlantUML source |

Introduce a `request-id` MDC key set by a `OncePerRequestFilter` so all log lines for a single request share a correlation ID.

### 10.6 API Versioning

Introduce URL-path versioning immediately before the pipeline refactor:

- Legacy endpoints remain at `/api/diagram/*` and `/api/diagrams/*` (no breaking change)
- New unified endpoints at `/api/v2/diagrams/*`
- Deprecation notice in `ONBOARDING.md` and OpenAPI descriptions
- Legacy endpoints removed after one minor version cycle

### 10.7 Dependency Injection Hygiene

After the refactor, each class should declare all dependencies via constructor injection (Lombok `@RequiredArgsConstructor` or explicit constructor). No `@Autowired` field injection. This makes dependencies visible in unit tests without Spring context.

---

## Summary Table — Problems and Fixes

| # | Problem | File(s) | Fix | Phase |
|---|---|---|---|---|
| 1 | Dual pipeline (PlantUML + Mermaid) | `PlantUmlDiagramController`, `DiagramController` | Unify under `/api/v2` | Phase 3+ |
| 2 | God class with duplicate switch | `PlantUmlGenerationServiceImpl` | Extract to 11 plugin classes | Phase 4 |
| 3 | Dead service beans (6 classes) | `*DiagramGeneratorServiceImpl` (6 files) | Delete | Phase 1 |
| 4 | Unused `DiagramGeneratorRegistry` | `DiagramGeneratorRegistry` | Migrate Mermaid to unified architecture | Phase 3 |
| 5 | Dual `DiagramType` enums | `domain.DiagramType`, `enums.DiagramType` | Merge to `model.DiagramType` | Phase 2 |
| 6 | Dual entity/repo/table | `domain.Diagram`, `entity.Diagram` | Merge with Flyway migration | Phase 2 |
| 7 | Duplicate classification constants | `DiagramSuggestionServiceImpl` | Delegate to `DiagramClassifierImpl` | Phase 3 |
| 8 | Wrong AI prompt for non-CLASS types | `SemanticExtractionServiceImpl` | Type-aware prompt builder | Phase 1 |
| 9 | 5 missing style profiles | `StyleProfileServiceImpl` | Add all 5 profiles | Phase 1 |
| 10 | `LayoutProfile.Direction` string mismatch | `PlantUmlGenerationServiceImpl` | Use enum constant, not string | Phase 1 |
| 11 | Silent `ARCHITECTURE`→`COMPONENT` alias | `ConfidenceDiagramServiceImpl` | Remove alias, add proper generators | Phase 1+4 |
| 12 | 500 on wrong HTTP method | `GlobalExceptionHandler` | Add 405 handler | Phase 1 |
| 13 | No caching | All generation paths | Caffeine/Redis content-addressable cache | Phase 6 |
| 14 | Synchronous rendering on request thread | `DiagramRenderingServiceImpl` | Async with dedicated thread pool | Phase 6 |
| 15 | Fragile AI integration (no retry/fallback) | `OpenAiService`, `OllamaService` | `AiProviderChain` + Resilience4j | Phase 5 |
| 16 | Implicit frontend-backend contract | `index.html`, all response DTOs | OpenAPI + generated TypeScript types | Phase 7 |
| 17 | Scattered input validation | `ConfidenceDiagramServiceImpl`, DTOs | `InputValidationPipeline` | Phase 3 |
| 18 | Classification only covers 6 types in suggestion path | `DiagramSuggestionServiceImpl` | Extend to all 13 types | Phase 3 |
| 19 | NLP pipeline cold-start on hot path | `SemanticExtractionServiceImpl` | Eager init `@PostConstruct` or `@Lazy` | Phase 1 |
| 20 | Hardcoded config values in services | `ConfidenceDiagramServiceImpl`, others | Externalize to `application.properties` | Phase 1 |
