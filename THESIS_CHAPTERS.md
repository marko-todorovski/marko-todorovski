# AI Diagram Generator — Academic Thesis Chapters
### Capstone Report — Chapter 4, 5, 6, Limitations, and Future Work

> **Note:** This document contains academic-style prose suitable for direct insertion into a university capstone report. All technical references correspond to actual source files and class names in this repository.

---

## Chapter 4: System Architecture

### 4.1 Overview

The AI Diagram Generator is a full-stack web application that accepts natural language descriptions, uploaded documents, or structured text and transforms them into software architecture diagrams. The system supports eleven distinct diagram types — including class, sequence, entity-relationship, use case, activity, state, component, deployment, object, collaboration, and microservices diagrams — all rendered as portable PNG or SVG images using the PlantUML rendering engine. The architecture follows a monolithic deployment model in which a single Spring Boot 4.0.2 process simultaneously serves the REST API and hosts the React 18 single-page application, simplifying deployment whilst concentrating all runtime concerns within one JVM process.

The technology stack was assembled to balance capability with operational simplicity. Java 24.0.1 provides the runtime environment; Apache Maven manages the build lifecycle and dependency resolution. For diagram intelligence, the system integrates with OpenAI's GPT-4o and GPT-4o-mini models as the primary AI layer, and employs Stanford CoreNLP 4.5.7 as a locally-executing natural language processing fallback. PlantUML 1.2024.3 serves as the diagram rendering engine, translating textual diagram specifications into binary image formats. Persistent storage is provided by PostgreSQL 18.3, accessed through the Hibernate 7.2.1 ORM layer managed by Spring Data JPA. The frontend is a React 18 single-page application delivered as a single static HTML file that is transpiled in-browser at runtime using Babel Standalone, requiring no frontend build step.

### 4.2 Architectural Tiers

The system is organized into three logical tiers that reflect the responsibilities of each layer: the client tier, the application tier, and the data tier.

The client tier consists of the React 18 single-page application served from `src/main/resources/static/index.html`. The application communicates with the server through HTTP REST calls to port 8080 and renders received PlantUML diagram codes alongside download controls for PNG and SVG outputs. Because the frontend is transpiled in-browser via CDN-hosted Babel, no separate frontend build server is required; the Spring Boot process serves both API requests and static assets from the same port.

The application tier is a Spring Boot monolith comprising six REST controllers, approximately forty-three service classes, a multi-stage AI abstraction layer, and the PlantUML rendering engine. The REST layer receives and validates incoming requests before delegating to the service layer, which orchestrates the classification, extraction, generation, and rendering pipeline. The AI layer provides an abstraction over the OpenAI HTTP API and the locally-running Stanford CoreNLP pipeline, enabling the application to remain operational when external AI services are unavailable. PlantUML rendering is performed synchronously within this tier using the `net.sourceforge.plantuml.SourceStringReader` class.

The data tier consists of a PostgreSQL 18.3 instance holding two relational tables that reflect the coexistence of two parallel diagram generation pipelines, discussed in detail in Section 4.3. Database connectivity is managed through HikariCP connection pooling, and schema management is performed automatically by Hibernate at application startup using the `ddl-auto=update` strategy.

### 4.3 The Two-Pipeline Architecture

One of the most architecturally significant aspects of the system is the simultaneous existence of two independent diagram generation pipelines, each with its own controller, service hierarchy, database table, and entity model. This duality arose incrementally: the legacy Mermaid pipeline was developed first and retained for backward compatibility, while the PlantUML pipeline was introduced as the primary path with enhanced confidence modelling and image rendering capabilities.

The primary pipeline accepts requests at `POST /api/diagram/generate` and is handled by `PlantUmlDiagramController`. Its orchestrator, `ConfidenceDiagramServiceImpl`, coordinates a multi-stage pipeline that classifies the input, evaluates confidence, extracts semantic information, generates PlantUML code, renders the result to PNG or SVG, and persists the diagram to the `domain_diagrams` table. The pipeline returns a response carrying the diagram UUID, the PlantUML source code, confidence score, and image URL. This is the path actively developed and used by the frontend.

The legacy pipeline accepts requests at `POST /api/diagrams/from-text` and is handled by `DiagramController`. It produces Mermaid syntax rather than rendered images, uses a strategy-based generator registry (`DiagramGeneratorRegistry`) that auto-discovers thirteen `DiagramGenerator` implementations, and persists to the `diagrams` table. Additional specialised entry points in this pipeline process XML (`/from-xml`), URLs (`/from-url`), and uploaded PDF files (`/from-pdf`), with PDF text extraction handled by Apache PDFBox 3.0.3. The legacy pipeline remains functional for these specialised input modes and for backward API compatibility.

The two pipelines use incompatible type systems: the primary pipeline operates on the `domain.DiagramType` enumeration (eleven values), while the legacy pipeline operates on the `enums.DiagramType` enumeration (thirteen values, adding `ARCHITECTURE` and `C4_CONTEXT`). This incompatibility means that every boundary crossing between the two pipelines requires an explicit type mapping step, and the strategy-based `DiagramGeneratorRegistry` of the legacy pipeline cannot be reused by the primary pipeline without conversion.

