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
                case COLLABORATION -> generateCollaborationDiagram(model);
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
                case COLLABORATION -> generateCollaborationDiagram(model);
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
        return switch (layoutDirection.toLowerCase().replaceAll("[\\s\\-_]", "").trim()) {
            case "topdown", "td", "topbottom", "tb", "toptobottomdirection" -> Direction.TOP_TO_BOTTOM;
            case "leftright", "lr", "lefttoright", "lefttorightdirection" -> Direction.LEFT_TO_RIGHT;
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
                        // Use visibility already encoded in attr string (e.g. "+ String name"),
                        // otherwise fall back to a convention-based default.
                        String rendered = renderClassMember(attr, random);
                        sb.append(prefix).append(INDENT).append(rendered).append(NEWLINE);
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
            String relType = rel.getType().toLowerCase();
            String arrow = mapRelationshipToClassArrow(relType, layout.getArrowStyle());

            // For inheritance/realization the UML triangle points to the parent:
            // PlantUML: Parent <|-- Child  →  emit target <|-- source
            if ("inheritance".equals(relType) || "realization".equals(relType)) {
                sb.append(target).append(" ").append(arrow).append(" ").append(source);
            } else if (rel.getSrcMultiplicity() != null || rel.getTgtMultiplicity() != null) {
                // Emit multiplicity decorators when available
                String srcMult = rel.getSrcMultiplicity() != null ? rel.getSrcMultiplicity() : "1";
                String tgtMult = rel.getTgtMultiplicity() != null ? rel.getTgtMultiplicity() : "*";
                sb.append(source)
                  .append(" \"").append(srcMult).append("\" ")
                  .append(arrow)
                  .append(" \"").append(tgtMult).append("\" ")
                  .append(target);
            } else {
                sb.append(source).append(" ").append(arrow).append(" ").append(target);
            }

            if (!"association".equals(relType)) {
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
        sb.append("autonumber").append(NEWLINE);
        sb.append(NEWLINE);

        List<EntityNode> entities = new ArrayList<>(deduplicateEntities(model.getEntities()));
        // Actors (User, Admin, Customer…) must appear left-most for proper UML column ordering
        entities.sort(Comparator.comparingInt(
                e -> "actor".equals(determineSequenceParticipantType(e.getName())) ? 0 : 1));
        List<String> actions = model.getActions();
        String arrow = mapToSequenceArrow(layout.getArrowStyle());

        // Declare all participants
        for (EntityNode entity : entities) {
            String name = entity.getName();
            String participantType = determineSequenceParticipantType(name);
            String alias = sequenceParticipantAlias(name);
            if (name.contains(" ") || !name.equals(alias)) {
                sb.append(participantType).append(" \"").append(name).append("\" as ").append(alias).append(NEWLINE);
            } else {
                sb.append(participantType).append(" ").append(alias).append(NEWLINE);
            }
        }
        sb.append(NEWLINE);

        if (entities.isEmpty()) {
            sb.append("@enduml");
            return sb.toString();
        }

        // Identify the User actor (first actor-typed participant) and services
        String userAlias = sequenceParticipantAlias(entities.get(0).getName());
        List<String> serviceAliases = new ArrayList<>();
        for (int i = 1; i < entities.size(); i++) {
            serviceAliases.add(sequenceParticipantAlias(entities.get(i).getName()));
        }

        List<Relationship> relationships = model.getRelationships();

        if (!relationships.isEmpty()) {
            // Use structured message flow from SemanticModel relationships
            Set<String> activated = new LinkedHashSet<>();
            int actionIdx = 0;
            for (Relationship rel : relationships) {
                String src = sequenceParticipantAlias(rel.getSource());
                String tgt = sequenceParticipantAlias(rel.getTarget());
                String type = rel.getType() == null ? "sends" : rel.getType().toLowerCase(Locale.ROOT);

                // ── ALT fragment markers ──────────────────────────────────────
                if ("alt_start".equals(type)) {
                    String condition = rel.getSrcMultiplicity();
                    sb.append("alt ").append(condition != null ? condition : "condition").append(NEWLINE);
                    continue;
                }
                if ("alt_else".equals(type)) {
                    String elseCondition = rel.getSrcMultiplicity();
                    sb.append("else");
                    if (elseCondition != null && !elseCondition.isBlank()) {
                        sb.append(" ").append(elseCondition);
                    }
                    sb.append(NEWLINE);
                    continue;
                }
                if ("alt_end".equals(type)) {
                    sb.append("end").append(NEWLINE);
                    continue;
                }

                // ── PAR fragment markers ──────────────────────────────────────
                if ("par_start".equals(type)) {
                    sb.append("par").append(NEWLINE);
                    continue;
                }
                if ("par_else".equals(type)) {
                    sb.append("else").append(NEWLINE);
                    continue;
                }
                if ("par_end".equals(type)) {
                    sb.append("end").append(NEWLINE);
                    continue;
                }
                // ─────────────────────────────────────────────────────────────

                String label = rel.getSrcMultiplicity();
                if (label == null || label.isBlank()) {
                    // Fall back to the actions list for the message label
                    if (actionIdx < actions.size()) {
                        label = formatSequenceMessageLabel(actions.get(actionIdx++));
                    } else {
                        label = formatSequenceMessageLabel(type);
                    }
                }
                boolean isReturn = type.startsWith("return") || type.startsWith("respond") || type.startsWith("repl")
                        || type.startsWith("confirm") || type.startsWith("acknowledg")
                        || type.startsWith("approv") || type.startsWith("reject") || type.startsWith("deni");
                String msgArrow = isReturn ? "-->" : "->";
                // Emit the arrow first, then manage the activation bar —
                // this matches standard UML: activation starts when the message is received.
                sb.append(src).append(" ").append(msgArrow).append(" ").append(tgt)
                  .append(" : ").append(label).append(NEWLINE);
                if (!isReturn && !src.equals(tgt) && !activated.contains(tgt)) {
                    sb.append("activate ").append(tgt).append(NEWLINE);
                    activated.add(tgt);
                }
                if (isReturn && !src.equals(tgt) && activated.contains(src)) {
                    sb.append("deactivate ").append(src).append(NEWLINE);
                    activated.remove(src);
                }
            }
            // Deactivate any still-active participants
            for (String active : new ArrayList<>(activated)) {
                sb.append("deactivate ").append(active).append(NEWLINE);
            }
        } else if (actions.isEmpty()) {
            // Fallback: generic request/response pairs with activation bars and correct return arrows
            for (String svc : serviceAliases) {
                sb.append(userAlias).append(" ").append(arrow).append(" ").append(svc)
                  .append(" : request").append(NEWLINE);
                sb.append("activate ").append(svc).append(NEWLINE);
                sb.append(svc).append(" --> ").append(userAlias)
                  .append(" : response").append(NEWLINE);
                sb.append("deactivate ").append(svc).append(NEWLINE);
            }
        } else {
            // Distribute actions across participants in a meaningful way:
            //   - First action: User -> first service (activate first service)
            //   - Middle actions: inter-service calls with activate, or self-calls
            //   - Last action (if >1 service): last service --> User (dashed return, deactivate)
            int total = actions.size();
            Set<String> activated = new LinkedHashSet<>();
            for (int i = 0; i < total; i++) {
                String label = formatSequenceMessageLabel(actions.get(i));
                if (i == 0) {
                    // Initiating call: User -> first service
                    String target = serviceAliases.isEmpty() ? userAlias : serviceAliases.get(0);
                    sb.append(userAlias).append(" ").append(arrow).append(" ").append(target)
                      .append(" : ").append(label).append(NEWLINE);
                    if (!serviceAliases.isEmpty()) {
                        sb.append("activate ").append(target).append(NEWLINE);
                        activated.add(target);
                    }
                } else if (i == total - 1 && !serviceAliases.isEmpty()) {
                    // Final return: last service --> User (dashed return arrow)
                    String lastSvc = serviceAliases.get(serviceAliases.size() - 1);
                    sb.append(lastSvc).append(" --> ").append(userAlias)
                      .append(" : ").append(label).append(NEWLINE);
                    sb.append("deactivate ").append(lastSvc).append(NEWLINE);
                    activated.remove(lastSvc);
                } else {
                    // Middle actions: route through services or self-calls
                    if (serviceAliases.size() >= 2 && i < serviceAliases.size()) {
                        // Inter-service call
                        String src = serviceAliases.get(i - 1);
                        String tgt = serviceAliases.get(Math.min(i, serviceAliases.size() - 1));
                        sb.append(src).append(" ").append(arrow).append(" ").append(tgt)
                          .append(" : ").append(label).append(NEWLINE);
                        if (!activated.contains(tgt)) {
                            sb.append("activate ").append(tgt).append(NEWLINE);
                            activated.add(tgt);
                        }
                    } else {
                        // Self-call on the first service
                        String svc = serviceAliases.isEmpty() ? userAlias : serviceAliases.get(0);
                        sb.append(svc).append(" -> ").append(svc)
                          .append(" : ").append(label).append(NEWLINE);
                    }
                }
            }
            // Deactivate any still-active participants
            for (String active : new ArrayList<>(activated)) {
                sb.append("deactivate ").append(active).append(NEWLINE);
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
        Set<String> actors = new LinkedHashSet<>();
        Set<String> useCases = new LinkedHashSet<>();

        for (EntityNode entity : entities) {
            String name = entity.getName();
            if (isUseCaseActorName(name)) actors.add(name);
            else useCases.add(name);
        }
        if (actors.isEmpty() && !entities.isEmpty()) {
            actors.add(entities.get(0).getName());
            useCases.remove(entities.get(0).getName());
        }

        // Remove UML meta-terms and the hardcoded package name "System" — these are
        // documentation noise, not real system use cases.
        useCases.removeIf(uc -> uc != null &&
                USE_CASE_NOISE_TERMS.contains(uc.toLowerCase(Locale.ROOT)));

        for (String action : model.getActions()) {
            if (action != null && !action.isBlank()) {
                String trimmed = action.trim();
                if (!USE_CASE_NOISE_TERMS.contains(trimmed.toLowerCase(Locale.ROOT)))
                    useCases.add(trimmed);
            }
        }
        for (Relationship rel : model.getRelationships()) {
            if (isUseCaseIncludeOrExtend(rel)) {
                useCases.add(rel.getSource());
                useCases.add(rel.getTarget());
            } else if ("association".equalsIgnoreCase(rel.getType())) {
                actors.add(rel.getSource());
                useCases.add(rel.getTarget());
            }
        }

        if (!actors.isEmpty()) {
            for (String actor : actors) {
                if (actor.contains(" ")) {
                    sb.append("actor \"").append(actor).append("\" as ")
                      .append(sanitizeName(actor)).append(NEWLINE);
                } else {
                    sb.append("actor ").append(actor).append(NEWLINE);
                }
            }

            String groupKeyword = layout.getGroupingStyle().toPlantUml();
            sb.append(groupKeyword).append(" \"System\" {").append(NEWLINE);
            for (String useCase : useCases) {
                sb.append(INDENT).append(parenthesizedUseCase(useCase)).append(NEWLINE);
            }
            sb.append("}").append(NEWLINE).append(NEWLINE);

            String associationArrow = layout.getArrowStyle().getNotation();
            Set<String> emittedAssociations = new LinkedHashSet<>();
            for (Relationship rel : model.getRelationships()) {
                if ("association".equalsIgnoreCase(rel.getType())) {
                    String key = rel.getSource().toLowerCase(Locale.ROOT) + "->" + rel.getTarget().toLowerCase(Locale.ROOT);
                    if (emittedAssociations.add(key)) {
                        sb.append(actorRef(rel.getSource())).append(" ").append(associationArrow).append(" ")
                          .append(parenthesizedUseCase(rel.getTarget())).append(NEWLINE);
                    }
                }
            }

            if (emittedAssociations.isEmpty()) {
                String firstActor = actors.iterator().next();
                for (String useCase : useCases) {
                    sb.append(actorRef(firstActor)).append(" ").append(associationArrow).append(" ")
                      .append(parenthesizedUseCase(useCase)).append(NEWLINE);
                }
            }

            for (Relationship rel : model.getRelationships()) {
                if (isUseCaseIncludeOrExtend(rel)) {
                    sb.append(parenthesizedUseCase(rel.getSource())).append(" ..> ")
                      .append(parenthesizedUseCase(rel.getTarget()))
                      .append(" : <<").append(mapUseCaseDependencyType(rel.getType())).append(">>")
                      .append(NEWLINE);
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

        // Node / rank spacing — not applicable to sequence diagrams (PlantUML ignores them)
        if (!"sequence".equals(diagramContext)) {
            sb.append("skinparam nodesep ").append(layout.getNodeSpacing()).append(NEWLINE);
            sb.append("skinparam ranksep ").append(layout.getRankSpacing()).append(NEWLINE);
        }

        // Arrow line style (not used for sequence — arrows are controlled by -> / --> per message)
        if (!"sequence".equals(diagramContext)) {
            switch (layout.getArrowStyle()) {
                case DOTTED -> sb.append("skinparam ").append(diagramContext)
                        .append("ArrowStyle dotted").append(NEWLINE);
                case ASYNC -> sb.append("skinparam ").append(diagramContext)
                        .append("ArrowStyle dashed").append(NEWLINE);
                default -> { /* solid — no override needed */ }
            }
        }

        // Background color with variation
        int v = layout.getBackgroundColorVariation();
        String bgColor = String.format("#%02X%02X%02X", 250 - v, 250 - v, 255);
        sb.append("skinparam ").append(diagramContext).append("BackgroundColor ").append(bgColor).append(NEWLINE);

        // Sequence-diagram-specific skinparams for textbook-quality rendering
        if ("sequence".equals(diagramContext)) {
            sb.append("skinparam sequenceArrowThickness 1.5").append(NEWLINE);
            sb.append("skinparam sequenceMessageAlign center").append(NEWLINE);
            sb.append("skinparam responseMessageBelowArrow true").append(NEWLINE);
            sb.append("skinparam sequenceLifeLineBorderColor #888888").append(NEWLINE);
            sb.append("skinparam sequenceGroupBorderThickness 1.5").append(NEWLINE);
            sb.append("skinparam sequenceGroupBorderColor #888888").append(NEWLINE);
            sb.append("skinparam sequenceGroupHeaderFontStyle bold").append(NEWLINE);
            sb.append("skinparam sequenceGroupBackgroundColor #F0F0FF").append(NEWLINE);
            sb.append("skinparam ParticipantPadding 20").append(NEWLINE);
            sb.append("skinparam BoxPadding 10").append(NEWLINE);
        }
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

    /**
     * Renders a class member (attribute or method) with appropriate visibility.
     * If the member string already starts with a visibility symbol (+, -, #, ~),
     * it is used as-is. Otherwise a convention-based default is applied:
     * getters/setters → public (+), fields → private (-), others → random.
     */
    private String renderClassMember(String member, Random random) {
        String trimmed = member.trim();
        // Already has a visibility marker
        if (!trimmed.isEmpty() && "+-#~".indexOf(trimmed.charAt(0)) >= 0) {
            return trimmed;
        }
        // Detect visibility by naming convention
        String baseName = trimmed.split("[\\s(]")[0];
        String vis;
        if (baseName.startsWith("get") || baseName.startsWith("set") || baseName.startsWith("is")) {
            vis = "+"; // public getter/setter
        } else if (trimmed.contains("(")) {
            vis = "+"; // general public method
        } else if (baseName.equals("id") || baseName.equals("password") || baseName.equals("token")
                || baseName.equals("secret") || baseName.equals("hash")) {
            vis = "-"; // private field
        } else {
            vis = randomVisibility(random);
        }
        return vis + " " + sanitizeName(trimmed);
    }

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
    /** Short display aliases for well-known multi-word participant names. */
    private static final Map<String, String> SEQUENCE_PARTICIPANT_ALIASES = Map.ofEntries(
            Map.entry("Web Server",        "WS"),
            Map.entry("SQL Server",        "SS"),
            Map.entry("Transaction Server","TS"),
            Map.entry("App Server",        "AppS"),
            Map.entry("Auth Server",       "AuthS"),
            Map.entry("Mail Server",       "MS"),
            Map.entry("File Server",       "FS"),
            Map.entry("Cache Server",      "CS"),
            Map.entry("Payment Gateway",   "PG"),
            Map.entry("Cash Dispenser",    "CD"),
            Map.entry("Bank Server",       "BS")
    );

    /** Human/external actor names for sequence diagrams. */
    private static final Set<String> SEQUENCE_ACTOR_NAMES = Set.of(
            "user", "admin", "administrator", "client", "customer",
            "operator", "actor", "guest", "member", "visitor", "person");

    /**
     * Single-word UML meta-terms and documentation keywords that the semantic extractor
     * may pick up from documentation PDFs but that should never appear as use case names.
     * "system" is included because it conflicts with the hardcoded {@code package "System"} block.
     */
    private static final Set<String> USE_CASE_NOISE_TERMS = Set.of(
            "system", "use", "case", "diagram", "documentation", "uml",
            "overview", "description", "example", "template", "model",
            "boundary", "include", "extend", "generalization", "association",
            "relationship", "interaction", "note", "actor");

    /**
     * Returns the PlantUML alias for a sequence participant.
     * Checks the predefined alias map first; falls back to sanitizeName().
     */
    private String sequenceParticipantAlias(String name) {
        String predefined = SEQUENCE_PARTICIPANT_ALIASES.get(name);
        return predefined != null ? predefined : sanitizeName(name);
    }

    private String determineSequenceParticipantType(String entityName) {
        String lower = entityName.toLowerCase(Locale.ROOT);
        // External human actors
        if (SEQUENCE_ACTOR_NAMES.contains(lower)) {
            return "actor";
        }
        // Data store components
        if (lower.endsWith("database") || lower.endsWith("db") || lower.endsWith("store")
                || lower.endsWith("cache") || lower.endsWith("storage")) {
            return "database";
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

    private String generateCollaborationDiagram(SemanticModel model) {
        List<EntityNode> entities = model.getEntities();
        List<String> actions = model.getActions();

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (entities == null || entities.isEmpty()) {
            sb.append("object Client\n");
            sb.append("object Server\n");
            sb.append("object Database\n");
            sb.append("\n");
            sb.append("Client --> Server : 1. request\n");
            sb.append("Server --> Database : 2. query\n");
            sb.append("Database --> Server : 3. result\n");
            sb.append("Server --> Client : 4. response\n");
        } else {
            for (EntityNode entity : entities) {
                sb.append("object ").append(sanitizeName(entity.getName())).append("\n");
            }
            sb.append("\n");
            for (int i = 0; i < entities.size() - 1; i++) {
                String label = (actions != null && i < actions.size()) ? actions.get(i) : "message";
                sb.append(sanitizeName(entities.get(i).getName()))
                  .append(" --> ")
                  .append(sanitizeName(entities.get(i + 1).getName()))
                  .append(" : ").append(i + 1).append(". ").append(label)
                  .append("\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String sanitizeName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String actorRef(String actor) {
        if (actor == null) return "Unknown";
        return actor.contains(" ") ? sanitizeName(actor) : actor;
    }

    private boolean isUseCaseActorName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return Set.of(
                "actor", "user", "admin", "administrator", "guest", "student",
                "professor", "teacher", "parent", "customer", "client", "visitor",
                "moderator", "registrar", "librarian", "instructor", "operator",
                "bank customer", "maintenance technician", "billing system",
                "payment gateway", "bank server", "cash dispenser", "search engine"
        ).contains(lower)
                || lower.endsWith(" system")
                || lower.endsWith(" gateway")
                || lower.endsWith(" server")
                || lower.endsWith(" dispenser")
                || lower.endsWith(" engine")
                || lower.endsWith(" technician");
    }

    private String formatSequenceMessageLabel(String action) {
        if (action == null || action.isBlank()) return "request";
        String trimmed = action.trim();
        if (trimmed.contains("(") || trimmed.contains(" ") || trimmed.contains(":")) {
            return trimmed;
        }
        return trimmed + "()";
    }

    private boolean isUseCaseIncludeOrExtend(Relationship rel) {
        if (rel == null || rel.getType() == null) return false;
        String type = rel.getType().toLowerCase(Locale.ROOT);
        return "include".equals(type)
                || "includes".equals(type)
                || "extend".equals(type)
                || "extends".equals(type)
                || "<<include>>".equals(type)
                || "<<extend>>".equals(type);
    }

    private String mapUseCaseDependencyType(String type) {
        if (type == null) return "include";
        String normalized = type.toLowerCase(Locale.ROOT).replaceAll("[<>\\s]", "");
        if (normalized.startsWith("extend")) return "extend";
        return "include";
    }

    private String parenthesizedUseCase(String useCase) {
        return "(" + toUseCaseLabel(useCase) + ")";
    }

    private String toUseCaseLabel(String value) {
        if (value == null || value.isBlank()) return "Use Case";
        String cleaned = value.trim().replaceAll("_", " ").replaceAll("\\s+", " ");
        String[] words = cleaned.split("\\s+");
        List<String> titled = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isBlank()) {
                titled.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", titled);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
