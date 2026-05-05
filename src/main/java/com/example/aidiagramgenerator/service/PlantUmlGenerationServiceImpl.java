package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.*;
import com.example.aidiagramgenerator.domain.LayoutProfile.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of PlantUmlGenerationService that dynamically generates
 * PlantUML diagram code from a semantic model with style-aware formatting
 * and randomized layout variation.
 *
 * <p>Each generation produces a unique {@link LayoutProfile} that controls:
 * <ul>
 *   <li>Layout direction (LR / TB)</li>
 *   <li>Node and rank spacing</li>
 *   <li>Arrow styles (--&gt;, -&gt;&gt;, ..&gt;)</li>
 *   <li>Grouping styles (rectangle, package, folder, frame, cloud)</li>
 *   <li>Note positions (top, bottom, left, right)</li>
 * </ul>
 *
 * <p>When a seed is provided, generation is deterministic.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Service
public class PlantUmlGenerationServiceImpl implements PlantUmlGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PlantUmlGenerationServiceImpl.class);

    private static final String NEWLINE = "\n";
    private static final String INDENT = "  ";

    public PlantUmlGenerationServiceImpl() {
    }

    @Override
    public String generate(SemanticModel model, StyleProfile style) {
        return generate(model, style, (Long) null);
    }

    @Override
    public String generate(SemanticModel model, StyleProfile style, Long seed) {
        logger.info("──── PlantUML Generation Start ────");
        logger.info("Seed: {}", seed);

        // ── Pre-generation diagnostics ───────────────────────────────────
        validateInputs(model, style);

        DiagramType type = style.getDiagramType();
        if (type == null) {
            logger.error("Diagram type is null on StyleProfile (id={})", style.getId());
            throw new IllegalArgumentException("StyleProfile.diagramType must not be null");
        }

        logger.info("Diagram type: {}", type);
        logger.info("Style profile: id={}, layoutDirection={}, arrowStyle={}, spacingRule={}",
                style.getId(), style.getLayoutDirection(), style.getArrowStyle(), style.getSpacingRule());
        logger.info("Semantic model: entities={}, relationships={}, actions={}",
                model.getEntities().size(), model.getRelationships().size(), model.getActions().size());
        logger.debug("Entity names: {}", model.getEntities().stream()
                .map(EntityNode::getName).toList());
        logger.debug("Relationship summary: {}", model.getRelationships().stream()
                .map(r -> r.getSource() + " -> " + r.getTarget() + " [" + r.getType() + "]")
                .toList());

        // ── Layout profile ───────────────────────────────────────────────
        Random random = (seed != null) ? new Random(seed) : new Random();
        LayoutProfile layout = generateRandomLayoutProfile(random, seed, style);

        logger.info("Layout profile: direction={}, nodeSpacing={}, rankSpacing={}, arrows={}, " +
                        "grouping={}, notePosition={}, bgVariation={}",
                layout.getDirection(), layout.getNodeSpacing(), layout.getRankSpacing(),
                layout.getArrowStyle(), layout.getGroupingStyle(), layout.getNotePosition(),
                layout.getBackgroundColorVariation());

        // ── Generation ───────────────────────────────────────────────────
        try {
            logger.info("Dispatching to {} diagram generator", type);

            String plantUml = switch (type) {
                case CLASS -> generateClassDiagram(model, style, layout, random);
                case ER -> generateErDiagram(model, style, layout, random);
                case SEQUENCE -> generateSequenceDiagram(model, style, layout, random);
                case USE_CASE -> generateUseCaseDiagram(model, style, layout, random);
                case COMPONENT -> generateComponentDiagram(model, style, layout, random);
                case DEPLOYMENT -> generateDeploymentDiagram(model, style, layout, random);
                case ACTIVITY -> generateActivityDiagram(model);
                case STATE -> generateStateDiagram(model);
                case OBJECT -> generateObjectDiagram(model);
                case MICROSERVICES -> generateMicroservicesDiagram(model);
                case COLLABORATION -> generateClassDiagram(model, style, layout, random);
            };

            logger.info("PlantUML generated successfully ({} chars)", plantUml.length());
            logger.debug("PlantUML output:\n{}", plantUml);
            logger.info("──── PlantUML Generation End ──────");
            return plantUml;

        } catch (NullPointerException ex) {
            logger.error("NullPointerException during {} diagram generation. "
                            + "Model entities={}, relationships={}, actions={}. "
                            + "StyleProfile id={}, direction={}, arrows={}. Seed={}",
                    type,
                    model.getEntities().size(), model.getRelationships().size(), model.getActions().size(),
                    style.getId(), style.getLayoutDirection(), style.getArrowStyle(),
                    seed, ex);
            throw new RuntimeException(
                    "Diagram generation failed for type " + type
                            + ": a required value was null. Check semantic model completeness.", ex);

        } catch (IllegalArgumentException ex) {
            logger.error("Unsupported or invalid argument during {} diagram generation: {}",
                    type, ex.getMessage(), ex);
            throw new RuntimeException(
                    "Diagram generation failed for type " + type + ": " + ex.getMessage(), ex);

        } catch (Exception ex) {
            logger.error("Unexpected error during {} diagram generation (seed={}): {}",
                    type, seed, ex.getMessage(), ex);
            throw new RuntimeException(
                    "Diagram generation failed for type " + type
                            + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public String generate(SemanticModel model, StyleProfile style, LayoutProfile layout) {
        logger.info("──── PlantUML Generation Start (with explicit LayoutProfile) ────");

        validateInputs(model, style);
        if (layout == null) {
            logger.error("Generation failed: layout profile is null");
            throw new IllegalArgumentException("Layout profile cannot be null");
        }

        DiagramType type = style.getDiagramType();
        if (type == null) {
            throw new IllegalArgumentException("StyleProfile.diagramType must not be null");
        }

        logger.info("Diagram type: {}", type);
        logger.info("Layout profile: direction={}, nodeSpacing={}, rankSpacing={}, arrows={}, grouping={}",
                layout.getDirection(), layout.getNodeSpacing(), layout.getRankSpacing(),
                layout.getArrowStyle(), layout.getGroupingStyle());

        // Use a seeded random if seed is provided in layout, otherwise use random
        Random random = (layout.getSeed() != null) ? new Random(layout.getSeed()) : new Random();

        try {
            String plantUml = switch (type) {
                case CLASS -> generateClassDiagram(model, style, layout, random);
                case ER -> generateErDiagram(model, style, layout, random);
                case SEQUENCE -> generateSequenceDiagram(model, style, layout, random);
                case USE_CASE -> generateUseCaseDiagram(model, style, layout, random);
                case COMPONENT -> generateComponentDiagram(model, style, layout, random);
                case DEPLOYMENT -> generateDeploymentDiagram(model, style, layout, random);
                case ACTIVITY -> generateActivityDiagram(model);
                case STATE -> generateStateDiagram(model);
                case OBJECT -> generateObjectDiagram(model);
                case MICROSERVICES -> generateMicroservicesDiagram(model);
                case COLLABORATION -> generateClassDiagram(model, style, layout, random);
            };

            logger.info("PlantUML generated successfully ({} chars)", plantUml.length());
            return plantUml;

        } catch (Exception ex) {
            logger.error("Error during {} diagram generation with LayoutProfile: {}",
                    type, ex.getMessage(), ex);
            throw new RuntimeException("Diagram generation failed for type " + type + ": " + ex.getMessage(), ex);
        }
    }

    // ─── Layout Profile Generation ────────────────────────────────────────────

    /**
     * Generates a layout profile that respects the StyleProfile's direction and
     * spacing rule while randomizing other visual properties (arrows, grouping,
     * notes, background).  When a seed is provided, generation is deterministic.
     *
     * @param random the Random instance (seeded or not)
     * @param seed   the seed value (may be null)
     * @param style  the style profile whose direction/spacing to honour
     * @return a fully populated LayoutProfile
     */
    private LayoutProfile generateRandomLayoutProfile(Random random, Long seed, StyleProfile style) {
        Direction direction = resolveDirection(style.getLayoutDirection(), random);
        int[] spacing = resolveSpacing(style.getSpacingRule(), random);
        int nodeSpacing = spacing[0];
        int rankSpacing = spacing[1];

        ArrowStyle[] arrowStyles = ArrowStyle.values();
        ArrowStyle arrowStyle = arrowStyles[random.nextInt(arrowStyles.length)];

        GroupingStyle[] groupingStyles = GroupingStyle.values();
        GroupingStyle groupingStyle = groupingStyles[random.nextInt(groupingStyles.length)];

        NotePosition[] notePositions = NotePosition.values();
        NotePosition notePosition = notePositions[random.nextInt(notePositions.length)];

        // Background color variation: 0–30
        int bgVariation = random.nextInt(31);

        LayoutProfile profile = LayoutProfile.builder()
                .direction(direction)
                .nodeSpacing(nodeSpacing)
                .rankSpacing(rankSpacing)
                .arrowStyle(arrowStyle)
                .groupingStyle(groupingStyle)
                .notePosition(notePosition)
                .backgroundColorVariation(bgVariation)
                .seed(seed)
                .build();

        logger.debug("Layout profile: direction={}, nodeSpacing={}, rankSpacing={}, arrows={}, " +
                        "grouping={}, notes={}, bgVariation={}",
                direction, nodeSpacing, rankSpacing, arrowStyle, groupingStyle, notePosition, bgVariation);

        return profile;
    }

    /**
     * Maps the StyleProfile layoutDirection string to a LayoutProfile Direction.
     * Falls back to random if the value is null or unrecognised.
     */
    private Direction resolveDirection(String layoutDirection, Random random) {
        if (layoutDirection == null) {
            return random.nextBoolean() ? Direction.LEFT_TO_RIGHT : Direction.TOP_TO_BOTTOM;
        }
        return switch (layoutDirection.toLowerCase().replace("-", "").replace("_", "").trim()) {
            case "topdown", "td", "topbottom", "tb" -> Direction.TOP_TO_BOTTOM;
            case "leftright", "lr", "lefttoright" -> Direction.LEFT_TO_RIGHT;
            default -> {
                logger.warn("Unrecognised layoutDirection '{}' — falling back to random", layoutDirection);
                yield random.nextBoolean() ? Direction.LEFT_TO_RIGHT : Direction.TOP_TO_BOTTOM;
            }
        };
    }

    /**
     * Maps the StyleProfile spacingRule string to concrete node/rank spacing values.
     * Returns {@code int[]{nodeSpacing, rankSpacing}}.
     */
    private int[] resolveSpacing(String spacingRule, Random random) {
        if (spacingRule == null) {
            return new int[]{30 + random.nextInt(11) * 5, 30 + random.nextInt(11) * 5};
        }
        return switch (spacingRule.toLowerCase().trim()) {
            case "compact", "tight" -> new int[]{25, 25};
            case "normal", "default" -> new int[]{50, 50};
            case "expanded", "relaxed", "spacious" -> new int[]{80, 80};
            default -> {
                logger.warn("Unrecognised spacingRule '{}' — using normal spacing", spacingRule);
                yield new int[]{50, 50};
            }
        };
    }

    // ─── Deduplication & Grouping ─────────────────────────────────────────────

    /**
     * Removes duplicate entities (by sanitised name). First occurrence wins.
     */
    private List<EntityNode> deduplicateEntities(List<EntityNode> entities) {
        Set<String> seen = new LinkedHashSet<>();
        List<EntityNode> unique = new ArrayList<>();
        for (EntityNode e : entities) {
            if (seen.add(sanitizeName(e.getName()))) {
                unique.add(e);
            }
        }
        return unique;
    }

    /**
     * Groups related entities into clusters based on relationship connectivity.
     * Entities that share direct or transitive relationships are placed in the
     * same group.  Isolated entities (no relationships) are returned in a
     * special "" (empty-key) group.
     *
     * @return ordered map of group label → entities in that group
     */
    private Map<String, List<EntityNode>> groupRelatedEntities(
            List<EntityNode> entities, List<Relationship> relationships) {

        // Build name→EntityNode index
        Map<String, EntityNode> byName = new LinkedHashMap<>();
        for (EntityNode e : entities) {
            byName.putIfAbsent(sanitizeName(e.getName()), e);
        }

        // Union-Find over sanitised names
        Map<String, String> parent = new HashMap<>();
        for (String name : byName.keySet()) {
            parent.put(name, name);
        }

        for (Relationship rel : relationships) {
            String a = sanitizeName(rel.getSource());
            String b = sanitizeName(rel.getTarget());
            if (parent.containsKey(a) && parent.containsKey(b)) {
                union(parent, a, b);
            }
        }

        // Collect groups
        Map<String, List<EntityNode>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, EntityNode> entry : byName.entrySet()) {
            String root = find(parent, entry.getKey());
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(entry.getValue());
        }

        // Separate isolated vs connected
        Map<String, List<EntityNode>> result = new LinkedHashMap<>();
        int groupIndex = 1;
        List<EntityNode> isolated = new ArrayList<>();
        for (List<EntityNode> group : groups.values()) {
            if (group.size() > 1) {
                result.put("Group " + groupIndex++, group);
            } else {
                isolated.add(group.get(0));
            }
        }
        if (!isolated.isEmpty()) {
            result.put("", isolated); // empty key = ungrouped
        }

        return result;
    }

    private String find(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x))); // path compression
            x = parent.get(x);
        }
        return x;
    }

    private void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private void validateInputs(SemanticModel model, StyleProfile style) {
        if (model == null) {
            logger.error("Generation failed: semantic model is null");
            throw new IllegalArgumentException("Semantic model cannot be null");
        }
        if (style == null) {
            logger.error("Generation failed: style profile is null");
            throw new IllegalArgumentException("Style profile cannot be null");
        }
    }

    // ─── Diagram Generators ───────────────────────────────────────────────────

    /**
     * Generates a class diagram in PlantUML format with deduplication and grouping.
     */
    private String generateClassDiagram(SemanticModel model, StyleProfile style,
                                        LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendLayoutDirective(sb, layout);
        appendSkinParams(sb, layout, "class");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        Map<String, List<EntityNode>> groups = groupRelatedEntities(entities, model.getRelationships());

        // Emit grouped and ungrouped entities
        for (Map.Entry<String, List<EntityNode>> entry : groups.entrySet()) {
            String groupLabel = entry.getKey();
            List<EntityNode> members = entry.getValue();
            boolean isGrouped = !groupLabel.isEmpty();

            if (isGrouped) {
                String keyword = layout.getGroupingStyle().toPlantUml();
                sb.append(keyword).append(" \"").append(groupLabel).append("\" {").append(NEWLINE);
            }

            String prefix = isGrouped ? INDENT : "";
            for (EntityNode entity : members) {
                sb.append(prefix).append("class ").append(sanitizeName(entity.getName()));
                if (entity.hasAttributes()) {
                    sb.append(" {").append(NEWLINE);
                    for (String attr : entity.getAttributes()) {
                        String visibility = randomVisibility(random);
                        sb.append(prefix).append(INDENT).append(visibility).append(" ")
                          .append(sanitizeName(attr)).append(NEWLINE);
                    }
                    sb.append(prefix).append("}");
                }
                sb.append(NEWLINE);
            }

            if (isGrouped) {
                sb.append("}").append(NEWLINE);
            }
        }

        sb.append(NEWLINE);

        // Generate relationships
        for (Relationship rel : model.getRelationships()) {
            String source = sanitizeName(rel.getSource());
            String target = sanitizeName(rel.getTarget());
            String arrow = mapRelationshipToClassArrow(rel.getType(), layout.getArrowStyle());
            sb.append(source).append(" ").append(arrow).append(" ").append(target);

            if (!"association".equals(rel.getType())) {
                sb.append(" : ").append(rel.getType());
            }
            sb.append(NEWLINE);
        }

        // Add a note using the layout's note position
        appendDiagramNote(sb, layout, "Class Diagram", entities);

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    /**
     * Generates an ER diagram in PlantUML format with deduplication and grouping.
     */
    private String generateErDiagram(SemanticModel model, StyleProfile style,
                                     LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendLayoutDirective(sb, layout);
        appendSkinParams(sb, layout, "entity");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        Map<String, List<EntityNode>> groups = groupRelatedEntities(entities, model.getRelationships());

        for (Map.Entry<String, List<EntityNode>> entry : groups.entrySet()) {
            String groupLabel = entry.getKey();
            List<EntityNode> members = entry.getValue();
            boolean isGrouped = !groupLabel.isEmpty();

            if (isGrouped) {
                String keyword = layout.getGroupingStyle().toPlantUml();
                sb.append(keyword).append(" \"").append(groupLabel).append("\" {").append(NEWLINE);
            }

            String prefix = isGrouped ? INDENT : "";
            for (EntityNode entity : members) {
                sb.append(prefix).append("entity \"").append(entity.getName()).append("\" as ")
                  .append(sanitizeName(entity.getName())).append(" {").append(NEWLINE);

                if (entity.hasAttributes()) {
                    sb.append(prefix).append(INDENT).append("* ")
                      .append(sanitizeName(entity.getAttributes().get(0)))
                      .append(" : PK").append(NEWLINE);
                    sb.append(prefix).append(INDENT).append("--").append(NEWLINE);

                    for (int i = 1; i < entity.getAttributes().size(); i++) {
                        sb.append(prefix).append(INDENT)
                          .append(sanitizeName(entity.getAttributes().get(i)))
                          .append(" : ").append(randomDataType(random)).append(NEWLINE);
                    }
                } else {
                    sb.append(prefix).append(INDENT).append("* id : PK").append(NEWLINE);
                    sb.append(prefix).append(INDENT).append("--").append(NEWLINE);
                    sb.append(prefix).append(INDENT).append("name : VARCHAR").append(NEWLINE);
                }

                sb.append(prefix).append("}").append(NEWLINE).append(NEWLINE);
            }

            if (isGrouped) {
                sb.append("}").append(NEWLINE);
            }
        }

        for (Relationship rel : model.getRelationships()) {
            String source = sanitizeName(rel.getSource());
            String target = sanitizeName(rel.getTarget());
            String cardinality = mapToCardinality(rel.getType());
            sb.append(source).append(" ").append(cardinality).append(" ").append(target).append(NEWLINE);
        }

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    /**
     * Generates a sequence diagram in PlantUML format with deduplicated participants.
     */
    private String generateSequenceDiagram(SemanticModel model, StyleProfile style,
                                           LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendSkinParams(sb, layout, "sequence");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        List<String> actions = model.getActions();
        String arrow = mapToSequenceArrow(layout.getArrowStyle());
        String replyArrow = reverseSequenceArrow(arrow);

        // Declare all participants
        for (EntityNode entity : entities) {
            String participantType = determineSequenceParticipantType(entity.getName());
            sb.append(participantType).append(" \"").append(entity.getName()).append("\" as ")
              .append(sanitizeName(entity.getName())).append(NEWLINE);
        }
        sb.append(NEWLINE);

        if (entities.isEmpty()) {
            sb.append("@enduml");
            return sb.toString();
        }

        // Identify the User actor (first actor-typed participant) and services
        String userAlias = sanitizeName(entities.get(0).getName());
        List<String> serviceAliases = new ArrayList<>();
        for (int i = 1; i < entities.size(); i++) {
            serviceAliases.add(sanitizeName(entities.get(i).getName()));
        }

        if (actions.isEmpty()) {
            // Fallback: generic request/response pairs
            for (String svc : serviceAliases) {
                sb.append(userAlias).append(" ").append(arrow).append(" ").append(svc)
                  .append(" : request").append(NEWLINE);
                sb.append(svc).append(" ").append(replyArrow).append(" ").append(userAlias)
                  .append(" : response").append(NEWLINE);
            }
        } else {
            // Distribute actions across participants in a meaningful way:
            //   - First action: User -> first service
            //   - Middle actions: internal self-calls on the service
            //   - Last action (if >1 service): last service -> User (confirmation)
            int total = actions.size();
            for (int i = 0; i < total; i++) {
                String label = actions.get(i);
                if (i == 0) {
                    // Initiating call: User -> first service
                    String target = serviceAliases.isEmpty() ? userAlias : serviceAliases.get(0);
                    sb.append(userAlias).append(" ").append(arrow).append(" ").append(target)
                      .append(" : ").append(label).append(NEWLINE);
                } else if (i == total - 1 && !serviceAliases.isEmpty()) {
                    // Final call: last service -> User
                    String lastSvc = serviceAliases.get(serviceAliases.size() - 1);
                    sb.append(lastSvc).append(" ").append(replyArrow).append(" ").append(userAlias)
                      .append(" : ").append(label).append(NEWLINE);
                } else {
                    // Middle actions: route through services or self-calls
                    if (serviceAliases.size() >= 2 && i < serviceAliases.size()) {
                        // Inter-service call
                        String src = serviceAliases.get(i - 1);
                        String tgt = serviceAliases.get(Math.min(i, serviceAliases.size() - 1));
                        sb.append(src).append(" ").append(arrow).append(" ").append(tgt)
                          .append(" : ").append(label).append(NEWLINE);
                    } else {
                        // Self-call on the first service
                        String svc = serviceAliases.isEmpty() ? userAlias : serviceAliases.get(0);
                        sb.append(svc).append(" -> ").append(svc)
                          .append(" : ").append(label).append(NEWLINE);
                    }
                }
            }
        }

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    /**
     * Generates a use case diagram in PlantUML format with deduplicated entities.
     */
    private String generateUseCaseDiagram(SemanticModel model, StyleProfile style,
                                          LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendLayoutDirective(sb, layout);
        appendSkinParams(sb, layout, "usecase");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        Set<String> emittedActions = new LinkedHashSet<>();

        if (!entities.isEmpty()) {
            EntityNode actor = entities.get(0);
            sb.append("actor \"").append(actor.getName()).append("\" as ")
              .append(sanitizeName(actor.getName())).append(NEWLINE);

            // Use layout's grouping style for the system boundary
            String groupKeyword = layout.getGroupingStyle().toPlantUml();
            sb.append(groupKeyword).append(" System {").append(NEWLINE);
            for (int i = 1; i < entities.size(); i++) {
                EntityNode uc = entities.get(i);
                sb.append(INDENT).append("usecase \"").append(uc.getName()).append("\" as ")
                  .append(sanitizeName(uc.getName())).append(NEWLINE);
            }

            for (String action : model.getActions()) {
                String key = sanitizeName(action);
                if (emittedActions.add(key)) {
                    sb.append(INDENT).append("usecase \"").append(capitalize(action)).append("\" as UC_")
                      .append(key).append(NEWLINE);
                }
            }
            sb.append("}").append(NEWLINE).append(NEWLINE);

            // Connect actor to use cases using layout arrow style
            String actorName = sanitizeName(actor.getName());
            String arrow = layout.getArrowStyle().getNotation();
            for (int i = 1; i < entities.size(); i++) {
                sb.append(actorName).append(" ").append(arrow).append(" ")
                  .append(sanitizeName(entities.get(i).getName())).append(NEWLINE);
            }
            for (String action : model.getActions()) {
                String key = sanitizeName(action);
                if (emittedActions.contains(key)) {
                    sb.append(actorName).append(" ").append(arrow).append(" UC_")
                      .append(key).append(NEWLINE);
                }
            }
        }

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    /**
     * Generates a component diagram in PlantUML format with deduplication and grouping.
     */
    private String generateComponentDiagram(SemanticModel model, StyleProfile style,
                                            LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendLayoutDirective(sb, layout);
        appendSkinParams(sb, layout, "component");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        Map<String, List<EntityNode>> groups = groupRelatedEntities(entities, model.getRelationships());

        for (Map.Entry<String, List<EntityNode>> entry : groups.entrySet()) {
            String groupLabel = entry.getKey();
            List<EntityNode> members = entry.getValue();
            boolean isGrouped = !groupLabel.isEmpty();

            if (isGrouped) {
                String keyword = layout.getGroupingStyle().toPlantUml();
                sb.append(keyword).append(" \"").append(groupLabel).append("\" {").append(NEWLINE);
            }

            String prefix = isGrouped ? INDENT : "";
            for (EntityNode entity : members) {
                sb.append(prefix).append("component \"").append(entity.getName()).append("\" as ")
                  .append(sanitizeName(entity.getName()));

                if (entity.hasAttributes()) {
                    sb.append(" {").append(NEWLINE);
                    for (String attr : entity.getAttributes()) {
                        sb.append(prefix).append(INDENT).append("port \"").append(attr).append("\"").append(NEWLINE);
                    }
                    sb.append(prefix).append("}");
                }
                sb.append(NEWLINE);
            }

            if (isGrouped) {
                sb.append("}").append(NEWLINE);
            }
        }

        sb.append(NEWLINE);

        for (Relationship rel : model.getRelationships()) {
            String source = sanitizeName(rel.getSource());
            String target = sanitizeName(rel.getTarget());
            String arrow = mapToComponentArrow(rel.getType(), layout.getArrowStyle());
            sb.append(source).append(" ").append(arrow).append(" ").append(target);
            if (!"association".equals(rel.getType())) {
                sb.append(" : <<").append(rel.getType()).append(">>");
            }
            sb.append(NEWLINE);
        }

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    /**
     * Generates a deployment diagram in PlantUML format with deduplication and grouping.
     */
    private String generateDeploymentDiagram(SemanticModel model, StyleProfile style,
                                             LayoutProfile layout, Random random) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NEWLINE);
        appendLayoutDirective(sb, layout);
        appendSkinParams(sb, layout, "deployment");
        sb.append(NEWLINE);

        List<EntityNode> entities = deduplicateEntities(model.getEntities());
        Map<String, List<EntityNode>> groups = groupRelatedEntities(entities, model.getRelationships());

        for (Map.Entry<String, List<EntityNode>> entry : groups.entrySet()) {
            String groupLabel = entry.getKey();
            List<EntityNode> members = entry.getValue();
            boolean isGrouped = !groupLabel.isEmpty();

            if (isGrouped) {
                String keyword = layout.getGroupingStyle().toPlantUml();
                sb.append(keyword).append(" \"").append(groupLabel).append("\" {").append(NEWLINE);
            }

            String prefix = isGrouped ? INDENT : "";
            for (EntityNode entity : members) {
                String nodeType = randomNodeType(random);
                sb.append(prefix).append(nodeType).append(" \"").append(entity.getName()).append("\" as ")
                  .append(sanitizeName(entity.getName()));

                if (entity.hasAttributes()) {
                    sb.append(" {").append(NEWLINE);
                    for (String attr : entity.getAttributes()) {
                        sb.append(prefix).append(INDENT).append("artifact \"").append(attr).append("\"").append(NEWLINE);
                    }
                    sb.append(prefix).append("}");
                }
                sb.append(NEWLINE);
            }

            if (isGrouped) {
                sb.append("}").append(NEWLINE);
            }
        }

        sb.append(NEWLINE);

        for (Relationship rel : model.getRelationships()) {
            String source = sanitizeName(rel.getSource());
            String target = sanitizeName(rel.getTarget());
            String arrow = layout.getArrowStyle().getNotation();
            sb.append(source).append(" ").append(arrow).append(" ").append(target);
            sb.append(" : ").append(rel.getType());
            sb.append(NEWLINE);
        }

        sb.append(NEWLINE).append("@enduml");
        return sb.toString();
    }

    // ─── Layout Helpers ───────────────────────────────────────────────────────

    /**
     * Appends the layout direction directive from the LayoutProfile.
     */
    private void appendLayoutDirective(StringBuilder sb, LayoutProfile layout) {
        sb.append(layout.getDirection().toPlantUml()).append(NEWLINE);
    }

    /**
     * Appends skin parameters derived from the LayoutProfile for visual variation.
     */
    private void appendSkinParams(StringBuilder sb, LayoutProfile layout, String diagramContext) {
        // Arrow color
        sb.append("skinparam ").append(diagramContext).append("ArrowColor #333333").append(NEWLINE);

        // Node spacing
        sb.append("skinparam nodesep ").append(layout.getNodeSpacing()).append(NEWLINE);
        sb.append("skinparam ranksep ").append(layout.getRankSpacing()).append(NEWLINE);

        // Arrow line style
        switch (layout.getArrowStyle()) {
            case DOTTED -> sb.append("skinparam ").append(diagramContext)
                    .append("ArrowStyle dotted").append(NEWLINE);
            case ASYNC -> sb.append("skinparam ").append(diagramContext)
                    .append("ArrowStyle dashed").append(NEWLINE);
            default -> { /* solid — no override needed */ }
        }

        // Background color with variation
        int v = layout.getBackgroundColorVariation();
        String bgColor = String.format("#%02X%02X%02X", 250 - v, 250 - v, 255);
        sb.append("skinparam ").append(diagramContext).append("BackgroundColor ").append(bgColor).append(NEWLINE);
    }

    /**
     * Appends a note to the diagram using the layout's note position.
     */
    private void appendDiagramNote(StringBuilder sb, LayoutProfile layout, String diagramLabel,
                                   List<EntityNode> entities) {
        if (entities.isEmpty()) return;

        String position = layout.getNotePosition().toPlantUml();
        String firstEntity = sanitizeName(entities.get(0).getName());
        sb.append(NEWLINE);
        sb.append("note ").append(position).append(" of ").append(firstEntity).append(NEWLINE);
        sb.append(INDENT).append(diagramLabel).append(NEWLINE);
        sb.append("end note").append(NEWLINE);
    }

    // ─── Arrow Mapping ────────────────────────────────────────────────────────

    /**
     * Maps relationship type to class diagram arrow notation using LayoutProfile arrow style.
     */
    private String mapRelationshipToClassArrow(String type, ArrowStyle arrowStyle) {
        String lineStyle = switch (arrowStyle) {
            case DOTTED -> "..";
            case ASYNC -> ".";
            default -> "-";
        };

        return switch (type.toLowerCase()) {
            case "inheritance" -> "<|" + lineStyle + "-";
            case "realization" -> "<|" + ".." + "-";
            case "composition" -> "*" + lineStyle + "-";
            case "aggregation" -> "o" + lineStyle + "-";
            case "dependency" -> "<" + ".." + "-";
            default -> lineStyle + "->";
        };
    }

    /**
     * Maps relationship type to ER diagram cardinality notation.
     */
    private String mapToCardinality(String type) {
        return switch (type.toLowerCase()) {
            case "composition", "aggregation" -> "||--o{";
            case "association" -> "}o--o{";
            case "dependency" -> "||..o{";
            default -> "||--||";
        };
    }

    /**
     * Maps LayoutProfile arrow style to sequence diagram arrow.
     */
    private String mapToSequenceArrow(ArrowStyle arrowStyle) {
        return switch (arrowStyle) {
            case ASYNC -> "->>";
            case DOTTED -> "-->";
            default -> "->";
        };
    }

    /**
     * Reverses a sequence arrow for return messages.
     */
    private String reverseSequenceArrow(String arrow) {
        return switch (arrow) {
            case "->>" -> "<<-";
            case "-->" -> "<--";
            default -> "<-";
        };
    }

    /**
     * Maps relationship type to component diagram arrow using LayoutProfile arrow style.
     */
    private String mapToComponentArrow(String type, ArrowStyle arrowStyle) {
        String line = (arrowStyle == ArrowStyle.DOTTED || arrowStyle == ArrowStyle.ASYNC) ? ".." : "--";
        return switch (type.toLowerCase()) {
            case "dependency" -> line + ">";
            case "realization" -> line + "|>";
            default -> line + ">";
        };
    }

    // ─── Random Helpers (all accept Random for determinism) ───────────────────

    private String randomVisibility(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "-";  // private
            case 1 -> "#";  // protected
            case 2 -> "~";  // package
            default -> "+"; // public
        };
    }

    private String randomDataType(Random random) {
        String[] types = {"VARCHAR", "INT", "BOOLEAN", "TIMESTAMP", "TEXT", "DECIMAL", "UUID"};
        return types[random.nextInt(types.length)];
    }

    /**
     * Determines the appropriate PlantUML participant keyword for a sequence diagram
     * based on naming conventions.
     * <ul>
     *   <li>External users (User, Admin, Client, Customer, Person) → {@code actor}</li>
     *   <li>Technical components (*Service, *Controller, *Repository, *Gateway, etc.) → {@code participant}</li>
     *   <li>Data stores (*Database, *DB, *Store, *Cache) → {@code database}</li>
     *   <li>Everything else → {@code participant}</li>
     * </ul>
     */
    private String determineSequenceParticipantType(String entityName) {
        String lower = entityName.toLowerCase();
        // External human actors
        if (lower.matches("user|admin|client|customer|person|operator|actor|guest|member")) {
            return "actor";
        }
        // Data store components
        if (lower.endsWith("database") || lower.endsWith("db") || lower.endsWith("store")
                || lower.endsWith("cache") || lower.endsWith("storage")) {
            return "database";
        }
        // Technical service/component participants
        if (entityName.endsWith("Service") || entityName.endsWith("Controller")
                || entityName.endsWith("Repository") || entityName.endsWith("Gateway")
                || entityName.endsWith("Manager") || entityName.endsWith("Handler")
                || entityName.endsWith("Facade") || entityName.endsWith("Adapter")
                || entityName.endsWith("Client") || entityName.endsWith("Provider")) {
            return "participant";
        }
        return "participant";
    }

    private String randomNodeType(Random random) {
        String[] types = {"node", "cloud", "database", "folder", "frame", "rectangle"};
        return types[random.nextInt(types.length)];
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private String generateActivityDiagram(SemanticModel model) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append("start\n");

        List<String> actions = model.getActions();
        List<EntityNode> entities = model.getEntities();

        if (actions != null && !actions.isEmpty()) {
            sb.append(":").append(capitalize(actions.get(0))).append(";\n");
            for (int i = 1; i < actions.size(); i++) {
                sb.append("--> :").append(capitalize(actions.get(i))).append(";\n");
            }
        } else if (entities != null && !entities.isEmpty()) {
            sb.append(":").append(capitalize(entities.get(0).getName())).append(";\n");
            for (int i = 1; i < entities.size(); i++) {
                sb.append("--> :").append(capitalize(entities.get(i).getName())).append(";\n");
            }
        } else {
            sb.append(":Initialize;\n");
            sb.append("--> :Process request;\n");
            sb.append("--> :Validate input;\n");
            sb.append("--> :Execute operation;\n");
            sb.append("--> :Return result;\n");
        }

        sb.append("stop\n");
        sb.append("@enduml");
        return sb.toString();
    }

    private String generateStateDiagram(SemanticModel model) {
        List<EntityNode> entities = model.getEntities();
        List<String> actions = model.getActions();

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (entities == null || entities.isEmpty()) {
            sb.append("[*] --> Idle\n");
            sb.append("Idle --> Processing : start\n");
            sb.append("Processing --> Completed : finish\n");
            sb.append("Processing --> Failed : error\n");
            sb.append("Completed --> [*]\n");
            sb.append("Failed --> [*]\n");
        } else {
            String firstName = sanitizeName(entities.get(0).getName());
            sb.append("[*] --> ").append(firstName).append("\n");
            for (int i = 0; i < entities.size() - 1; i++) {
                String from = sanitizeName(entities.get(i).getName());
                String to   = sanitizeName(entities.get(i + 1).getName());
                String label = (actions != null && i < actions.size())
                        ? actions.get(i)
                        : "transition";
                sb.append(from).append(" --> ").append(to).append(" : ").append(label).append("\n");
            }
            sb.append(sanitizeName(entities.get(entities.size() - 1).getName())).append(" --> [*]\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String generateObjectDiagram(SemanticModel model) {
        List<EntityNode> entities = model.getEntities();

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (entities == null || entities.isEmpty()) {
            sb.append("object User1\n");
            sb.append("object Order1\n");
            sb.append("object Product1\n");
            sb.append("\n");
            sb.append("User1 --> Order1\n");
            sb.append("Order1 --> Product1\n");
        } else {
            // Declare instances (EntityName + "1")
            for (EntityNode entity : entities) {
                sb.append("object ").append(sanitizeName(entity.getName())).append("1\n");
            }
            sb.append("\n");
            // Chain with -->
            for (int i = 0; i < entities.size() - 1; i++) {
                sb.append(sanitizeName(entities.get(i).getName())).append("1 --> ")
                  .append(sanitizeName(entities.get(i + 1).getName())).append("1\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String generateMicroservicesDiagram(SemanticModel model) {
        List<EntityNode> entities = model.getEntities();

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (entities == null || entities.isEmpty()) {
            sb.append("rectangle \"[API Gateway]\" as APIGateway\n");
            sb.append("rectangle \"[Auth Service]\" as AuthService\n");
            sb.append("rectangle \"[Order Service]\" as OrderService\n");
            sb.append("rectangle \"[User Service]\" as UserService\n");
            sb.append("\n");
            sb.append("APIGateway --> AuthService : authenticate\n");
            sb.append("APIGateway --> OrderService : route\n");
            sb.append("OrderService --> UserService : user info\n");
        } else {
            // Declare each entity as a rectangle with [Label] style
            for (EntityNode entity : entities) {
                String alias = sanitizeName(entity.getName());
                sb.append("rectangle \"[").append(entity.getName()).append("]\" as ").append(alias).append("\n");
            }
            sb.append("\n");
            // Use explicit relationships or auto-connect from gateway (first) to others
            List<Relationship> rels = model.getRelationships();
            if (rels != null && !rels.isEmpty()) {
                for (Relationship rel : rels) {
                    sb.append(sanitizeName(rel.getSource())).append(" --> ")
                      .append(sanitizeName(rel.getTarget()));
                    if (rel.getType() != null && !"association".equals(rel.getType())) {
                        sb.append(" : ").append(rel.getType());
                    }
                    sb.append("\n");
                }
            } else if (entities.size() >= 2) {
                String gateway = sanitizeName(entities.get(0).getName());
                for (int i = 1; i < entities.size(); i++) {
                    sb.append(gateway).append(" --> ")
                      .append(sanitizeName(entities.get(i).getName())).append(" : route\n");
                }
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String sanitizeName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