### 4.4 The Three-Tier Confidence Model

A central design feature of the primary pipeline is the three-tier confidence model implemented in `ConfidenceDiagramServiceImpl`. Rather than generating a diagram unconditionally upon every request, the system evaluates whether the request is sufficiently well-understood before committing to generation. This evaluation proceeds through four steps.

First, the `DiagramClassificationServiceImpl` determines the most appropriate diagram type through a five-layer cascade: explicit regular-expression matching against twenty-eight compiled patterns; a structured JSON query to the OpenAI API requesting a classification with reasoning; semantic category pattern matching against per-type keyword sets; a broad keyword scoring pass across eleven diagram-type-specific term sets; and, as a final fallback, a plain-text AI query whose response is parsed for type keywords. This cascade allows the classifier to produce a reasoned result even when the OpenAI API is unavailable, falling through to progressively simpler heuristic methods.

Second, `DiagramSuggestionServiceImpl` derives a confidence score for the classified type by delegating back to the classification service and evaluating the strength of evidence found. The score is expressed as an integer percentage.

Third, the confidence score is evaluated against two thresholds. A score at or above seventy percent places the request in the HIGH tier, triggering immediate diagram generation. A score between forty and sixty-nine percent places the request in the MEDIUM tier, causing the system to return a suggestion response that presents the proposed diagram type to the client and requests explicit confirmation. A score below forty percent places the request in the LOW tier, resulting in a rejection response with an explanatory message. These threshold boundaries are expressed as constants within `ConfidenceDiagramServiceImpl` and can be adjusted without changes to the surrounding logic.

Fourth, when the request body includes `forceGenerate: true`, the confidence gate is bypassed entirely, and generation proceeds regardless of the computed score. This mechanism is used by the frontend when the user responds to a MEDIUM-tier suggestion by clicking "Proceed Anyway." When the user explicitly selects a diagram type from the dropdown rather than using auto-detection, the user-specified type overrides the classifier's suggestion, though the confidence score is still computed for informational purposes.

### 4.5 The AI and NLP Fallback Strategy

The system employs a layered fallback strategy to ensure resilience against AI service unavailability. The `AiModelService` interface defines the contract for AI provider interactions, specifying `callLLM(prompt)` for plain-text generation and `classify(text)` for structured classification. `AiProviderConfig` reads the `ai.provider` configuration property at startup and registers either `OpenAiService` or `OllamaService` as the primary Spring bean implementing this interface.

`OpenAiService` maintains two configured model names: `gpt-4o-mini` for classification tasks, where speed and token economy are prioritised, and `gpt-4o` for full diagram generation, where reasoning quality is paramount. Communication with the OpenAI API is performed through Spring WebFlux's non-blocking `WebClient`. The `LlmResult` wrapper class encapsulates the AI response, exposing `getContent()` for the response text and `isSuccess()` to indicate whether the call completed successfully or encountered an error such as an HTTP 429 quota-exceeded response.

When `isSuccess()` returns false, `SemanticExtractionServiceImpl` activates the Stanford CoreNLP fallback. This pipeline executes five processing stages in sequence: tokenization, part-of-speech tagging, lemmatization, named entity recognition, and dependency parsing. The NLP pipeline extracts entities using CAPITALIZED and PascalCase word patterns, identifies relationships through a set of thirty or more relationship keyword patterns and thirteen multiplicity pattern expressions, and detects action verbs for use in sequence and activity diagrams. The resulting `SemanticModel` — containing arrays of entities, relationships, and actions — is passed to the generation layer regardless of whether it was produced by AI or NLP. The Stanford CoreNLP model files are loaded once at application startup, consuming approximately 3.5 seconds; all subsequent NLP calls reuse the pre-loaded pipeline with negligible overhead.

### 4.6 Database Design

The database schema consists of two tables whose structure mirrors the two-pipeline architecture. The `domain_diagrams` table serves the primary PlantUML pipeline. Its schema records a UUID primary key, a creation timestamp, the diagram type (constrained to the eleven valid enum string values), the original input text, the name of the AI model or NLP pipeline that produced the diagram, and the full PlantUML source code. The `diagrams` table serves the legacy Mermaid pipeline, storing a UUID primary key, creation timestamp, diagram type, input text, and Mermaid syntax code. Both tables are managed through their respective Spring Data JPA repositories: `DomainDiagramRepository` for the primary pipeline and `DiagramRepository` for the legacy pipeline.

An additional export capability is provided by `DiagramDrawIoController`, which handles `GET /api/diagram/{id}/drawio` requests. This controller applies a fallback lookup pattern: it first queries the `diagrams` table, and if the requested UUID is not found there, queries the `domain_diagrams` table. This dual-table lookup ensures that Draw.io export remains reachable for diagrams from either pipeline without the client needing to know which pipeline produced the diagram.

For the development and test environment, an H2 in-memory database is substituted for PostgreSQL. The `application-dev.properties` file configures a `jdbc:h2:mem:ai_diagrams_dev` connection with `ddl-auto=create-drop`, ensuring a clean schema for each test run without requiring a running PostgreSQL instance.

