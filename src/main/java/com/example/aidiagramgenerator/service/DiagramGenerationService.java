package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.LlmResult;
import com.example.aidiagramgenerator.enums.DiagramType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service that generates Mermaid diagram code from natural language text.
 *
 * <p>Primary: LLM-based generation via {@link AiModelService}.
 * <br>Fallback: keyword-based rule engine (always succeeds).
 *
 * <p>The service guarantees that at least one entity is present in every
 * generated diagram — if no keywords are detected, sensible defaults
 * (User + Service) are applied.</p>
 */
@Service
public class DiagramGenerationService {

    private final MermaidValidator mermaidValidator;
    private final AiModelService aiModelService;

    public DiagramGenerationService(MermaidValidator mermaidValidator, AiModelService aiModelService) {
        this.mermaidValidator = mermaidValidator;
        this.aiModelService = aiModelService;
    }

    private static final Logger logger = LoggerFactory.getLogger(DiagramGenerationService.class);

    private static final Set<String> SEQUENCE_KEYWORDS = Set.of("login", "auth", "register", "authenticate", "sign in", "sign up");
    private static final Set<String> NODE_KEYWORDS_USER = Set.of("user", "client", "customer", "actor");
    private static final Set<String> NODE_KEYWORDS_SERVICE = Set.of("service", "api", "server", "backend", "controller");
    private static final Set<String> NODE_KEYWORDS_DATABASE = Set.of("database", "db", "repository", "storage", "persist");

    /**
     * Internal result class holding generation output with explainability trace.
     */
    public static class DiagramResult {
        private final DiagramType diagramType;
        private final String mermaidCode;
        private final String explanation;
        private final List<String> detectedKeywords;
        private final List<String> rulesTriggered;
        private final String generationMode;

        public DiagramResult(DiagramType diagramType, String mermaidCode, String explanation,
                            List<String> detectedKeywords, List<String> rulesTriggered) {
            this(diagramType, mermaidCode, explanation, detectedKeywords, rulesTriggered, null);
        }

        public DiagramResult(DiagramType diagramType, String mermaidCode, String explanation,
                            List<String> detectedKeywords, List<String> rulesTriggered, String generationMode) {
            this.diagramType = diagramType;
            this.mermaidCode = mermaidCode;
            this.explanation = explanation;
            this.detectedKeywords = detectedKeywords;
            this.rulesTriggered = rulesTriggered;
            this.generationMode = generationMode;
        }

        public DiagramType getDiagramType() { return diagramType; }
        public String getMermaidCode() { return mermaidCode; }
        public String getExplanation() { return explanation; }
        public List<String> getDetectedKeywords() { return detectedKeywords; }
        public List<String> getRulesTriggered() { return rulesTriggered; }
        public String getGenerationMode() { return generationMode; }
    }

    /**
     * Generate a Mermaid diagram from natural language text.
     *
     * <p>Attempts LLM-based generation first. If the LLM response is empty or
     * the call fails, falls back to the rule-based generator (logged as a
     * fallback event). The result is guaranteed to contain at least one entity.
     *
     * @param text          the user's natural language description
     * @param requestedType optional diagram type; if null, auto-detected from keywords
     * @return a {@link DiagramResult} with type, Mermaid code, and explanation
     */
    public DiagramResult generateFromText(String text, DiagramType requestedType) {
        logger.info("DiagramGenerationService.generateFromText — requestedType={}, textIsBlank={}, textLength={}, text='{}'",
                requestedType,
                text == null || text.isBlank(),
                text != null ? text.length() : 0,
                text);

        // --- Try LLM generation first ---
        try {
            DiagramResult llmResult = generateViaLlm(text, requestedType);
            if (llmResult != null) {
                logger.info("GENERATOR_SELECTED=LLM — requestedType={}", requestedType);
                return llmResult;
            }
        } catch (Exception e) {
            logger.warn("LLM_FALLBACK_TRIGGERED - using rule-based generation: {}", e.getMessage());
        }

        // --- Fallback: rule-based generation ---
        logger.warn("LLM_FALLBACK_TRIGGERED - using rule-based generation");
        logger.info("GENERATOR_SELECTED=RULE_BASED — requestedType={}", requestedType);
        return generateRuleBased(text, requestedType);
    }

