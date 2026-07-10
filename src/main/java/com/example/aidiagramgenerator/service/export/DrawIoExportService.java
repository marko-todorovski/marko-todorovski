package com.example.aidiagramgenerator.service.export;

import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for exporting diagrams to Draw.io XML format.
 * Converts Mermaid diagram code to Draw.io compatible XML.
 */
@Service
public class DrawIoExportService {

    private static final Logger logger = LoggerFactory.getLogger(DrawIoExportService.class);

    private final DiagramRepository diagramRepository;

    public DrawIoExportService(DiagramRepository diagramRepository) {
        this.diagramRepository = diagramRepository;
    }

    /**
     * Exports a diagram to Draw.io XML format.
     *
     * @param diagramId the ID of the diagram to export
     * @return Draw.io compatible XML string
     * @throws DiagramNotFoundException if the diagram is not found
     */
    public String exportToDrawIoXml(UUID diagramId) {
        Diagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found with ID: " + diagramId));

        return convertToDrawIoXml(diagram);
    }

    /**
     * Converts a diagram entity to Draw.io XML.
     *
     * @param diagram the diagram entity
     * @return Draw.io compatible XML string
     */
    public String convertToDrawIoXml(Diagram diagram) {
        String mermaidCode = diagram.getMermaidCode();
        DiagramType diagramType = diagram.getDiagramType();

        if (mermaidCode == null || mermaidCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Diagram " + diagram.getId() + " has no Mermaid code to convert");
        }

        logger.info("Converting {} diagram to Draw.io XML (codeLength={})", diagramType, mermaidCode.length());
        logger.debug("Mermaid code: {}", mermaidCode);

        DrawIoXmlBuilder builder = new DrawIoXmlBuilder();

        try {
            // Set appropriate layout strategy based on diagram type
            switch (diagramType) {
                case SEQUENCE -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HORIZONTAL);
                    parseSequenceDiagram(mermaidCode, builder);
                }
                case CLASS -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HIERARCHICAL);
                    parseClassDiagram(mermaidCode, builder);
                }
                case ER -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.GRID);
                    builder.setNodesPerRow(3);
                    parseErDiagram(mermaidCode, builder);
                }
                case ARCHITECTURE -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HIERARCHICAL);
                    parseArchitectureDiagram(mermaidCode, builder);
                }
                case C4 -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HIERARCHICAL);
                    parseC4Diagram(mermaidCode, builder);
                }
                default -> {
                    builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.GRID);
                    parseGenericDiagram(mermaidCode, builder);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse " + diagramType + " Mermaid code for diagram " + diagram.getId() + ": " + e.getMessage(), e);
        }

        logger.info("Draw.io XML built: {} nodes, {} edges", builder.getNodeCount(), builder.getEdgeCount());
        if (builder.getNodeCount() == 0) {
            logger.warn("No nodes parsed from {} diagram {} — XML will be structurally valid but empty", diagramType, diagram.getId());
        }
        return builder.build();
    }

    /**
     * Parses Mermaid sequence diagram and builds Draw.io nodes/edges.
     */
    private void parseSequenceDiagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseSequenceDiagram] input {} chars", mermaidCode.length());
        // Parse participants - auto-layout will position them horizontally
        Pattern participantPattern = Pattern.compile("participant\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher participantMatcher = participantPattern.matcher(mermaidCode);

        int participantCount = 0;
        while (participantMatcher.find()) {
            builder.addNode(participantMatcher.group(1));
            participantCount++;
        }
        logger.debug("[parseSequenceDiagram] participant pattern matched {} nodes", participantCount);

        // Parse message flows (arrows)
        Pattern arrowPattern = Pattern.compile("(\\w+)\\s*(->>|-->>|->|-->)\\s*(\\w+)\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher arrowMatcher = arrowPattern.matcher(mermaidCode);

        int arrowCount = 0;
        while (arrowMatcher.find()) {
            String source = arrowMatcher.group(1);
            String target = arrowMatcher.group(3);
            String label = arrowMatcher.group(4).trim();
            builder.addEdge(source, target, label);
            arrowCount++;
        }
        logger.debug("[parseSequenceDiagram] arrow pattern matched {} edges", arrowCount);
    }

    /**
     * Parses Mermaid class diagram and builds Draw.io nodes/edges.
     */
    private void parseClassDiagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseClassDiagram] input {} chars", mermaidCode.length());
        // Parse classes with their attributes/methods
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)\\s*\\{([^}]*)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher classMatcher = classPattern.matcher(mermaidCode);

        int classCount = 0;
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String body = classMatcher.group(2);
            List<String> attributes = parseClassBody(body);
            builder.addNode(className, attributes);
            classCount++;
        }
        logger.debug("[parseClassDiagram] class-with-body pattern matched {} nodes", classCount);

        // Parse standalone class names (without body)
        Pattern standaloneClassPattern = Pattern.compile("class\\s+(\\w+)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
        Matcher standaloneMatcher = standaloneClassPattern.matcher(mermaidCode);

        int standaloneCount = 0;
        while (standaloneMatcher.find()) {
            String className = standaloneMatcher.group(1);
            // Check if already added (has body)
            if (!mermaidCode.contains("class " + className + " {")) {
                builder.addNode(className);
                standaloneCount++;
            }
        }
        logger.debug("[parseClassDiagram] standalone class pattern matched {} new nodes", standaloneCount);

        // Parse relationships
        int edgesBefore = builder.getEdgeCount();
        parseClassRelationships(mermaidCode, builder);
        logger.debug("[parseClassDiagram] relationships: {} edges added", builder.getEdgeCount() - edgesBefore);
    }

    /**
     * Parses class body (attributes and methods).
     */
    private List<String> parseClassBody(String body) {
        List<String> attributes = new ArrayList<>();
        String[] lines = body.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                attributes.add(line);
            }
        }
        
        return attributes;
    }

    /**
     * Parses class diagram relationships.
     */
    private void parseClassRelationships(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseClassRelationships] scanning {} chars", mermaidCode.length());
        // Mermaid class relationships: A --> B, A --|> B, A ..> B, etc.
        Pattern relationPattern = Pattern.compile("(\\w+)\\s*(-->|<--|--|\\.\\.>|<\\.\\.|\\*--|--\\*|o--|--o|\\|>|<\\||--\\|>|<\\|--|\\.\\.)\\s*(\\w+)(?:\\s*:\\s*(.+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher relationMatcher = relationPattern.matcher(mermaidCode);

        int count = 0;
        while (relationMatcher.find()) {
            String source = relationMatcher.group(1);
            String target = relationMatcher.group(3);
            String label = relationMatcher.group(4);
            String relationshipType = determineRelationshipType(relationMatcher.group(2), label);
            builder.addEdge(source, target, relationshipType);
            count++;
        }
        logger.debug("[parseClassRelationships] matched {} edges", count);
    }

    /**
     * Determines relationship type from Mermaid arrow syntax.
     */
    private String determineRelationshipType(String arrow, String label) {
        if (label != null && !label.isEmpty()) {
            return label.trim();
        }
        
        return switch (arrow) {
            case "--|>", "<|--" -> "inherits";
            case "..|>", "<|.." -> "implements";
            case "*--", "--*" -> "composes";
            case "o--", "--o" -> "aggregates";
            case "..>", "<.." -> "depends";
            default -> "association";
        };
    }

    /**
     * Parses Mermaid ER diagram and builds Draw.io nodes/edges.
     */
    private void parseErDiagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseErDiagram] input {} chars", mermaidCode.length());
        // Parse entities
        Pattern entityPattern = Pattern.compile("(\\w+)\\s*\\{([^}]*)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher entityMatcher = entityPattern.matcher(mermaidCode);

        int entityCount = 0;
        while (entityMatcher.find()) {
            String entityName = entityMatcher.group(1);
            String body = entityMatcher.group(2);
            List<String> attributes = parseErEntityBody(body);
            builder.addNode(entityName, attributes);
            entityCount++;
        }
        logger.debug("[parseErDiagram] entity pattern matched {} nodes", entityCount);

        // Parse relationships
        Pattern relationPattern = Pattern.compile("(\\w+)\\s*(\\|\\|--|\\|o--|\\}\\|--|\\|\\{--|--\\|\\||--o\\||--\\|\\}|--\\{\\||\\|\\|--o\\{|\\}o--\\|\\||o\\{--\\|\\|)\\s*(\\w+)\\s*:\\s*(.+)",
                Pattern.CASE_INSENSITIVE);
        Matcher relationMatcher = relationPattern.matcher(mermaidCode);

        int relCount = 0;
        while (relationMatcher.find()) {
            String source = relationMatcher.group(1);
            String target = relationMatcher.group(3);
            String label = relationMatcher.group(4).trim();
            builder.addEdge(source, target, label);
            relCount++;
        }
        logger.debug("[parseErDiagram] relationship pattern matched {} edges", relCount);
    }

    /**
     * Parses ER entity body (fields).
     */
    private List<String> parseErEntityBody(String body) {
        List<String> attributes = new ArrayList<>();
        String[] lines = body.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                // ER format: "type name" or "type name PK"
                attributes.add(line);
            }
        }
        
        return attributes;
    }

    /**
     * Parses Mermaid architecture/flowchart diagram and builds Draw.io nodes/edges.
     */
    private void parseArchitectureDiagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseArchitectureDiagram] input {} chars", mermaidCode.length());
        // Parse nodes: NodeId[Label] or NodeId[(Label)] or NodeId{Label} etc.
        Pattern nodePattern = Pattern.compile("(\\w+)\\s*([\\[\\(\\{<])([^\\]\\)\\}>]+)[\\]\\)\\}>]", Pattern.CASE_INSENSITIVE);
        Matcher nodeMatcher = nodePattern.matcher(mermaidCode);

        int nodeCount = 0;
        while (nodeMatcher.find()) {
            String nodeId = nodeMatcher.group(1);
            String label = nodeMatcher.group(3).trim();
            builder.addNode(label.isEmpty() ? nodeId : label);
            nodeCount++;
        }
        logger.debug("[parseArchitectureDiagram] node pattern matched {} nodes", nodeCount);

        // Parse edges
        Pattern edgePattern = Pattern.compile("(\\w+)\\s*(-->|---|-\\.-|==>)\\s*(\\w+)(?:\\s*\\|([^|]+)\\|)?", Pattern.CASE_INSENSITIVE);
        Matcher edgeMatcher = edgePattern.matcher(mermaidCode);

        int edgeCount = 0;
        while (edgeMatcher.find()) {
            String source = edgeMatcher.group(1);
            String target = edgeMatcher.group(3);
            String label = edgeMatcher.group(4);
            builder.addEdge(source, target, label);
            edgeCount++;
        }
        logger.debug("[parseArchitectureDiagram] edge pattern matched {} edges", edgeCount);
    }

    /**
     * Parses C4 diagram and builds Draw.io nodes/edges.
     */
    private void parseC4Diagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseC4Diagram] input {} chars", mermaidCode.length());
        // Parse C4 elements: Person(id, "name", "description"), System(id, "name", "description"), etc.
        Pattern elementPattern = Pattern.compile("(Person|System|SystemDb|Container|Component)\\s*\\(\\s*(\\w+)\\s*,\\s*\"([^\"]+)\"(?:\\s*,\\s*\"([^\"]+)\")?\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher elementMatcher = elementPattern.matcher(mermaidCode);

        int elementCount = 0;
        while (elementMatcher.find()) {
            String type = elementMatcher.group(1);
            String name = elementMatcher.group(3);
            String description = elementMatcher.group(4);
            List<String> attributes = new ArrayList<>();
            attributes.add("<<" + type + ">>");
            if (description != null && !description.isEmpty()) {
                attributes.add(description);
            }
            builder.addNode(name, attributes);
            elementCount++;
        }
        logger.debug("[parseC4Diagram] element pattern matched {} nodes", elementCount);

        // Parse C4 relationships: Rel(source, target, "label")
        Pattern relPattern = Pattern.compile("Rel\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*,\\s*\"([^\"]+)\"\\)", Pattern.CASE_INSENSITIVE);
        Matcher relMatcher = relPattern.matcher(mermaidCode);

        int relCount = 0;
        while (relMatcher.find()) {
            builder.addEdge(relMatcher.group(1), relMatcher.group(2), relMatcher.group(3));
            relCount++;
        }
        logger.debug("[parseC4Diagram] Rel pattern matched {} edges", relCount);
    }

    /**
     * Generic parser for unrecognized diagram types.
     */
    private void parseGenericDiagram(String mermaidCode, DrawIoXmlBuilder builder) {
        logger.debug("[parseGenericDiagram] input {} chars", mermaidCode.length());
        // Extract any words that look like identifiers
        Pattern wordPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9]+)\\b");
        Matcher wordMatcher = wordPattern.matcher(mermaidCode);

        int wordCount = 0;
        while (wordMatcher.find()) {
            String word = wordMatcher.group(1);
            if (!isMermaidKeyword(word)) {
                builder.addNode(word);
                wordCount++;
            }
        }
        logger.debug("[parseGenericDiagram] identifier pattern added {} nodes", wordCount);

        // Look for arrow-like patterns
        Pattern arrowPattern = Pattern.compile("(\\w+)\\s*[-=><]+\\s*(\\w+)");
        Matcher arrowMatcher = arrowPattern.matcher(mermaidCode);

        int arrowCount = 0;
        while (arrowMatcher.find()) {
            builder.addEdge(arrowMatcher.group(1), arrowMatcher.group(2), "");
            arrowCount++;
        }
        logger.debug("[parseGenericDiagram] arrow pattern matched {} edges", arrowCount);
    }

    /**
     * Checks if a word is a Mermaid keyword to be excluded.
     */
    private boolean isMermaidKeyword(String word) {
        String lower = word.toLowerCase();
        return switch (lower) {
            case "classDiagram", "sequencediagram", "erdiagram", "graph", "flowchart",
                 "participant", "class", "entity", "person", "system", "container",
                 "component", "rel", "title", "note" -> true;
            default -> false;
        };
    }

    /**
     * Generates a filename for the exported diagram.
     *
     * @param diagram the diagram entity
     * @return suggested filename
     */
    public String generateFilename(Diagram diagram) {
        return "diagram-" + diagram.getId().toString().substring(0, 8) + ".drawio";
    }

    public String generateFilename(UUID id) {
        return "diagram-" + id.toString().substring(0, 8) + ".drawio";
    }

    /**
     * Builds a guaranteed-safe fallback Draw.io XML when normal conversion fails.
     * Extracts capitalized identifiers from the raw code as simple boxes so the
     * file is always openable in Draw.io even if parsing was incomplete.
     *
     * @param diagramType label for the diagram type (used as title node)
     * @param rawCode     raw Mermaid or PlantUML source; may be null
     * @return valid Draw.io XML string, never throws
     */
    public String buildFallbackXml(String diagramType, String rawCode) {
        logger.warn("Building fallback Draw.io XML for type={}", diagramType);
        DrawIoXmlBuilder builder = new DrawIoXmlBuilder();

        // Title node is always present
        builder.addNode("[" + diagramType + "]");

        if (rawCode != null && !rawCode.isBlank()) {
            Pattern wordPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9]{2,})\\b");
            Matcher m = wordPattern.matcher(rawCode);
            Set<String> seen = new LinkedHashSet<>();
            while (m.find() && seen.size() < 25) {
                String word = m.group(1);
                if (!isMermaidKeyword(word)) {
                    seen.add(word);
                }
            }
            seen.forEach(builder::addNode);
            logger.debug("Fallback XML: added {} identifier nodes from raw code", seen.size());
        }

        return builder.build();
    }

    /**
     * Converts PlantUML code to Draw.io XML.
     * Used for diagrams stored in the domain_diagrams table (domain.Diagram).
     *
     * @param plantUmlCode the PlantUML source code
     * @param domainType   the diagram type from the domain model
     * @return Draw.io compatible XML string
     */
    public String convertPlantUmlToDrawIoXml(String plantUmlCode,
            com.example.aidiagramgenerator.domain.DiagramType domainType) {
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot convert Draw.io XML: PlantUML code is null or blank for type " + domainType);
        }

        logger.info("Converting PlantUML {} diagram to Draw.io XML via dedicated exporter (codeLength={})", domainType, plantUmlCode.length());

        try {
            return switch (domainType) {
                case SEQUENCE      -> new SequenceDrawIoExporter().export(plantUmlCode);
                case CLASS         -> new ClassDrawIoExporter().export(plantUmlCode);
                case ER            -> new ErDrawIoExporter().export(plantUmlCode);
                case USE_CASE      -> new UseCaseDrawIoExporter().export(plantUmlCode);
                case ACTIVITY      -> new ActivityDrawIoExporter().export(plantUmlCode);
                case STATE         -> new StateDrawIoExporter().export(plantUmlCode);
                case OBJECT        -> new ObjectDrawIoExporter().export(plantUmlCode);
                case COLLABORATION -> new CollaborationDrawIoExporter().export(plantUmlCode);
                case COMPONENT     -> new ComponentDrawIoExporter().export(plantUmlCode);
                case DEPLOYMENT    -> new DeploymentDrawIoExporter().export(plantUmlCode);
                case MICROSERVICES -> new MicroservicesDrawIoExporter().export(plantUmlCode);
                default            -> buildFallbackXml(domainType.name(), plantUmlCode);
            };
        } catch (Exception e) {
            logger.warn("Dedicated exporter failed for {} — falling back to generic XML: {}", domainType, e.getMessage(), e);
            return buildFallbackXml(domainType.name(), plantUmlCode);
        }
    }

    private void parsePlantUmlSequence(String code, DrawIoXmlBuilder builder) {
        logger.debug("[parsePlantUmlSequence] input {} chars", code.length());
        Pattern declPattern = Pattern.compile(
                "^\\s*(?:participant|actor|boundary|control|entity|database|collections)\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+(\\w+))?\\s*$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = declPattern.matcher(code);
        int nodeCount = 0;
        while (m.find()) {
            String name = m.group(2) != null ? m.group(2).trim() : m.group(1).trim();
            builder.addNode(name);
            nodeCount++;
        }
        logger.debug("[parsePlantUmlSequence] participant/actor declarations: {} nodes", nodeCount);

        Pattern arrowPattern = Pattern.compile(
                "(\\w+)\\s*(->>|-->>|->|-->|<-|<--|<<-|<<--)\\s*(\\w+)(?:\\s*:\\s*(.+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher am = arrowPattern.matcher(code);
        int edgeCount = 0;
        while (am.find()) {
            String label = am.group(4) != null ? am.group(4).trim() : "";
            builder.addEdge(am.group(1), am.group(3), label);
            edgeCount++;
        }
        logger.debug("[parsePlantUmlSequence] arrow pattern: {} edges", edgeCount);
    }

    private void parsePlantUmlEr(String code, DrawIoXmlBuilder builder) {
        logger.debug("[parsePlantUmlEr] input {} chars", code.length());
        Pattern entityPattern = Pattern.compile(
                "entity\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+\\w+)?\\s*\\{([^}]*)\\}",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = entityPattern.matcher(code);
        int entityCount = 0;
        while (m.find()) {
            builder.addNode(m.group(1).trim(), parseClassBody(m.group(2)));
            entityCount++;
        }
        logger.debug("[parsePlantUmlEr] entity pattern matched {} nodes", entityCount);

        Pattern relPattern = Pattern.compile(
                "(\\w+)\\s+(?:[|o}]{1,3}[-.][-.]?[|o{]{1,3})\\s+(\\w+)",
                Pattern.CASE_INSENSITIVE);
        Matcher rm = relPattern.matcher(code);
        int relCount = 0;
        while (rm.find()) {
            builder.addEdge(rm.group(1), rm.group(2), "");
            relCount++;
        }
        logger.debug("[parsePlantUmlEr] relationship pattern matched {} edges", relCount);
    }

    private void parsePlantUmlComponent(String code, DrawIoXmlBuilder builder) {
        logger.debug("[parsePlantUmlComponent] input {} chars", code.length());
        Pattern nodePattern = Pattern.compile("\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
        Matcher m = nodePattern.matcher(code);
        int bracketCount = 0;
        while (m.find()) {
            builder.addNode(m.group(1).trim());
            bracketCount++;
        }
        logger.debug("[parsePlantUmlComponent] bracket [...] pattern matched {} nodes", bracketCount);

        Pattern compPattern = Pattern.compile(
                "component\\s+\"?([\\w][\\w\\s]*)\"?", Pattern.CASE_INSENSITIVE);
        Matcher cm = compPattern.matcher(code);
        int compCount = 0;
        while (cm.find()) {
            builder.addNode(cm.group(1).trim());
            compCount++;
        }
        logger.debug("[parsePlantUmlComponent] component keyword pattern matched {} nodes", compCount);

        Pattern arrowPattern = Pattern.compile(
                "\\[([^\\]]+)\\]\\s*(-->|->|\\.\\.\\.)\\s*\\[([^\\]]+)\\](?:\\s*:\\s*(.+))?");
        Matcher am = arrowPattern.matcher(code);
        int edgeCount = 0;
        while (am.find()) {
            String label = am.group(4) != null ? am.group(4).trim() : "";
            builder.addEdge(am.group(1).trim(), am.group(3).trim(), label);
            edgeCount++;
        }
        logger.debug("[parsePlantUmlComponent] arrow pattern matched {} edges", edgeCount);
    }

    private void parsePlantUmlUseCase(String code, DrawIoXmlBuilder builder) {
        logger.debug("[parsePlantUmlUseCase] input {} chars", code.length());
        Pattern actorPattern = Pattern.compile(
                "^\\s*actor\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+(\\w+))?\\s*$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher am = actorPattern.matcher(code);
        int actorCount = 0;
        while (am.find()) {
            String name = am.group(2) != null ? am.group(2).trim() : am.group(1).trim();
            builder.addNode(name);
            actorCount++;
        }
        logger.debug("[parsePlantUmlUseCase] actor pattern matched {} nodes", actorCount);

        Pattern ucPattern = Pattern.compile("\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher um = ucPattern.matcher(code);
        int ucCount = 0;
        while (um.find()) {
            builder.addNode(um.group(1).trim());
            ucCount++;
        }
        logger.debug("[parsePlantUmlUseCase] use-case (...) pattern matched {} nodes", ucCount);

        Pattern relPattern = Pattern.compile(
                "(\\w+)\\s*(-->|->)\\s*\\(([^)]+)\\)(?:\\s*:\\s*(.+))?");
        Matcher rm = relPattern.matcher(code);
        int relCount = 0;
        while (rm.find()) {
            String label = rm.group(4) != null ? rm.group(4).trim() : "";
            builder.addEdge(rm.group(1), rm.group(3).trim(), label);
            relCount++;
        }
        logger.debug("[parsePlantUmlUseCase] relationship pattern matched {} edges", relCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SemanticModel export
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a {@link SemanticModel} directly to Draw.io XML without requiring a
     * stored {@link Diagram} entity.
     *
     * <p>Each {@link EntityNode} becomes an {@code mxCell} vertex; each
     * {@link Relationship} becomes an {@code mxCell} edge. Nodes are arranged in an
     * auto-calculated grid layout (4 per row by default).
     *
     * @param model the semantic model to export (must not be null)
     * @return Draw.io compatible XML string
     * @throws IllegalArgumentException if model is null
     */
    public String convertToDrawIoXml(SemanticModel model) {
        if (model == null) {
            throw new IllegalArgumentException("SemanticModel must not be null");
        }

        logger.info("Converting SemanticModel to Draw.io XML: {} entities, {} relationships",
                model.getEntities().size(), model.getRelationships().size());

        DrawIoXmlBuilder builder = new DrawIoXmlBuilder();
        builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.GRID);
        builder.setNodesPerRow(4);

        // Add entity nodes
        for (EntityNode entity : model.getEntities()) {
            List<String> attrs = entity.getAttributes();
            if (attrs.isEmpty()) {
                builder.addNode(entity.getName());
            } else {
                builder.addNode(entity.getName(), new ArrayList<>(attrs));
            }
        }

        // Add relationship edges (auto-creates missing endpoint nodes)
        for (Relationship rel : model.getRelationships()) {
            builder.addEdge(rel.getSource(), rel.getTarget(), rel.getType());
        }

        // Ensure every action verb is represented as a label attribute on a
        // generic "actions" node when no relationships reference it — keeps the
        // canvas useful even for purely verb-heavy descriptions.
        if (!model.getActions().isEmpty() && model.getRelationships().isEmpty()) {
            builder.addNode("Actions", model.getActions());
        }

        return builder.build();
    }

    /**
     * Converts PlantUML code to Draw.io XML.
     * Used for diagrams generated by the PlantUML/domain pipeline.
     *
     * @param plantUmlCode the PlantUML diagram source code
     * @param diagramId    the diagram's UUID (for logging)
     * @return Draw.io compatible XML string
     */
    public String convertPlantUmlToDrawIoXml(String plantUmlCode, UUID diagramId) {
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            throw new IllegalArgumentException("Diagram " + diagramId + " has no PlantUML code to convert");
        }
        logger.info("Converting PlantUML diagram id={} to Draw.io XML (codeLength={})", diagramId, plantUmlCode.length());
        DrawIoXmlBuilder builder = new DrawIoXmlBuilder();
        parsePlantUmlDiagram(plantUmlCode, builder);
        logger.info("Draw.io XML built from PlantUML: {} nodes, {} edges", builder.getNodeCount(), builder.getEdgeCount());
        if (builder.getNodeCount() == 0) {
            logger.warn("No nodes parsed from PlantUML diagram {} — building fallback XML", diagramId);
            return buildFallbackXml("PlantUML", plantUmlCode);
        }
        return builder.build();
    }

    /**
     * Parses PlantUML source and populates the builder with nodes and edges.
     */
    private void parsePlantUmlDiagram(String plantUmlCode, DrawIoXmlBuilder builder) {
        // Extract named entities: class, actor, participant, object, node, component, etc.
        Pattern entityPattern = Pattern.compile(
                "(?:class|actor|participant|object|node|component|database|entity|usecase|boundary|control|interface)\\s+\"?([\\w][\\w\\s]*?)\"?\\s*(?:\\{|$|\\n|\\bas\\b)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher entityMatcher = entityPattern.matcher(plantUmlCode);
        while (entityMatcher.find()) {
            String name = entityMatcher.group(1).trim();
            if (!name.isBlank()) {
                builder.addNode(name);
            }
        }

        // Extract relationships: A --> B, A ..> B, A -- B, A -> B, A .> B, etc.
        Pattern arrowPattern = Pattern.compile(
                "^\\s*(\\w+)\\s*(?:\"[^\"]*\"\\s*)?(-+>|<-+|\\.+>|<\\.+|--+|\\.+|-+|<-->)\\s*(?:\"[^\"]*\"\\s*)?(\\w+)(?:\\s*:\\s*(.+))?",
                Pattern.MULTILINE);
        Matcher arrowMatcher = arrowPattern.matcher(plantUmlCode);
        while (arrowMatcher.find()) {
            String source = arrowMatcher.group(1);
            String target = arrowMatcher.group(3);
            String label = arrowMatcher.group(4);
            if (source != null && target != null && !source.equalsIgnoreCase(target)) {
                builder.addEdge(source, target, label != null ? label.trim() : "");
            }
        }

        // Fallback: extract capitalized identifiers if no entities/edges were found
        if (builder.getNodeCount() == 0) {
            Pattern wordPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9]+)\\b");
            Matcher wordMatcher = wordPattern.matcher(plantUmlCode);
            while (wordMatcher.find()) {
                String word = wordMatcher.group(1);
                if (!isPlantUmlKeyword(word)) {
                    builder.addNode(word);
                }
            }
        }
    }

    private boolean isPlantUmlKeyword(String word) {
        String lower = word.toLowerCase();
        return switch (lower) {
            case "startuml", "enduml", "class", "interface", "abstract", "enum",
                 "actor", "participant", "object", "node", "component", "database",
                 "entity", "usecase", "boundary", "control", "rectangle", "title",
                 "note", "package", "namespace", "skinparam", "left", "right",
                 "top", "bottom", "together" -> true;
            default -> false;
        };
    }

    /**
     * Suggests a default filename for a {@link SemanticModel} export.
     *
     * @return a filename in the form {@code semantic-model-&lt;shortUUID&gt;.drawio}
     */
    public String generateFilenameForSemanticModel() {
        return "semantic-model-" + UUID.randomUUID().toString().substring(0, 8) + ".drawio";
    }
}