### 4.7 Component Interactions

The internal component architecture of the application tier follows a layered dependency hierarchy. `PlantUmlDiagramController` receives and validates requests from the client and delegates processing to `ConfidenceDiagramServiceImpl`, which acts as the sole orchestrator of the primary pipeline. The orchestrator does not implement any domain logic itself; instead, it coordinates the following downstream components in sequence: `DiagramClassificationServiceImpl` for type determination, `DiagramSuggestionServiceImpl` for confidence scoring, `SemanticExtractionServiceImpl` for entity and relationship extraction, `PlantUmlGenerationServiceImpl` for PlantUML code synthesis, `DiagramRenderingServiceImpl` for image compilation, and `DomainDiagramRepository` for persistence.

Both `DiagramClassificationServiceImpl` and `SemanticExtractionServiceImpl` depend on the `AiModelService` interface, and both contain local fallback logic activated when the AI call fails. `DiagramSuggestionServiceImpl` avoids duplicating classification logic by delegating entirely to `DiagramClassificationServiceImpl` rather than maintaining its own pattern sets. The rendering component, `DiagramRenderingServiceImpl`, depends solely on the PlantUML library and performs three validation steps before rendering: it verifies the input is non-empty, confirms the string begins with a `@start` prefix, and enforces a ten-megabyte output size limit.

Exception handling is centralized in `GlobalExceptionHandler`, which maps the application's exception hierarchy to appropriate HTTP status codes: `DiagramNotFoundException` yields 404, `InvalidDiagramRequestException` yields 400, `DiagramGenerationException` yields 500, and `DiagramRenderingException` yields 500. This centralization ensures that no business exception reaches the client as an unhandled 500 response, and that error response bodies carry a consistent structure including timestamp, status code, error category, message text, and request path.

---

## Chapter 5: Implementation

### 5.1 Overview

This chapter describes the implementation of the principal components of the primary diagram generation pipeline, focusing on the design decisions, algorithms, and notable fixes applied during development. The implementation spans the request validation and normalization layer, the orchestration and confidence evaluation logic, the semantic extraction and generation services, and the diagram rendering and export subsystems.

### 5.2 Request Validation and DTO Normalization

Incoming generation requests are represented by the `GenerationRequest` data transfer object, located in `dto/request/GenerationRequest.java`. A notable implementation detail is the acceptance of three distinct field names for the primary input text. The Jackson annotation `@JsonAlias({"description", "inputText"})` is applied to the `text` field, allowing the API to accept payloads where the user description is transmitted as `text`, `description`, or `inputText`. This alias mapping was introduced to maintain backward compatibility with clients that were using the older field naming conventions of the legacy pipeline, while adopting a more explicit field name for new clients. The field is also annotated with `@Size(max = 10000)` to guard against excessively long inputs that could produce OpenAI context length errors or cause extended NLP processing. The remaining fields are `diagramType` (a `String` representing the requested diagram category), `seed` (an optional `Long` for reproducible diagram layout), and `forceGenerate` (a `boolean` that, when true, bypasses the confidence gate).

At the controller level, `PlantUmlDiagramController` receives the deserialized `GenerationRequest`, performs logging, and delegates to `ConfidenceDiagramServiceImpl`. Invalid requests — such as those specifying an unrecognized diagram type — produce an `InvalidDiagramRequestException` that the controller catches explicitly, returning a 400 response to the client. This explicit catch was a fix applied during development; previously, invalid type strings propagated as unhandled exceptions, causing the global handler to return a 500 response even though the error was the client's fault.

### 5.3 Diagram Type Resolution and Alias Mapping

The `ConfidenceDiagramServiceImpl` class includes a twenty-seven-entry static map named `DIAGRAM_TYPE_ALIASES` that translates the wide variety of strings a client might send for the `diagramType` field into the canonical `domain.DiagramType` enumeration values recognized by the rest of the pipeline. This map accommodates lowercase variants (`"class"`, `"sequence"`, `"er"`), common abbreviations (`"erd"`, `"uml"`), human-readable phrases (`"entity relationship"`, `"state machine"`, `"class diagram"`), underscore-separated identifiers (`"CLASS_DIAGRAM"`, `"USE_CASE"`), and common colloquial terms (`"flow"`, `"flowchart"`, which both map to `ACTIVITY`). The resolution logic in `mapToEnum()` normalizes the input string to lowercase, looks it up in the alias map first, and falls back to the Java `valueOf()` method and then the `fromCode()` method of `domain.DiagramType`. If none of these resolution paths succeeds, an `InvalidDiagramRequestException` is thrown with a message that includes the rejected type string, enabling the client to diagnose the error without inspecting server logs.

This alias resolution was a key fix to the manual diagram generation flow: prior to its implementation, a user selecting "Class Diagram" from the frontend dropdown would send the string `"CLASS"` which the API accepted, but strings like `"class diagram"` or `"SEQUENCE_DIAGRAM"` — which some API clients were sending — caused silent failures or 500 errors. The alias map now handles all these variants deterministically.

### 5.4 Template-Based Generation Fallback