    /**
     * Attempts to generate a Mermaid diagram via the configured AI model.
     *
     * @return a valid {@link DiagramResult}, or {@code null} if the response is
     *         empty / not valid Mermaid syntax
     */
    private DiagramResult generateViaLlm(String text, DiagramType requestedType) {
        String typeHint = requestedType != null
                ? " Generate a " + requestedType.getValue() + " diagram."
                : " Auto-detect the most appropriate diagram type.";

        String prompt = "Generate valid Mermaid diagram code for the following description." + typeHint +
                " Respond ONLY with the raw Mermaid code, no markdown fences, no explanation.\n\n" + text;

        LlmResult llmResult = aiModelService.callLLM(prompt);

        if (!llmResult.isSuccess()) {
            logger.warn("LLM_FALLBACK_TRIGGERED - using rule-based generation");
            return null;
        }

        String llmContent = llmResult.getContent();
        logger.info("LLM_USED - AI generated diagram ({} chars)", llmContent.length());

        // Strip markdown fences if the LLM wraps its output
        String mermaidCode = llmContent.strip();
        if (mermaidCode.startsWith("```")) {
            mermaidCode = mermaidCode.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }

        if (mermaidCode.isBlank()) {
            logger.warn("LLM_FALLBACK_TRIGGERED - LLM content was blank after stripping fences");
            return null;
        }

        // Validate before returning
        mermaidValidator.validate(mermaidCode);

        DiagramType diagramType = requestedType != null ? requestedType : detectTypeFromMermaid(mermaidCode);

        logger.info("LLM generated {} diagram ({} chars)", diagramType, mermaidCode.length());
        return new DiagramResult(
                diagramType,
                mermaidCode,
                "Generated via AI model: " + aiModelService.getModelName(),
                List.of(),
                List.of("LLM_GENERATION: " + aiModelService.getModelName()),
                "LLM");
    }

    /**
     * Infers the {@link DiagramType} from the first token of Mermaid code.
     */
    private DiagramType detectTypeFromMermaid(String mermaidCode) {
        String first = mermaidCode.split("\\s+", 2)[0].toLowerCase();
        return switch (first) {
            case "sequencediagram" -> DiagramType.SEQUENCE;
            case "erdiagram"      -> DiagramType.ER;
            case "c4context", "c4container", "c4component" -> DiagramType.C4;
            default               -> DiagramType.CLASS;
        };
    }

    /**
     * Rule-based Mermaid diagram generator (the original keyword engine).
     * Guarantees at least one entity by defaulting to User + Service when no
     * keywords are detected.
     */
    DiagramResult generateRuleBased(String text, DiagramType requestedType) {
        String lower = text.toLowerCase();
        List<String> detectedKeywords = new ArrayList<>();

        // --- Detect keywords ---
        boolean hasUser = containsAny(lower, NODE_KEYWORDS_USER, detectedKeywords);
        boolean hasService = containsAny(lower, NODE_KEYWORDS_SERVICE, detectedKeywords);
        boolean hasDatabase = containsAny(lower, NODE_KEYWORDS_DATABASE, detectedKeywords);
        boolean hasSequenceTrigger = containsAny(lower, SEQUENCE_KEYWORDS, detectedKeywords);

        // Supply sensible defaults when text is vague
        if (!hasUser && !hasService && !hasDatabase) {
            hasUser = true;
            hasService = true;
        }

        // --- Determine diagram type ---
        DiagramType diagramType;
        String typeReason;
        List<String> rulesTriggered = new ArrayList<>();

        if (requestedType != null) {
            diagramType = requestedType;
            typeReason = "Diagram type explicitly requested: " + requestedType;
            rulesTriggered.add("EXPLICIT_TYPE_REQUEST: " + requestedType);
        } else if (hasSequenceTrigger) {
            diagramType = DiagramType.SEQUENCE;
            typeReason = "Auto-detected SEQUENCE because keywords suggest an interaction flow";
            rulesTriggered.add("SEQUENCE_KEYWORD_MATCH: detected interaction keywords");
        } else {
            diagramType = DiagramType.CLASS;
            typeReason = "Auto-detected CLASS as the default structural diagram";
            rulesTriggered.add("DEFAULT_FALLBACK: no specific keywords matched, using CLASS");
        }

        // Track node detection rules
        if (hasUser) {
            rulesTriggered.add("NODE_USER_DETECTED: matched user/client/customer/actor keyword");
        }
        if (hasService) {
            rulesTriggered.add("NODE_SERVICE_DETECTED: matched service/api/server/backend/controller keyword");
        }
        if (hasDatabase) {
            rulesTriggered.add("NODE_DATABASE_DETECTED: matched database/db/repository/storage/persist keyword");
        }
        if (!hasUser && !hasService && !hasDatabase) {
            rulesTriggered.add("DEFAULT_NODES_APPLIED: no nodes detected, using User and Service defaults");
        }

        // --- Generate Mermaid code ---
        String mermaidCode = switch (diagramType) {
            case SEQUENCE      -> generateSequenceDiagram(hasUser, hasService, hasDatabase);
            case CLASS         -> generateClassDiagram(hasUser, hasService, hasDatabase);
            case ER            -> generateErDiagram(hasUser, hasService, hasDatabase);
            case USE_CASE      -> generateUseCaseDiagram(hasUser, hasService);
            case ARCHITECTURE  -> generateArchitectureDiagram(hasUser, hasService, hasDatabase);
            case C4            -> generateC4Diagram(hasUser, hasService, hasDatabase);
            case ACTIVITY      -> generateActivityDiagram();
            case STATE         -> generateStateDiagram();
            case OBJECT          -> generateObjectDiagram();
            case MICROSERVICES   -> generateMicroservicesDiagram();
            case COLLABORATION   -> generateClassDiagram(hasUser, hasService, hasDatabase);
            case COMPONENT       -> generateComponentDiagram();
            case DEPLOYMENT      -> generateDeploymentDiagram();
        };

        // --- Build explanation ---
        String explanation = String.format(
                "Detected keywords: %s. %s. Nodes included: %s.",
                detectedKeywords.isEmpty() ? "none" : String.join(", ", detectedKeywords),
                typeReason,
                buildNodeList(hasUser, hasService, hasDatabase)
        );

        // Validate the generated Mermaid code before returning
        mermaidValidator.validate(mermaidCode);

        logger.info("Generated {} diagram. {}", diagramType, explanation);
        return new DiagramResult(diagramType, mermaidCode, explanation,
                List.copyOf(detectedKeywords), List.copyOf(rulesTriggered), "RULE_BASED");
    }

