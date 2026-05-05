package com.example.aidiagramgenerator.domain;

/**
 * Encapsulates all randomizable layout parameters for PlantUML diagram generation.
 * Each generation can produce a unique LayoutProfile for visual variation,
 * or produce a deterministic one when a seed is provided.
 *
 * <p>This is a value object — immutable after construction.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public final class LayoutProfile {

    // ─── Layout direction ─────────────────────────────────────────────────────

    /**
     * Supported PlantUML layout directions.
     */
    public enum Direction {
        LEFT_TO_RIGHT("left to right direction"),
        TOP_TO_BOTTOM("top to bottom direction");

        private final String plantUmlDirective;

        Direction(String plantUmlDirective) {
            this.plantUmlDirective = plantUmlDirective;
        }

        public String toPlantUml() {
            return plantUmlDirective;
        }
    }

    // ─── Arrow style ──────────────────────────────────────────────────────────

    /**
     * Supported PlantUML arrow styles.
     */
    public enum ArrowStyle {
        SOLID("-->", "solid"),
        ASYNC("->>", "async"),
        DOTTED("..>", "dotted");

        private final String notation;
        private final String label;

        ArrowStyle(String notation, String label) {
            this.notation = notation;
            this.label = label;
        }

        public String getNotation() {
            return notation;
        }

        public String getLabel() {
            return label;
        }
    }

    // ─── Grouping style ───────────────────────────────────────────────────────

    /**
     * Controls how elements are grouped in the diagram.
     */
    public enum GroupingStyle {
        RECTANGLE("rectangle"),
        PACKAGE("package"),
        FOLDER("folder"),
        FRAME("frame"),
        CLOUD("cloud");

        private final String plantUmlKeyword;

        GroupingStyle(String plantUmlKeyword) {
            this.plantUmlKeyword = plantUmlKeyword;
        }

        public String toPlantUml() {
            return plantUmlKeyword;
        }
    }

    // ─── Note position ────────────────────────────────────────────────────────

    /**
     * Supported positions for notes in PlantUML diagrams.
     */
    public enum NotePosition {
        TOP("top"),
        BOTTOM("bottom"),
        LEFT("left"),
        RIGHT("right");

        private final String plantUmlValue;

        NotePosition(String plantUmlValue) {
            this.plantUmlValue = plantUmlValue;
        }

        public String toPlantUml() {
            return plantUmlValue;
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final Direction direction;
    private final int nodeSpacing;
    private final int rankSpacing;
    private final ArrowStyle arrowStyle;
    private final GroupingStyle groupingStyle;
    private final NotePosition notePosition;
    private final int backgroundColorVariation;
    private final Long seed;

    // ─── Constructor (package-private, use Builder) ───────────────────────────

    private LayoutProfile(Direction direction, int nodeSpacing, int rankSpacing,
                          ArrowStyle arrowStyle, GroupingStyle groupingStyle,
                          NotePosition notePosition, int backgroundColorVariation, Long seed) {
        this.direction = direction;
        this.nodeSpacing = nodeSpacing;
        this.rankSpacing = rankSpacing;
        this.arrowStyle = arrowStyle;
        this.groupingStyle = groupingStyle;
        this.notePosition = notePosition;
        this.backgroundColorVariation = backgroundColorVariation;
        this.seed = seed;
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Direction direction = Direction.TOP_TO_BOTTOM;
        private int nodeSpacing = 50;
        private int rankSpacing = 50;
        private ArrowStyle arrowStyle = ArrowStyle.SOLID;
        private GroupingStyle groupingStyle = GroupingStyle.RECTANGLE;
        private NotePosition notePosition = NotePosition.RIGHT;
        private int backgroundColorVariation = 0;
        private Long seed;

        public Builder direction(Direction direction) {
            this.direction = direction;
            return this;
        }

        public Builder nodeSpacing(int nodeSpacing) {
            this.nodeSpacing = nodeSpacing;
            return this;
        }

        public Builder rankSpacing(int rankSpacing) {
            this.rankSpacing = rankSpacing;
            return this;
        }

        public Builder arrowStyle(ArrowStyle arrowStyle) {
            this.arrowStyle = arrowStyle;
            return this;
        }

        public Builder groupingStyle(GroupingStyle groupingStyle) {
            this.groupingStyle = groupingStyle;
            return this;
        }

        public Builder notePosition(NotePosition notePosition) {
            this.notePosition = notePosition;
            return this;
        }

        public Builder backgroundColorVariation(int variation) {
            this.backgroundColorVariation = variation;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public LayoutProfile build() {
            return new LayoutProfile(direction, nodeSpacing, rankSpacing,
                    arrowStyle, groupingStyle, notePosition, backgroundColorVariation, seed);
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public Direction getDirection() {
        return direction;
    }

    public int getNodeSpacing() {
        return nodeSpacing;
    }

    public int getRankSpacing() {
        return rankSpacing;
    }

    public ArrowStyle getArrowStyle() {
        return arrowStyle;
    }

    public GroupingStyle getGroupingStyle() {
        return groupingStyle;
    }

    public NotePosition getNotePosition() {
        return notePosition;
    }

    public int getBackgroundColorVariation() {
        return backgroundColorVariation;
    }

    public Long getSeed() {
        return seed;
    }

    @Override
    public String toString() {
        return "LayoutProfile{" +
                "direction=" + direction +
                ", nodeSpacing=" + nodeSpacing +
                ", rankSpacing=" + rankSpacing +
                ", arrowStyle=" + arrowStyle +
                ", groupingStyle=" + groupingStyle +
                ", notePosition=" + notePosition +
                ", seed=" + seed +
                '}';
    }
}