`ConfidenceDiagramServiceImpl` contains a static `DEFAULT_TEMPLATES` map that associates each of the eleven `domain.DiagramType` values with a hardcoded minimal PlantUML diagram string. This map is used in two circumstances: when the request contains no input text (a manual generation mode), and when the full AI and NLP pipeline fails to produce a non-empty `SemanticModel`.

When the input text is absent or blank, the orchestrator calls `generateFromTemplate()` directly, bypassing all classification, confidence evaluation, semantic extraction, and AI interaction. The template string for the requested diagram type is retrieved from the map, passed to `DiagramRenderingServiceImpl` for validation and image compilation, and the result is persisted to `domain_diagrams` with `modelUsed` set to `"template"`. This path provides a deterministic, externally-independent generation mode that is particularly valuable for testing, since it produces a known PlantUML output without requiring any external service calls.

When the pipeline is invoked with text but encounters failures in semantic extraction or generation, the `generateWithFallback()` method wraps the full pipeline in a try-catch and calls `generateFromTemplate()` as a last resort. This ensures the system always returns a valid, renderable PlantUML diagram rather than propagating a 500 error to the client when the AI layer fails.

### 5.5 Semantic Extraction

`SemanticExtractionServiceImpl` is responsible for transforming raw input text into a structured `SemanticModel` containing entity nodes, relationships, and action verbs. The primary extraction path calls `OpenAiService.callLLM()` with a structured prompt requesting entity and relationship identification in a machine-parseable format. The resulting JSON is parsed into `SemanticModel` fields.

When the AI call fails or returns `isSuccess() = false`, the service activates the Stanford CoreNLP pipeline. This pipeline is initialized once at application startup by constructing a `StanfordCoreNLP` object configured with five annotators: `tokenize`, `ssplit`, `pos`, `lemma`, `ner`, and `depparse`. Entity detection applies compiled regular expressions for capitalized-word sequences and PascalCase identifiers. Relationship extraction matches dependency parse output against a vocabulary of thirty or more relationship verbs (such as "extends", "implements", "uses", "contains", "calls") and applies thirteen multiplicity patterns to identify cardinalities. Action verbs — relevant to sequence and activity diagrams — are extracted from the set of recognized tokens whose part-of-speech tag indicates a verb and whose lemma appears in a predefined action verb vocabulary.

A known limitation of the current implementation is that the AI extraction prompt does not vary by diagram type. The same entity-and-relationship prompt designed for class diagram extraction is sent regardless of whether the target is a sequence diagram, an activity diagram, or a state machine. This means the `SemanticModel` is populated with class-diagram-oriented fields even for diagram types whose semantics differ fundamentally, and the downstream generator for those types must accommodate a potentially inappropriate model structure.

### 5.6 PlantUML Code Generation

`PlantUmlGenerationServiceImpl` converts a `SemanticModel` into a valid PlantUML source string. The class contains an eleven-case switch statement that dispatches on the `domain.DiagramType` value to a private generation method for each diagram type. The methods are: `generateClassDiagram()`, `generateSequenceDiagram()`, `generateErDiagram()`, `generateUseCaseDiagram()`, `generateComponentDiagram()`, `generateDeploymentDiagram()`, `generateActivityDiagram()`, `generateStateDiagram()`, `generateObjectDiagram()`, `generateMicroservicesDiagram()`, and `generateCollaborationDiagram()`. Each method reads the entity nodes, relationships, and action verbs from the `SemanticModel` and constructs the corresponding PlantUML syntax, beginning with `@startuml` and concluding with `@enduml`.

Two profile objects are accepted as parameters alongside the `SemanticModel`: a `StyleProfile` that controls color schemes, font selections, and line styles; and a `LayoutProfile` that controls diagram direction and node spacing. A known defect exists in the layout profile handling: the condition that applies directional layout preferences compares the `LayoutProfile.Direction` enum value against a hardcoded string literal `"top to bottom direction"`. Because a Java enum instance never satisfies equality with a `String`, this condition is permanently false, and layout direction is never applied intentionally. The visible consequence is that diagram layout direction varies non-deterministically even when a seed is provided.

The switch statement is duplicated in two overloaded `generate()` method signatures, meaning that any change to diagram generation logic for a given type requires parallel edits in both overloads. This structural duplication is identified as a maintenance risk and is addressed in the proposed refactoring roadmap described in Chapter 7.

### 5.7 Diagram Rendering

`DiagramRenderingServiceImpl` accepts a PlantUML source string and compiles it to binary image data using the `net.sourceforge.plantuml.SourceStringReader` class from the PlantUML library. Before invoking the renderer, the service performs three validation checks: the input string must be non-null and non-empty; it must begin with a `@start` prefix (for example, `@startuml` or `@startgantt`); and the rendered output must not exceed ten megabytes. Violation of any of these conditions raises a `DiagramRenderingException` with a descriptive error category (`INVALID_SYNTAX`, `RENDERING_ERROR`, or `OUTPUT_ERROR`), which the global exception handler maps to an HTTP 500 response.

