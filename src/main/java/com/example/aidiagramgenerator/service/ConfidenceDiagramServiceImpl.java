package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramSuggestion.ClassificationSource;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.domain.StyleProfile;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation;
import com.example.aidiagramgenerator.dto.response.GenerationResult;
import com.example.aidiagramgenerator.exception.DiagramGenerationException;
import com.example.aidiagramgenerator.exception.InvalidDiagramRequestException;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Confidence-based diagram generation orchestrator.
 *
 * <p>Implements a three-tier decision model based on the classification
 * confidence score:
 * <ul>
 *   <li><strong>HIGH (≥ 70%)</strong> — full pipeline: extract → generate → render → persist</li>
 *   <li><strong>MEDIUM (40–69%)</strong> — return a suggestion message for user confirmation</li>
 *   <li><strong>LOW (&lt; 40%)</strong> — reject with a "provide more details" message</li>
 * </ul>
 *
 * <p>When the frontend supplies an explicit diagram type, classification is
 * skipped and confidence is treated as 100%.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Service
public class ConfidenceDiagramServiceImpl implements ConfidenceDiagramService {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceDiagramServiceImpl.class);

    /** Confidence at or above this threshold triggers automatic generation. */
    private final int highConfidenceThreshold;

    /** Confidence at or above this threshold returns a suggestion for confirmation. */
    private final int mediumConfidenceThreshold;

    /**
     * Relationship verbs that signal a structural connection between entities.
     * If at least one is present alongside ≥2 entity nouns, a REJECT is promoted to SUGGEST.
     */
    private static final Set<String> STRUCTURAL_RELATIONSHIP_WORDS = Set.of(
            "has", "have", "sends", "calls", "belongs"
    );

    /** Common English stop words excluded from entity-noun counting. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "nor", "so", "yet",
            "is", "are", "was", "were", "be", "been", "being",
            "do", "does", "did", "will", "would", "shall", "should",
            "may", "might", "must", "can", "could",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "up", "out", "off", "about", "into", "through", "that", "this",
            "these", "those", "not", "if", "as", "when", "where", "how",
            "i", "you", "he", "she", "it", "we", "they", "me", "him",
            "her", "us", "them", "my", "your", "his", "its", "our", "their"
    );

    /** Confidence bonus added when strong ER signals are detected in the input. */
    private static final int ER_CONFIDENCE_BOOST = 30;

    /**
     * Phrases that strongly indicate an Entity-Relationship diagram.
     * Any single match qualifies as an ER signal alongside comma-separated entities.
     */
    private static final List<String> ER_SIGNAL_PHRASES = List.of(
            "has many", "belongs to", "foreign key", "primary key"
    );

    /**
     * Default PlantUML templates returned when the user selects a diagram type manually
     * but provides no description text.
     */
    private static final Map<DiagramType, String> DEFAULT_TEMPLATES = Map.ofEntries(
            Map.entry(DiagramType.SEQUENCE,
                    "@startuml\n" +
                    "actor User\n" +
                    "participant \"Auth Service\" as Auth\n" +
                    "participant \"Order Service\" as Orders\n" +
                    "database \"Database\" as DB\n" +
                    "\n" +
                    "User -> Auth : login(username, password)\n" +
                    "Auth -> DB : validateCredentials()\n" +
                    "DB --> Auth : credentials OK\n" +
                    "Auth --> User : JWT token\n" +
                    "\n" +
                    "User -> Orders : createOrder(token, items)\n" +
                    "Orders -> DB : saveOrder(items)\n" +
                    "DB --> Orders : orderId\n" +
                    "Orders --> User : orderConfirmation(orderId)\n" +
                    "@enduml"),

            Map.entry(DiagramType.CLASS,
                    "@startuml\n" +
                    "class User {\n" +
                    "  +String username\n" +
                    "  +String email\n" +
                    "  -String password\n" +
                    "  +login()\n" +
                    "  +logout()\n" +
                    "}\n" +
                    "class Order {\n" +
                    "  +String orderId\n" +
                    "  +Date createdAt\n" +
                    "  +double totalAmount\n" +
                    "  +submit()\n" +
                    "  +cancel()\n" +
                    "}\n" +
                    "class Product {\n" +
                    "  +String name\n" +
                    "  +double price\n" +
                    "  +int stock\n" +
                    "}\n" +
                    "class Admin {\n" +
                    "  +manageUsers()\n" +
                    "}\n" +
                    "Admin --|> User\n" +
                    "User \"1\" o-- \"0..*\" Order : places\n" +
                    "Order \"1\" *-- \"1..*\" Product : contains\n" +
                    "@enduml"),

            Map.entry(DiagramType.ER,
                    "@startuml\n" +
                    "entity Customer {\n" +
                    "  * id : int <<PK>>\n" +
                    "  --\n" +
                    "  name : string\n" +
                    "  email : string\n" +
                    "}\n" +
                    "entity Order {\n" +
                    "  * id : int <<PK>>\n" +
                    "  --\n" +
                    "  customer_id : int <<FK>>\n" +
                    "  total : decimal\n" +
                    "  status : string\n" +
                    "}\n" +
                    "entity Product {\n" +
                    "  * id : int <<PK>>\n" +
                    "  --\n" +
                    "  name : string\n" +
                    "  price : decimal\n" +
                    "}\n" +
                    "entity OrderItem {\n" +
                    "  * id : int <<PK>>\n" +
                    "  --\n" +
                    "  order_id : int <<FK>>\n" +
                    "  product_id : int <<FK>>\n" +
                    "  quantity : int\n" +
                    "}\n" +
                    "Customer ||--o{ Order : places\n" +
                    "Order ||--|{ OrderItem : contains\n" +
                    "Product ||--o{ OrderItem : \"in\"\n" +
                    "@enduml"),

            Map.entry(DiagramType.COMPONENT,
                    "@startuml\n" +
                    "package \"Frontend\" {\n" +
                    "  [Web App] as web\n" +
                    "}\n" +
                    "package \"Backend\" {\n" +
                    "  [API Gateway] as gw\n" +
                    "  [Auth Service] as auth\n" +
                    "  [Business Logic] as biz\n" +
                    "}\n" +
                    "database \"Database\" as db\n" +
                    "web --> gw : HTTPS\n" +
                    "gw --> auth : authenticate\n" +
                    "gw --> biz : delegate\n" +
                    "biz --> db : query\n" +
                    "@enduml"),

            Map.entry(DiagramType.DEPLOYMENT,
                    "@startuml\n" +
                    "node \"Client\" {\n" +
                    "  [Browser]\n" +
                    "}\n" +
                    "node \"Web Server\" {\n" +
                    "  [Nginx]\n" +
                    "  [Spring Boot App]\n" +
                    "}\n" +
                    "database \"PostgreSQL\" as db\n" +
                    "node \"Cache\" {\n" +
                    "  [Redis]\n" +
                    "}\n" +
                    "Browser --> Nginx : HTTPS\n" +
                    "Nginx --> [Spring Boot App] : reverse proxy\n" +
                    "[Spring Boot App] --> db : JDBC\n" +
                    "[Spring Boot App] --> Redis : cache\n" +
                    "@enduml"),

            Map.entry(DiagramType.USE_CASE,
                    "@startuml\n" +
                    "actor Customer\n" +
                    "actor Admin\n" +
                    "rectangle \"Shopping System\" {\n" +
                    "  (Browse Products) as browse\n" +
                    "  (Add to Cart) as cart\n" +
                    "  (Checkout) as checkout\n" +
                    "  (Manage Products) as manage\n" +
                    "  (View Reports) as reports\n" +
                    "}\n" +
                    "Customer --> browse\n" +
                    "Customer --> cart\n" +
                    "Customer --> checkout\n" +
                    "Admin --> manage\n" +
                    "Admin --> reports\n" +
                    "checkout ..> (Authenticate) : <<include>>\n" +
                    "@enduml"),

            Map.entry(DiagramType.OBJECT,
                    "@startuml\n" +
                    "object \"alice : Customer\" as alice {\n" +
                    "  name = \"Alice Smith\"\n" +
                    "  email = \"alice@example.com\"\n" +
                    "}\n" +
                    "object \"order1 : Order\" as order1 {\n" +
                    "  orderId = \"ORD-001\"\n" +
                    "  totalAmount = 149.99\n" +
                    "}\n" +
                    "object \"laptop : Product\" as laptop {\n" +
                    "  name = \"Laptop Pro\"\n" +
                    "  price = 149.99\n" +
                    "}\n" +
                    "alice --> order1 : places\n" +
                    "order1 --> laptop : contains\n" +
                    "@enduml"),

            Map.entry(DiagramType.ACTIVITY,
                    "@startuml\n" +
                    "start\n" +
                    ":User submits login form;\n" +
                    "if (Valid credentials?) then (yes)\n" +
                    "  :Create session token;\n" +
                    "  :Store token in cookie;\n" +
                    "  :Redirect to dashboard;\n" +
                    "else (no)\n" +
                    "  :Increment failed attempts;\n" +
                    "  if (Too many failures?) then (yes)\n" +
                    "    :Lock account;\n" +
                    "    :Send unlock email;\n" +
                    "  else (no)\n" +
                    "    :Show error message;\n" +
                    "  endif\n" +
                    "endif\n" +
                    "stop\n" +
                    "@enduml"),

            Map.entry(DiagramType.STATE,
                    "@startuml\n" +
                    "[*] --> Pending\n" +
                    "Pending --> Processing : payment received\n" +
                    "Processing --> Shipped : items dispatched\n" +
                    "Shipped --> Delivered : package received\n" +
                    "Delivered --> [*]\n" +
                    "Pending --> Cancelled : order cancelled\n" +
                    "Processing --> Cancelled : payment failed\n" +
                    "Cancelled --> [*]\n" +
                    "@enduml"),

            Map.entry(DiagramType.COLLABORATION,
                    "@startuml\n" +
                    "object Browser\n" +
                    "object OrderController\n" +
                    "object InventoryService\n" +
                    "object PaymentService\n" +
                    "object NotificationService\n" +
                    "Browser -> OrderController : placeOrder()\n" +
                    "OrderController -> InventoryService : reserveItems()\n" +
                    "InventoryService --> OrderController : reservation OK\n" +
                    "OrderController -> PaymentService : chargeCustomer()\n" +
                    "PaymentService --> OrderController : transactionId\n" +
                    "OrderController -> NotificationService : sendConfirmation()\n" +
                    "OrderController --> Browser : orderConfirmed\n" +
                    "@enduml"),

            Map.entry(DiagramType.MICROSERVICES,
                    "@startuml\n" +
                    "rectangle \"[API Gateway]\" as gw\n" +
                    "rectangle \"[Auth Service]\" as auth\n" +
                    "rectangle \"[Product Service]\" as product\n" +
                    "rectangle \"[Order Service]\" as order\n" +
                    "rectangle \"[Payment Service]\" as payment\n" +
                    "queue \"[Message Broker]\" as broker\n" +
                    "rectangle \"[Notification Service]\" as notify\n" +
                    "gw --> auth : authenticate\n" +
                    "gw --> product : GET /products\n" +
                    "gw --> order : POST /orders\n" +
                    "order --> payment : processPayment\n" +
                    "auth ..> broker : userEvents\n" +
                    "order ..> broker : orderEvents\n" +
                    "broker ..> notify : consume\n" +
                    "@enduml")
    );

    /**
     * Alias map for frontend diagram type strings to backend {@link DiagramType} enum.
     * Keys are uppercase with spaces/dashes replaced by underscores.
     */
    private static final Map<String, DiagramType> DIAGRAM_TYPE_ALIASES = Map.ofEntries(
            Map.entry("CLASS", DiagramType.CLASS),
            Map.entry("CLASS_DIAGRAM", DiagramType.CLASS),
            Map.entry("SEQUENCE", DiagramType.SEQUENCE),
            Map.entry("SEQUENCE_DIAGRAM", DiagramType.SEQUENCE),
            Map.entry("ER", DiagramType.ER),
            Map.entry("ER_DIAGRAM", DiagramType.ER),
            Map.entry("ENTITY_RELATIONSHIP", DiagramType.ER),
            Map.entry("USE_CASE", DiagramType.USE_CASE),
            Map.entry("USE_CASE_DIAGRAM", DiagramType.USE_CASE),
            Map.entry("COMPONENT", DiagramType.COMPONENT),
            Map.entry("COMPONENT_DIAGRAM", DiagramType.COMPONENT),
            Map.entry("ARCHITECTURE", DiagramType.MICROSERVICES),
            Map.entry("ARCHITECTURE_DIAGRAM", DiagramType.MICROSERVICES),
            Map.entry("C4_CONTEXT", DiagramType.COMPONENT),
            Map.entry("C4_CONTEXT_DIAGRAM", DiagramType.COMPONENT),
            Map.entry("DEPLOYMENT", DiagramType.DEPLOYMENT),
            Map.entry("DEPLOYMENT_DIAGRAM", DiagramType.DEPLOYMENT),
            Map.entry("OBJECT", DiagramType.OBJECT),
            Map.entry("OBJECT_DIAGRAM", DiagramType.OBJECT),
            Map.entry("ACTIVITY", DiagramType.ACTIVITY),
            Map.entry("ACTIVITY_DIAGRAM", DiagramType.ACTIVITY),
            Map.entry("STATE", DiagramType.STATE),
            Map.entry("STATE_DIAGRAM", DiagramType.STATE),
            Map.entry("COLLABORATION", DiagramType.COLLABORATION),
            Map.entry("COLLABORATION_DIAGRAM", DiagramType.COLLABORATION),
            Map.entry("MICROSERVICES", DiagramType.MICROSERVICES),
            Map.entry("MICROSERVICES_DIAGRAM", DiagramType.MICROSERVICES),
            Map.entry("MICROSERVICES_ARCHITECTURE", DiagramType.MICROSERVICES),
            Map.entry("MICROSERVICES_ARCHITECTURE_DIAGRAM", DiagramType.MICROSERVICES)
    );

    private final DiagramSuggestionService suggestionService;
    private final SemanticExtractionService extractionService;
    private final StyleProfileService styleProfileService;
    private final PlantUmlGenerationService generationService;
    private final DiagramRenderingService renderingService;
    private final DomainDiagramRepository diagramRepository;
    private final AiModelService aiModelService;
    private final DiagramExplanationService explanationService;

    public ConfidenceDiagramServiceImpl(
            @Value("${diagram.classification.high:70}") int highConfidenceThreshold,
            @Value("${diagram.classification.medium:40}") int mediumConfidenceThreshold,
            DiagramSuggestionService suggestionService,
            SemanticExtractionService extractionService,
            StyleProfileService styleProfileService,
            PlantUmlGenerationService generationService,
            DiagramRenderingService renderingService,
            DomainDiagramRepository diagramRepository,
            AiModelService aiModelService,
            DiagramExplanationService explanationService) {
        this.highConfidenceThreshold = highConfidenceThreshold;
        this.mediumConfidenceThreshold = mediumConfidenceThreshold;
        this.suggestionService = suggestionService;
        this.extractionService = extractionService;
        this.styleProfileService = styleProfileService;
        this.generationService = generationService;
        this.renderingService = renderingService;
        this.diagramRepository = diagramRepository;
        this.aiModelService = aiModelService;
        this.explanationService = explanationService;
    }

    // ── Public entry point ───────────────────────────────────────────────────

    @Override
    public GenerationResult process(String text, String diagramType, Long seed, boolean forceGenerate) {
        logger.info("Processing diagram request (textLength={}, explicitType='{}', seed={}, forceGenerate={})",
                text != null ? text.length() : 0, diagramType, seed, forceGenerate);

        // If the user confirmed a SUGGEST (forceGenerate=true) or explicitly supplied a
        // diagram type, skip classification entirely.
        if (forceGenerate || (diagramType != null && !diagramType.isBlank())) {
            // Guard: forceGenerate=true with no diagramType is not meaningful
            if (diagramType == null || diagramType.isBlank()) {
                throw new InvalidDiagramRequestException(
                        "Diagram type must be specified when forceGenerate is true");
            }
            DiagramType mapped = mapToEnum(diagramType);
            logger.info("Normalized diagramType: '{}' → {}", diagramType, mapped);
            if (text == null || text.isBlank()) {
                // No description — render the built-in template for this type
                logger.info("Explicit type {} with no text — using default template", mapped);
                return generateFromTemplate(mapped, seed);
            }
            String normalizedText = text.trim();
            logger.info("Explicit diagram type mapped: {} → confidence treated as 100%{} | normalized text (first 100 chars): '{}'",
                    mapped,
                    forceGenerate ? " (forceGenerate)" : "",
                    normalizedText.substring(0, Math.min(100, normalizedText.length())));
            return generateWithFallback(normalizedText, mapped, 100, seed, null);
        }

        // For auto-detect mode, require meaningful text
        validateInput(text);

        // Classify and evaluate confidence
        DiagramSuggestion suggestion = suggestionService.suggest(text);

        // Apply ER boost before confidence branching
        suggestion = applyErBoostIfSignalled(text, suggestion);

        int confidence = suggestion.getConfidenceScore();
        DiagramType suggestedType = suggestion.getSuggestedDiagramType();

        logger.info("Classification result: type={}, confidence={}, source={}",
                suggestedType, confidence, suggestion.getSource());

        if (confidence >= highConfidenceThreshold) {
            // HIGH — auto-generate
            logger.info("High confidence ({}%) — proceeding with automatic generation", confidence);
            return generateWithFallback(text, suggestedType, confidence, seed, suggestion);

        } else if (confidence >= mediumConfidenceThreshold) {
            // MEDIUM — return suggestion for user confirmation
            logger.info("Medium confidence ({}%) — returning suggestion for confirmation", confidence);
            return buildSuggestionResult(suggestedType, confidence, suggestion);

        } else {
            // LOW — check for structural signals before rejecting
            if (hasStructuralSignals(text)) {
                int promotedConfidence = Math.max(confidence, mediumConfidenceThreshold);
                logger.info("Low confidence ({}%) but structural signals detected — promoting to SUGGEST ({}%)",
                        confidence, promotedConfidence);
                return buildSuggestionResult(suggestedType, promotedConfidence, suggestion);
            }
            logger.warn("Low confidence ({}%) — requesting more detailed input", confidence);
            return buildLowConfidenceResult(suggestedType, confidence, suggestion);
        }
    }

    // ── Full generation pipeline ─────────────────────────────────────────────

    /**
     * Renders a built-in default template for the given type without running
     * semantic extraction or classification.  Used when the user picks a type
     * manually but supplies no description text.
     */
    private GenerationResult generateFromTemplate(DiagramType diagramType, Long seed) {
        String plantUmlCode = DEFAULT_TEMPLATES.getOrDefault(diagramType,
                "@startuml\nnote : No template for " + diagramType.name() + "\n@enduml");

        logger.debug("Using default template for {} ({} chars)", diagramType, plantUmlCode.length());

        String pngBase64 = null;
        String svgContent = null;
        try {
            byte[] pngBytes = renderingService.renderToPng(plantUmlCode);
            pngBase64 = Base64.getEncoder().encodeToString(pngBytes);
            byte[] svgBytes = renderingService.renderToSvg(plantUmlCode);
            svgContent = new String(svgBytes, StandardCharsets.UTF_8);
        } catch (DiagramRenderingException e) {
            logger.warn("Template rendering failed: {}", e.getMessage());
        }

        Diagram diagram = new Diagram("Default template for " + diagramType.getDisplayName(), diagramType, plantUmlCode);
        diagram.setModelUsed(aiModelService.getModelName());
        diagram = diagramRepository.save(diagram);
        logger.info("Saved template diagram with ID: {}", diagram.getId());

        return GenerationResult.builder()
                .id(diagram.getId())
                .diagramType(diagramType)
                .plantUmlCode(plantUmlCode)
                .pngBase64(pngBase64)
                .svgContent(svgContent)
                .entityCount(0)
                .relationshipCount(0)
                .actionCount(0)
                .modelUsed(diagram.getModelUsed())
                .generatedAt(diagram.getCreatedAt())
                .confidenceScore(100)
                .confirmationRequired(false)
                .explanation(null)
                .generationMode("TEMPLATE")
                .decision("AUTO")
                .message("Default template for " + diagramType.getDisplayName())
                .build();
    }

    /**
     * Executes the full generation pipeline: extract → style → generate → render → persist.
     *
     * @param text           the input description
     * @param diagramType    the resolved diagram type
     * @param confidence     the classification confidence (for the response)
     * @param seed           optional deterministic seed
     * @return a fully populated {@link GenerationResult}
     */
    private GenerationResult generateDiagram(String text, DiagramType diagramType,
                                             int confidence, Long seed,
                                             DiagramSuggestion suggestion) {
        // Step 1: Extract semantic model (type-aware prompt)
        SemanticModel semanticModel = extractionService.extract(text, diagramType);
        logger.info("Extracted semantic model: {} entities, {} relationships, {} actions",
                semanticModel.getEntityCount(),
                semanticModel.getRelationshipCount(),
                semanticModel.getActionCount());

        // Step 2: Get style profile
        StyleProfile styleProfile = styleProfileService.getStyleProfile(diagramType);
        logger.debug("Style profile: layout={}, arrows={}, spacing={}",
                styleProfile.getLayoutDirection(), styleProfile.getArrowStyle(),
                styleProfile.getSpacingRule());

        // Step 3: Generate PlantUML code
        logger.info("Dispatching generation for diagram type: {}", diagramType);
        String plantUmlCode = generationService.generate(semanticModel, styleProfile, seed);

        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            logger.error("Generation returned null/empty PlantUML for type: {}", diagramType);
            throw new DiagramGenerationException(
                    "Generation produced no output for diagram type: " + diagramType);
        }

        logger.debug("Generated PlantUML code ({} chars, seed: {})", plantUmlCode.length(), seed);

        // Step 4: Render to PNG and SVG
        String pngBase64 = null;
        String svgContent = null;
        try {
            byte[] pngBytes = renderingService.renderToPng(plantUmlCode);
            pngBase64 = Base64.getEncoder().encodeToString(pngBytes);
            logger.debug("Rendered PNG ({} bytes)", pngBytes.length);

            byte[] svgBytes = renderingService.renderToSvg(plantUmlCode);
            svgContent = new String(svgBytes, StandardCharsets.UTF_8);
            logger.debug("Rendered SVG ({} bytes)", svgBytes.length);
        } catch (DiagramRenderingException e) {
            logger.warn("Rendering failed, returning PlantUML code only: {}", e.getMessage());
        }

        // Step 5: Persist to database
        Diagram diagram = new Diagram(text, diagramType, plantUmlCode);
        diagram.setModelUsed(aiModelService.getModelName());
        diagram = diagramRepository.save(diagram);
        logger.info("Saved diagram with ID: {} (model: {})", diagram.getId(), diagram.getModelUsed());

        // Build explanation
        DiagramExplanation explanation = explanationService.explain(
                diagramType, confidence, semanticModel, suggestion);

        String generationMode = (suggestion != null &&
                suggestion.getSource() == ClassificationSource.AI_PROVIDER) ? "LLM" : "RULE_BASED";

        // Build the full response
        return GenerationResult.builder()
                .id(diagram.getId())
                .diagramType(diagramType)
                .plantUmlCode(plantUmlCode)
                .pngBase64(pngBase64)
                .svgContent(svgContent)
                .entityCount(semanticModel.getEntityCount())
                .relationshipCount(semanticModel.getRelationshipCount())
                .actionCount(semanticModel.getActionCount())
                .modelUsed(diagram.getModelUsed())
                .generatedAt(diagram.getCreatedAt())
                .confidenceScore(confidence)
                .confirmationRequired(false)
                .explanation(explanation)
                .generationMode(generationMode)
                .decision("AUTO")
                .message("Diagram generated successfully")
                .build();
    }

    // ── Suggestion / low-confidence responses ────────────────────────────────

    /**
     * Attempts full pipeline generation; on any failure falls back to the
     * built-in default template for the resolved diagram type.
     *
     * <p>Fallback triggers when {@link #generateDiagram} throws any exception
     * (e.g. AI model unavailable, rendering failure, empty output).  The
     * returned result has {@code generationMode = "TEMPLATE_FALLBACK"} and a
     * message that tells the user generation failed and a default diagram is
     * shown instead.</p>
     */
    private GenerationResult generateWithFallback(String text, DiagramType diagramType,
                                                  int confidence, Long seed,
                                                  DiagramSuggestion suggestion) {
        try {
            return generateDiagram(text, diagramType, confidence, seed, suggestion);
        } catch (Exception e) {
            logger.warn("Generation pipeline failed for type={} — falling back to default template: {}",
                    diagramType, e.getMessage());
            GenerationResult fallback = generateFromTemplate(diagramType, seed);
            fallback.setGenerationMode("TEMPLATE_FALLBACK");
            fallback.setMessage("Diagram generation failed; showing a default " +
                    diagramType.getDisplayName() + " template instead.");
            return fallback;
        }
    }

    // ── Suggestion / low-confidence responses ────────────────────────────────

    /**
     * Builds a suggestion result for medium-confidence classifications (40–69%).
     * The client should present this to the user for confirmation before regenerating.
     */
    private GenerationResult buildSuggestionResult(DiagramType suggestedType,
                                                   int confidence,
                                                   DiagramSuggestion suggestion) {
        String reasoning = suggestion.getReasoningMessage();
        String displayName = suggestedType.getDisplayName();
        String message = (reasoning != null && !reasoning.isBlank())
                ? reasoning
                : "Your description appears to describe interactions. A " + displayName +
                  " may be appropriate. Do you want to proceed?";

        DiagramExplanation explanation = explanationService.explain(
                suggestedType, confidence, null, suggestion);

        return GenerationResult.builder()
                .diagramType(suggestedType)
                .confidenceScore(confidence)
                .confirmationRequired(true)
                .explanation(explanation)
                .decision("SUGGEST")
                .message(message)
                .build();
    }

    /**
     * Builds a low-confidence result (&lt; 40%) asking the user for more detail.
     */
    private GenerationResult buildLowConfidenceResult(DiagramType suggestedType,
                                                      int confidence,
                                                      DiagramSuggestion suggestion) {
        DiagramExplanation explanation = explanationService.explain(
                suggestedType, confidence, null, suggestion);

        return GenerationResult.builder()
                .diagramType(suggestedType)
                .confidenceScore(confidence)
                .confirmationRequired(true)
                .explanation(explanation)
                .decision("REJECT")
                .message("Please provide more detailed system description.")
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * If the input contains strong ER signals (a phrase from {@link #ER_SIGNAL_PHRASES}
     * plus at least two comma-separated tokens), returns a new {@link DiagramSuggestion}
     * with type overridden to {@link DiagramType#ER} and confidence boosted by
     * {@link #ER_CONFIDENCE_BOOST}, clamped to 100.
     * The resulting confidence is also clamped to at least {@link #MEDIUM_CONFIDENCE_THRESHOLD}
     * so the decision is never REJECT when ER signals are present.
     *
     * @param text       the raw input
     * @param suggestion the suggestion produced by the classification layers
     * @return the original suggestion if no ER signals, otherwise a boosted ER suggestion
     */
    private DiagramSuggestion applyErBoostIfSignalled(String text, DiagramSuggestion suggestion) {
        if (!hasErSignals(text)) return suggestion;

        int boosted = Math.min(100, Math.max(mediumConfidenceThreshold,
                suggestion.getConfidenceScore() + ER_CONFIDENCE_BOOST));
        logger.info("ER signals detected — boosting confidence from {}% to {}%, type overridden to ER",
                suggestion.getConfidenceScore(), boosted);

        return new DiagramSuggestion(
                DiagramType.ER,
                boosted,
                "ER-specific patterns detected (e.g. 'has many', 'belongs to', 'primary key'). " +
                        "An Entity-Relationship Diagram is recommended.",
                DiagramSuggestion.ClassificationSource.SEMANTIC_PATTERN
        );
    }

    /**
     * Returns {@code true} when the input contains at least one ER signal phrase
     * from {@link #ER_SIGNAL_PHRASES} and at least two comma-separated tokens,
     * indicating multiple entities are listed.
     *
     * @param text the raw input description
     * @return {@code true} if ER signals are present
     */
    private boolean hasErSignals(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();

        boolean hasPhrase = ER_SIGNAL_PHRASES.stream().anyMatch(lower::contains);
        if (!hasPhrase) return false;

        // At least two comma-separated segments means multiple entities are named
        String[] commaParts = lower.split(",");
        return commaParts.length >= 2;
    }

    /**
     * Returns {@code true} when the input contains at least two entity-like nouns
     * and at least one structural relationship word from {@link #STRUCTURAL_RELATIONSHIP_WORDS}.
     *
     * <p>A token is counted as an entity noun when it has three or more characters and
     * is neither a stop word nor a relationship word.
     *
     * @param text the raw input description
     * @return {@code true} if the structural signal threshold is met
     */
    private boolean hasStructuralSignals(String text) {
        if (text == null || text.isBlank()) return false;

        String lower = text.toLowerCase();

        // Check for at least one relationship word
        boolean hasRelationship = Arrays.stream(lower.split("\\W+"))
                .anyMatch(STRUCTURAL_RELATIONSHIP_WORDS::contains);
        if (!hasRelationship) return false;

        // Count noun-like tokens: length ≥ 3, not a stop word, not a relationship word
        long entityCount = Arrays.stream(lower.split("\\W+"))
                .filter(w -> w.length() >= 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .filter(w -> !STRUCTURAL_RELATIONSHIP_WORDS.contains(w))
                .distinct()
                .count();

        return entityCount >= 2;
    }

    /**
     * Maps a frontend-supplied diagram type string to the backend {@link DiagramType} enum.
     * Normalizes input (trim, uppercase, replace spaces/dashes with underscores) and
     * checks the alias map, {@code valueOf}, and {@code fromCode} in order.
     *
     * @param input the raw diagram type string
     * @return the matched {@link DiagramType}
     * @throws InvalidDiagramRequestException if no match is found
     */
    private DiagramType mapToEnum(String input) {
        String normalized = input.trim().toUpperCase().replaceAll("[\\s-]+", "_");
        logger.debug("Normalizing diagram type input '{}' → '{}'", input, normalized);

        // 1. Alias map
        DiagramType mapped = DIAGRAM_TYPE_ALIASES.get(normalized);
        if (mapped != null) {
            return mapped;
        }

        // 2. Direct enum valueOf
        try {
            return DiagramType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // continue
        }

        // 3. fromCode lookup
        try {
            return DiagramType.fromCode(normalized.toLowerCase());
        } catch (IllegalArgumentException ignored) {
            // continue
        }

        throw new InvalidDiagramRequestException("Unsupported diagram type: " + input);
    }

    /**
     * Validates the input text.
     *
     * @param text the user-supplied description
     * @throws InvalidDiagramRequestException if text is null, blank, or too short
     */
    private void validateInput(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidDiagramRequestException("Input text cannot be null or blank");
        }
        if (text.trim().length() < 10) {
            throw new InvalidDiagramRequestException(
                    "Input text is too short (minimum 10 characters). " +
                    "Please provide a more detailed system description.");
        }
    }
}