    // ---- Diagram generators (simple templates) ----

    private String generateSequenceDiagram(boolean hasUser, boolean hasService, boolean hasDatabase) {
        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        if (hasUser) sb.append("    participant User\n");
        if (hasService) sb.append("    participant Service\n");
        if (hasDatabase) sb.append("    participant Database\n");

        if (hasUser && hasService) {
            sb.append("    User->>Service: request\n");
            sb.append("    Service-->>User: response\n");
        }
        if (hasService && hasDatabase) {
            sb.append("    Service->>Database: query\n");
            sb.append("    Database-->>Service: result\n");
        }
        return sb.toString().stripTrailing();
    }

    private String generateClassDiagram(boolean hasUser, boolean hasService, boolean hasDatabase) {
        StringBuilder sb = new StringBuilder("classDiagram\n");
        if (hasUser) {
            sb.append("    class User {\n");
            sb.append("        +String name\n");
            sb.append("        +String email\n");
            sb.append("    }\n");
        }
        if (hasService) {
            sb.append("    class Service {\n");
            sb.append("        +handleRequest()\n");
            sb.append("        +processData()\n");
            sb.append("    }\n");
        }
        if (hasDatabase) {
            sb.append("    class Database {\n");
            sb.append("        +query()\n");
            sb.append("        +save()\n");
            sb.append("    }\n");
        }
        if (hasUser && hasService) sb.append("    User --> Service\n");
        if (hasService && hasDatabase) sb.append("    Service --> Database\n");
        return sb.toString().stripTrailing();
    }

    private String generateErDiagram(boolean hasUser, boolean hasService, boolean hasDatabase) {
        StringBuilder sb = new StringBuilder("erDiagram\n");
        if (hasUser) {
            sb.append("    USER {\n        string id\n        string name\n        string email\n    }\n");
        }
        if (hasService) {
            sb.append("    SERVICE {\n        string id\n        string endpoint\n    }\n");
        }
        if (hasDatabase) {
            sb.append("    DATABASE {\n        string id\n        string connectionUrl\n    }\n");
        }
        if (hasUser && hasService) sb.append("    USER ||--o{ SERVICE : uses\n");
        if (hasService && hasDatabase) sb.append("    SERVICE ||--o{ DATABASE : queries\n");
        return sb.toString().stripTrailing();
    }

    private String generateUseCaseDiagram(boolean hasUser, boolean hasService) {
        StringBuilder sb = new StringBuilder("graph LR\n");
        sb.append("    User[User]\n");
        sb.append("    Login((Login))\n");
        sb.append("    ViewInfo((View Information))\n");
        sb.append("    User --> Login\n");
        sb.append("    User --> ViewInfo\n");
        if (hasService) {
            sb.append("    ManageService((Manage Service))\n");
            sb.append("    User --> ManageService\n");
        }
        return sb.toString().stripTrailing();
    }