Rendering is performed synchronously on the HTTP request thread, which means that complex diagrams whose PlantUML generation takes several hundred milliseconds of CPU time will block the Tomcat request thread for the full duration. Under concurrent load, this synchronous rendering model limits the system's effective throughput proportionally to render time, as documented in the limitations analysis.

The PNG and SVG endpoints exposed at `GET /api/diagram/{id}/png` and `GET /api/diagram/{id}/svg` retrieve the stored `plantUmlCode` from `domain_diagrams` by UUID and re-render on demand, falling back to the `diagrams` (legacy) table if the UUID is not found in the primary table. This on-demand rendering approach avoids storing large binary images in the database at the cost of re-executing the render on each download request.

### 5.8 Draw.io Export

The `DiagramDrawIoController` provides an export capability that converts stored diagram data into Draw.io XML format, accessible at `GET /api/diagram/{id}/drawio`. The controller applies a dual-table fallback pattern to support both pipelines: it first queries `DiagramRepository` (the legacy `diagrams` table), and if the requested UUID is absent, queries `DomainDiagramRepository` (the primary `domain_diagrams` table). The diagram's PlantUML or Mermaid code is then converted to Draw.io XML syntax by constructing an `<mxfile>` document, and the response is returned with content type `application/xml`.

This dual-table lookup resolved a previous conflict in which `PlantUmlDiagramController` had defined its own Draw.io download endpoint at the same URL path. The conflicting endpoint was removed during the bug-fixing phase of development, and all Draw.io export traffic was consolidated into `DiagramDrawIoController`, which is the sole handler for this URL pattern.

### 5.9 Exception Handling and Error Propagation

The system's exception hierarchy is flat and domain-expressive. `InvalidDiagramRequestException` signals client errors — malformed input, unrecognized type strings, or requests that violate business constraints. `DiagramNotFoundException` signals that a requested UUID does not exist in either table. `DiagramGenerationException` signals failures in the generation pipeline, such as an empty semantic model with no template fallback. `DiagramRenderingException` signals failures in the PlantUML compilation stage. All four exceptions extend a common base or are independently caught by `GlobalExceptionHandler`, which produces a consistent JSON error body with timestamp, HTTP status code, error category, human-readable message, and request path.

A notable fix applied during development was the introduction of an explicit null guard in `ConfidenceDiagramServiceImpl` for the case where `forceGenerate = true` is combined with a blank or absent `diagramType`. Previously, this combination caused a `NullPointerException` that propagated through the global handler as a 500 response. After the fix, this condition raises an `InvalidDiagramRequestException` with a descriptive message, producing a proper 400 response.

---

## Chapter 6: Testing

### 6.1 Testing Strategy

The testing approach adopted for the AI Diagram Generator combines unit testing of individual service components with integration testing of the full Spring Boot application context. The overarching goal of the testing strategy is to verify correct behaviour of the primary PlantUML pipeline — including request handling, type resolution, template generation, rendering, and export — without incurring any dependency on external services. Achieving this independence is non-trivial in a system whose core value proposition involves calls to OpenAI's API; the design of the test suite therefore relies on two complementary mechanisms: Mockito-based mocking for unit tests that verify controller and service interactions in isolation, and template-path invocation for integration tests that exercise the full pipeline without triggering AI calls.

The template-path invocation strategy exploits the fact that when a generation request contains no `text` field, `ConfidenceDiagramServiceImpl` immediately calls `generateFromTemplate()` rather than entering the classification-and-extraction pipeline. Because the template map contains a hardcoded PlantUML string for each of the eleven diagram types, requests of this form are processed entirely within the JVM, using only the PlantUML rendering library, and are free of any network dependency. This makes them fast, deterministic, and suitable for the CI environment.

All integration tests are annotated with `@SpringBootTest` to load the full application context, `@ActiveProfiles("dev")` to activate the H2 in-memory database configuration, and `@Transactional` to ensure that database state written during each test is rolled back automatically at test completion, providing isolation between test methods without requiring manual cleanup.

### 6.2 Unit Tests: PlantUmlDiagramAssetEndpointTest

`PlantUmlDiagramAssetEndpointTest` exercises the PNG and SVG asset download endpoints of `PlantUmlDiagramController` using Mockito mocks for all dependencies. The test class instantiates the controller directly, passing mock objects for each of the seven constructor parameters: `ConfidenceDiagramService`, `DiagramSuggestionService`, `DiagramRenderingService`, `DomainDiagramRepository`, `DiagramRepository`, a Mermaid renderer, and `EvaluationRepository`. This approach verifies the controller's response-building logic — including content-type headers, attachment disposition headers, and fallback behaviour when a diagram UUID is absent from the primary table but present in the legacy table — without involving any Spring application context or database.

A defect corrected during the development of this test class was the removal of a stale constructor argument. An earlier version of `PlantUmlDiagramController` had accepted a `DrawIoExportService` as an eighth constructor parameter for a Draw.io download endpoint that was subsequently moved to `DiagramDrawIoController`. The test class had retained the stale mock field and the stale eighth argument, causing the constructor call to fail with an argument-count mismatch at test initialization. The fix consisted of removing the `@Mock DrawIoExportService drawIoExportService` field and reducing the constructor invocation to the correct seven arguments, aligning the test setup with the current production constructor signature.

### 6.3 Integration Tests: ManualDiagramGenerationFlowTest

`ManualDiagramGenerationFlowTest` is a comprehensive integration test class created to verify the corrected manual diagram generation flow across five distinct testing concerns, organized as `@Nested` inner classes. The class uses `MockMvc` via `WebApplicationContext` to dispatch HTTP requests through the full filter and controller chain within the Spring context, receiving real `MockMvcResult` objects that can be inspected for status codes, headers, and response body content.

**AllDiagramTypes** contains eleven parameterized test cases, one for each diagram type supported by the primary pipeline: `CLASS`, `SEQUENCE`, `ER`, `COMPONENT`, `DEPLOYMENT`, `USE_CASE`, `OBJECT`, `ACTIVITY`, `STATE`, `COLLABORATION`, and `MICROSERVICES_ARCHITECTURE`. Each test sends a POST request to `/api/diagram/generate` with only the `diagramType` field set and no `text` field, triggering the template-path generation. The test asserts that the response is HTTP 200 and that the response body contains the `@startuml` prefix, confirming that a valid PlantUML string was produced and that no exception escaped to the error handler.

**FieldNormalization** verifies the `@JsonAlias` behaviour of `GenerationRequest.text`. Four test cases confirm that the same payload produces equivalent responses regardless of whether the description field is named `text`, `description`, or `inputText`, and that a request with none of these fields also succeeds through the template path. This test group was motivated directly by the bug fix that introduced the `@JsonAlias` annotation; these tests would have failed against the pre-fix codebase.

**TypeAliasMapping** contains eleven parameterized test cases that send human-readable or variant-formatted type strings — such as `"class diagram"`, `"CLASS_DIAGRAM"`, `"entity relationship"`, `"state machine"`, and `"MICROSERVICES_ARCHITECTURE"` — and verify that each is resolved to the correct `domain.DiagramType` by examining the PlantUML snippet present in the response body. For example, a request with `diagramType: "class diagram"` should produce a response containing PlantUML class diagram syntax, confirming that the alias map translated the input correctly before dispatching to the generation method.

**UnsupportedType** verifies that diagram type strings which have no mapping in the alias system are rejected with an HTTP 400 response. The test cases cover `"FLOWCHART"`, `"GANTT"`, and `"PIE_CHART"` — types not present in `domain.DiagramType` — as well as the combination of `forceGenerate: true` with an absent `diagramType` field, which triggers the null guard fix described in Section 5.9. Each test additionally asserts that the error response body contains the rejected type string, confirming that the `InvalidDiagramRequestException` message is surfaced to the client rather than replaced by a generic error message.

**ExportEndpoints** verifies the asset retrieval endpoints using a diagram generated within the same test transaction. A diagram is first created by posting a template-path request, and the returned UUID is used to make subsequent requests to `GET /api/diagram/{id}` (expecting `application/json`), `GET /api/diagram/{id}/png` (expecting `image/png`), `GET /api/diagram/{id}/svg` (expecting `image/svg+xml`), and `GET /api/diagram/{id}/drawio` (expecting `application/xml` with an `<mxfile` root element). These four assertions confirm that a diagram persisted through the template path is retrievable through all four export endpoints, and that the `DiagramDrawIoController`'s dual-table lookup correctly locates diagrams from the primary pipeline.

The complete test suite for `ManualDiagramGenerationFlowTest` comprises thirty-eight test cases, all of which pass consistently without requiring any external service, running against the H2 in-memory database with full application context.

### 6.4 Evaluation-Based Testing

The repository includes three JSON datasets in `src/test/resources/` designed to support batch accuracy evaluation of the classification and generation components. `evaluation-dataset.json` contains a collection of natural language inputs paired with expected diagram type classifications, used to measure the accuracy of the five-layer classification cascade. `generation-evaluation-dataset.json` contains inputs paired with expected structural properties of the generated PlantUML output. `usecase-evaluation-dataset.json` contains inputs specific to use case diagram generation for targeted assessment of that diagram type's generation quality.

These datasets are consumed by the `EvaluationController`, which exposes batch evaluation endpoints that execute the full pipeline against each dataset entry and report accuracy metrics. This evaluation framework allows the development team to measure the quantitative impact of changes to the classification service, the AI prompt design, or the keyword term sets without requiring manual review of individual diagram outputs. The datasets are version-controlled alongside the source code, ensuring that evaluation results are reproducible across builds.

### 6.5 Test Infrastructure

The test infrastructure relies on two Spring profiles: the default profile, which targets PostgreSQL and is used for manual integration testing against a running database, and the `dev` profile, which substitutes H2 and is activated for automated test runs. The distinction between these profiles is encoded in `application.properties` and `application-dev.properties` respectively, and the `@ActiveProfiles("dev")` annotation on integration test classes ensures that the automated test suite never requires a running PostgreSQL instance.