    private String generateArchitectureDiagram(boolean hasUser, boolean hasService, boolean hasDatabase) {
        StringBuilder sb = new StringBuilder("graph TD\n");
        if (hasUser) sb.append("    User[User]\n");
        if (hasService) sb.append("    Service[Service / API]\n");
        if (hasDatabase) sb.append("    Database[(Database)]\n");
        if (hasUser && hasService) sb.append("    User --> Service\n");
        if (hasService && hasDatabase) sb.append("    Service --> Database\n");
        return sb.toString().stripTrailing();
    }

    private String generateC4Diagram(boolean hasUser, boolean hasService, boolean hasDatabase) {
        StringBuilder sb = new StringBuilder("C4Context\n");
        sb.append("    title System Context Diagram\n\n");
        if (hasUser) sb.append("    Person(user, \"User\", \"End user\")\n");
        if (hasService) sb.append("    System(service, \"Service\", \"Core API\")\n");
        if (hasDatabase) sb.append("    SystemDb(db, \"Database\", \"Data store\")\n\n");
        if (hasUser && hasService) sb.append("    Rel(user, service, \"Uses\")\n");
        if (hasService && hasDatabase) sb.append("    Rel(service, db, \"Reads/Writes\")\n");
        return sb.toString().stripTrailing();
    }

    private String generateComponentDiagram() {
        return "@startuml\n" +
               "component \"Client\"\n" +
               "component \"Service\"\n" +
               "database \"Database\"\n" +
               "\n" +
               "\"Client\" --> \"Service\" : HTTP\n" +
               "\"Service\" --> \"Database\" : JDBC\n" +
               "@enduml";
    }

    private String generateDeploymentDiagram() {
        return "@startuml\n" +
               "node \"Client\" {\n" +
               "  artifact \"Browser\"\n" +
               "}\n" +
               "node \"App Server\" {\n" +
               "  artifact \"Application\"\n" +
               "}\n" +
               "database \"Database\"\n" +
               "\n" +
               "\"Client\" --> \"App Server\" : HTTPS\n" +
               "\"App Server\" --> \"Database\" : JDBC\n" +
               "@enduml";
    }

    // ---- Utility methods ----

    private String generateObjectDiagram() {
        return "@startuml\n" +
               "object User1\n" +
               "object Order1\n" +
               "object Product1\n" +
               "\n" +
               "User1 --> Order1\n" +
               "Order1 --> Product1\n" +
               "@enduml";
    }

    private String generateMicroservicesDiagram() {
        return "@startuml\n" +
               "rectangle \"[API Gateway]\" as APIGateway\n" +
               "rectangle \"[Auth Service]\" as AuthService\n" +
               "rectangle \"[Order Service]\" as OrderService\n" +
               "rectangle \"[User Service]\" as UserService\n" +
               "\n" +
               "APIGateway --> AuthService : authenticate\n" +
               "APIGateway --> OrderService : route\n" +
               "OrderService --> UserService : user info\n" +
               "@enduml";
    }

    private String generateStateDiagram() {
        return "@startuml\n" +
               "[*] --> Idle\n" +
               "Idle --> Processing : start\n" +
               "Processing --> Completed : finish\n" +
               "Processing --> Failed : error\n" +
               "Completed --> [*]\n" +
               "Failed --> [*]\n" +
               "@enduml";
    }

    private String generateActivityDiagram() {
        return "@startuml\n" +
               "start\n" +
               ":Initialize;\n" +
               "--> :Process request;\n" +
               "--> :Validate input;\n" +
               "--> :Execute operation;\n" +
               "--> :Return result;\n" +
               "stop\n" +
               "@enduml";
    }

    private boolean containsAny(String text, Set<String> keywords, List<String> matched) {
        boolean found = false;
        for (String kw : keywords) {
            if (text.contains(kw)) {
                matched.add(kw);
                found = true;
            }
        }
        return found;
    }

    private String buildNodeList(boolean hasUser, boolean hasService, boolean hasDatabase) {
        List<String> nodes = new ArrayList<>();
        if (hasUser) nodes.add("User");
        if (hasService) nodes.add("Service");
        if (hasDatabase) nodes.add("Database");
        return nodes.isEmpty() ? "none" : String.join(", ", nodes);
    }
}