Bean validation is exercised implicitly through the integration test path, since `MockMvc` requests pass through the full Spring MVC pipeline including argument resolution, message conversion, and `@Valid` processing. The `GlobalExceptionHandler` is therefore active during integration tests, and its exception-to-status-code mappings are verified by the tests in `UnsupportedType` and indirectly by all tests that assert on the HTTP status code of the response.

---

## Limitations

### Architectural Constraints

The most significant architectural limitation of the system is the coexistence of two independent diagram generation pipelines that share no code, entity model, or type system. The primary PlantUML pipeline and the legacy Mermaid pipeline each maintain their own controller, service hierarchy, JPA entity, repository interface, and database table. This duplication doubles the maintenance burden for every future change to shared concerns — such as input validation, confidence modelling, or diagram type addition — and introduces the risk of the two pipelines diverging in behaviour without detection. The incompatibility of the two `DiagramType` enumeration types (`domain.DiagramType` and `enums.DiagramType`) further complicates any attempt to share logic between the pipelines, as every boundary crossing requires an explicit mapping step.

A related structural problem is the monolithic nature of `PlantUmlGenerationServiceImpl`. This class implements all eleven PlantUML diagram generators as private methods within a single class, amounting to a God Class violation of the Single Responsibility Principle. The eleven-case switch statement that dispatches between these generators is duplicated across two overloaded `generate()` method signatures, meaning that any modification to diagram generation logic requires parallel edits in two places. Furthermore, six Spring beans named with the pattern `*DiagramGeneratorServiceImpl` — intended to implement per-type generation as injectable components — are never injected anywhere in the application context and represent dead code from an incomplete refactoring.

The `DiagramGeneratorRegistry` of the legacy pipeline, which correctly implements a strategy pattern for generator selection and auto-discovers thirteen `DiagramGenerator` implementations at startup, is similarly unused by the primary pipeline. The registry cannot be reused by `PlantUmlGenerationServiceImpl` without enum conversion, and this incompatibility has prevented the existing strategy pattern infrastructure from being applied to the primary pipeline.

### Semantic and Classification Limitations

`SemanticExtractionServiceImpl` sends the same OpenAI prompt — designed for class diagram entity extraction — regardless of the actual target diagram type. A request for a state machine diagram therefore receives a prompt asking for classes, attributes, and methods, which are not the appropriate semantic units for state diagrams. Similarly, `DiagramSuggestionServiceImpl` contains semantic pattern sets for only six of the eleven diagram types, omitting ACTIVITY, STATE, OBJECT, MICROSERVICES, and COLLABORATION. For these five types, the suggestion and confidence scoring subsystem relies entirely on the AI layer, which is subject to quota limitations, reducing classification accuracy for these types when the OpenAI API is unavailable.

Five diagram types also lack configured `StyleProfile` entries in `StyleProfileServiceImpl`: ACTIVITY, STATE, OBJECT, MICROSERVICES, and COLLABORATION. These types receive a default style profile tuned for class diagram layout, resulting in suboptimal visual output for flow-oriented and state-oriented diagram types.

### Operational Limitations

The system does not implement any caching for diagram generation results. Identical or near-identical inputs submitted in separate requests each trigger the full pipeline, including AI calls, NLP processing, PlantUML generation, and rendering. Under high request volumes, this absence of caching increases latency, consumes AI API quota, and generates redundant CPU load from PlantUML rendering.

Diagram rendering is performed synchronously on the HTTP request thread. PlantUML rendering is CPU-bound and may take several hundred milliseconds for complex diagrams. Under concurrent load, this blocks Tomcat worker threads proportionally to render time, limiting the system's effective throughput. The `RestClient` used for OpenAI API calls is also configured without connection or response timeouts, meaning that a hanging API request will block the processing thread indefinitely, potentially causing thread pool exhaustion under sustained load.

The monolithic architecture results in the Stanford CoreNLP NLP pipeline and the PlantUML rendering engine competing for JVM heap and CPU within the same process. The CoreNLP model occupies substantial heap memory, and PlantUML rendering is CPU-intensive; under concurrent load, contention between these two subsystems increases request latency without any workload isolation mechanism.

The system does not validate or sanitize input text before sending it to the OpenAI API, leaving the prompt construction path open to prompt injection attacks in which a malicious user might embed instructions in their diagram description intended to manipulate the model's output or extract system prompt content.

---

## Future Work

### Consolidation of the Dual Pipeline Architecture

The most impactful architectural improvement that could be made to this system is the elimination of the dual pipeline structure in favour of a single, format-agnostic pipeline. This unified pipeline would expose a single URL namespace (for example, `/api/v2/diagrams`), operate on a single canonical `DiagramType` enumeration, persist to a single database table, and serve all client needs — including the specialised input modes currently handled by the legacy pipeline's `/from-pdf`, `/from-xml`, and `/from-url` endpoints. Legacy endpoints should be retained as deprecated wrappers that delegate to the unified pipeline, ensuring backward compatibility without maintaining parallel implementation logic.

This consolidation would also resolve the duplicate entity and repository problem. A single `DiagramEntity` in a shared `persistence` package, with a corresponding migration script merging the `diagrams` table into `domain_diagrams` (or a replacement), would eliminate the dual-table lookup workaround present in `DiagramDrawIoController` and allow the Draw.io export, analytics, and evaluation infrastructure to operate on a single data source.

### Strategy Pattern for Diagram Generators

The current monolithic `PlantUmlGenerationServiceImpl` should be decomposed into a strategy hierarchy following the template already established by the legacy pipeline's `DiagramGeneratorRegistry`. A `PlantUmlGenerator` interface would define a contract consisting of a `supports()` method returning a `DiagramType` and a `generate()` method accepting a `SemanticModel`, `StyleProfile`, and `LayoutProfile`. Each of the eleven diagram types would then have its own `@Component` implementation — for example, `ClassPlantUmlGenerator`, `SequencePlantUmlGenerator`, and `StatePlantUmlGenerator` — each containing only the generation logic relevant to its type. A `PlantUmlGeneratorRegistry` bean would auto-discover these implementations via Spring's list injection and dispatch by `DiagramType`, replacing the duplicated switch statement entirely. Adding a new diagram type would then require only the creation of a single new implementation class, with no modification to any existing class.

### Type-Aware Semantic Extraction

The semantic extraction layer should be redesigned to issue type-appropriate prompts to the language model rather than a single class-diagram-oriented prompt for all types. Sequence diagram extraction should prompt for actors, messages, and ordering; state diagram extraction should prompt for states, transitions, and guards; activity diagram extraction should prompt for actions, decisions, forks, and swimlanes. This per-type prompt design would materially improve the quality of the `SemanticModel` produced for non-class diagram types and reduce the degree to which downstream generators must compensate for semantically inappropriate model content.

Similarly, the `DiagramSuggestionServiceImpl` should be extended to cover all eleven diagram types with appropriate `SEMANTIC_CATEGORIES` and `KEYWORD_SETS` entries. The five omitted types — ACTIVITY, STATE, OBJECT, MICROSERVICES, and COLLABORATION — should receive keyword vocabularies and semantic pattern sets of equivalent completeness to those already defined for the six covered types. Once this extension is made, the suggestion service's duplicate classification constants should be removed, and all classification logic should be delegated entirely to `DiagramClassificationServiceImpl` as a single source of truth.

### Asynchronous Rendering and Result Caching

Diagram rendering should be moved off the HTTP request thread to a dedicated thread pool using either Spring's `@Async` mechanism or an explicit `ExecutorService` with a bounded queue. Long-running render operations should return a `202 Accepted` response carrying a job identifier, with the client polling a status endpoint or receiving a Server-Sent Event upon completion. This decoupling would prevent rendering latency from blocking request threads and would allow the server to accept a higher volume of concurrent generation requests.

A content-addressable caching layer keyed on a SHA-256 hash of the input text and diagram type would eliminate redundant AI calls and rendering work for duplicate requests. For a single-instance deployment, Caffeine provides a lightweight in-process cache; for a horizontally scaled deployment, Redis would allow the cache to be shared across instances. Classification and semantic extraction results should be cached separately from rendered images, since they may be reused across different output format requests for the same input.

### REST Client Hardening and AI Provider Resilience

The `RestClient` used for OpenAI API communication should be configured with explicit connection and response timeouts to prevent indefinite thread blocking. A retry policy with exponential backoff and jitter should be implemented for HTTP 429 and 503 responses, using a library such as Resilience4j or Spring Retry. The health endpoint at `/api/health` should be expanded to report the current AI provider status, the last failure timestamp, whether the system is operating in NLP fallback mode, and the CoreNLP model load state. This information is essential for operators to diagnose degraded-mode operation without inspecting application logs.

### Input Sanitization and Security Hardening

A centralized input sanitization stage should be inserted before the AI prompt construction step to detect and neutralize prompt injection attempts. This stage should strip or escape instruction-like patterns in user-supplied text — such as sequences beginning with "Ignore all previous instructions" — before the text is embedded in an LLM prompt. The stage should also enforce maximum input length limits appropriate to the configured model's context window, and should log a security event when a potential injection attempt is detected. These measures would address the current absence of any prompt injection protection identified as a security gap.

### User Accounts and Diagram Persistence

The current system stores all diagrams in a shared namespace accessible by UUID to any caller who knows the identifier. The addition of Spring Security with JWT authentication would allow diagrams to be associated with authenticated user accounts, enabling personal diagram libraries with search, tagging, and revision history. Diagram versioning — maintaining a history of edits to a given diagram identified by a stable logical ID — would support iterative refinement workflows in which users issue follow-up natural language instructions to modify an existing diagram rather than regenerating it from scratch.

### Containerization and Production Deployment

The application should be containerized using a multi-stage Dockerfile that produces a minimal JRE image based on Eclipse Temurin 24. A production deployment topology would place the application behind a load balancer or reverse proxy, with a managed PostgreSQL instance — such as Amazon RDS or Google Cloud SQL — providing the data tier, and an object storage service providing a permanent store for rendered image bytes rather than re-rendering on every request. Observability infrastructure using Micrometer with a Prometheus and Grafana stack would expose per-type generation throughput, AI-versus-NLP fallback ratios, rendering latency percentiles, and rendering failure rates as production metrics.
