package com.example.aidiagramgenerator.service.generation.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A single numbered message in a UML collaboration diagram, representing one
 * directed interaction between two participants.
 *
 * <h3>Sequence number format</h3>
 * <p>Sequence numbers follow the standard UML collaboration diagram convention and
 * support arbitrary nesting depth via dot-separated integer segments:
 * <pre>
 *   1          – top-level message
 *   1.1        – first nested call within message 1
 *   1.2        – second nested call within message 1
 *   1.2.1      – doubly nested call
 *   2          – second top-level message
 * </pre>
 *
 * <h3>Source / target</h3>
 * <p>{@code source} and {@code target} are optional — they are populated when the
 * input text provides an explicit arrow ({@code User --> WebServer : 1.1: login()}),
 * and left {@code null} when only a bare numbered label was found
 * ({@code 1.1: createSQLQuery()}).
 */
public final class CollaborationMessage {

    /**
     * Orders {@link CollaborationMessage} instances by their hierarchical sequence number.
     * Comparison is performed segment-by-segment, numerically:
     * {@code "1" < "1.1" < "1.2" < "2" < "2.1"}.
     */
    public static final Comparator<CollaborationMessage> SEQUENCE_ORDER =
            Comparator.comparing(CollaborationMessage::getParts, CollaborationMessage::comparePartLists);

    /** Full hierarchical sequence number string, e.g. {@code "1"}, {@code "1.1"}, {@code "2.3.1"}. */
    private final String sequenceNumber;

    /**
     * Human-readable message label, e.g. {@code "searchMessage()"},
     * {@code "createSQLQuery()"}, {@code "request"}.
     */
    private final String label;

    /**
     * Source participant name (PascalCase, PlantUML-safe).
     * {@code null} when not present in the input.
     */
    private final String source;

    /**
     * Target participant name (PascalCase, PlantUML-safe).
     * {@code null} when not present in the input.
     */
    private final String target;

    /**
     * Optional UML guard condition enclosed in square brackets, e.g. {@code "amount > 1000"}.
     * {@code null} when the message is unconditional.
     */
    private final String condition;

    /**
     * Full constructor.
     *
     * @param sequenceNumber hierarchical sequence string, e.g. {@code "1.1"}
     * @param label          message label; must not be blank
     * @param source         source participant name, or {@code null}
     * @param target         target participant name, or {@code null}
     * @param condition      guard condition text without brackets, or {@code null}
     */
    public CollaborationMessage(String sequenceNumber, String label,
                                String source, String target, String condition) {
        if (sequenceNumber == null || sequenceNumber.isBlank()) {
            throw new IllegalArgumentException("Sequence number must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Message label must not be blank");
        }
        this.sequenceNumber = sequenceNumber.trim();
        this.label          = label.trim();
        this.source         = (source == null || source.isBlank()) ? null : source.trim();
        this.target         = (target == null || target.isBlank()) ? null : target.trim();
        this.condition      = (condition == null || condition.isBlank()) ? null : condition.trim();
    }

    /**
     * Convenience constructor for messages without a guard condition.
     *
     * @param sequenceNumber hierarchical sequence string, e.g. {@code "1.1"}
     * @param label          message label; must not be blank
     * @param source         source participant name, or {@code null}
     * @param target         target participant name, or {@code null}
     */
    public CollaborationMessage(String sequenceNumber, String label, String source, String target) {
        this(sequenceNumber, label, source, target, null);
    }

    /**
     * Convenience constructor for bare messages without an explicit source or target.
     *
     * @param sequenceNumber hierarchical sequence string, e.g. {@code "1.1"}
     * @param label          message label; must not be blank
     */
    public CollaborationMessage(String sequenceNumber, String label) {
        this(sequenceNumber, label, null, null, null);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** @return the full sequence number string, e.g. {@code "1.2"} */
    public String getSequenceNumber() { return sequenceNumber; }

    /** @return the message label */
    public String getLabel() { return label; }

    /**
     * @return the source participant name, or {@code null} if not detected
     */
    public String getSource() { return source; }

    /**
     * @return the target participant name, or {@code null} if not detected
     */
    public String getTarget() { return target; }

    /**
     * @return the guard condition text (without brackets), or {@code null} if unconditional.
     *         e.g. {@code "amount > 1000"} for input {@code [amount > 1000]}
     */
    public String getCondition() { return condition; }

    // ── Derived properties ────────────────────────────────────────────────

    /**
     * Returns the nesting depth of this message.
     * {@code "1"} → depth 1; {@code "1.1"} → depth 2; {@code "1.1.1"} → depth 3.
     *
     * @return nesting depth, always ≥ 1
     */
    public int getDepth() {
        return sequenceNumber.split("\\.").length;
    }

    /**
     * Parses the sequence number into an ordered list of integer segments for
     * hierarchical comparison.
     * e.g. {@code "1.2.3"} → {@code [1, 2, 3]}.
     *
     * @return list of integer segments; non-numeric segments are mapped to 0
     */
    public List<Integer> getParts() {
        return Arrays.stream(sequenceNumber.split("\\."))
                .map(s -> {
                    try { return Integer.parseInt(s); }
                    catch (NumberFormatException e) { return 0; }
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} when this message has both a source and a target participant.
     *
     * @return {@code true} if {@link #getSource()} and {@link #getTarget()} are non-null
     */
    public boolean hasParticipants() {
        return source != null && target != null;
    }

    /**
     * Returns {@code true} when this message carries a UML guard condition.
     *
     * @return {@code true} if {@link #getCondition()} is non-null
     */
    public boolean isConditional() {
        return condition != null;
    }

    /**
     * Returns {@code true} when this is a self-call — a message where the source and
     * target are the same object (e.g. {@code WebServer --> WebServer : 1.1: createSQLQuery()}).
     *
     * @return {@code true} if source and target are non-null and equal
     */
    public boolean isSelfCall() {
        return source != null && source.equals(target);
    }

    /**
     * Formats this message as a single PlantUML arrow line.
     * Falls back to {@code "Unknown"} for absent source / target.
     *
     * <p>Example: {@code "User -> WebServer : 5.1 [amount > 1000] askForConfirmation()"}
     *
     * @return PlantUML arrow line
     */
    public String toPlantUml() {
        String src  = source != null ? source : "Unknown";
        String tgt  = target != null ? target : "Unknown";
        String cond = condition != null ? "[" + condition + "] " : "";
        return src + " -> " + tgt + " : " + sequenceNumber + " " + cond + label;
    }

    // ── Ordering ──────────────────────────────────────────────────────────

    /**
     * Compares two parsed-parts lists segment by segment, numerically.
     * A shorter list that is a prefix of a longer list sorts first
     * (e.g. {@code [1]} before {@code [1,1]}).
     */
    private static int comparePartLists(List<Integer> a, List<Integer> b) {
        int limit = Math.min(a.size(), b.size());
        for (int i = 0; i < limit; i++) {
            int cmp = Integer.compare(a.get(i), b.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.size(), b.size());
    }

    // ── Object overrides ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollaborationMessage that = (CollaborationMessage) o;
        return Objects.equals(sequenceNumber, that.sequenceNumber)
                && Objects.equals(label, that.label)
                && Objects.equals(source, that.source)
                && Objects.equals(target, that.target)
                && Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, label, source, target, condition);
    }

    @Override
    public String toString() {
        return "CollaborationMessage{seq='" + sequenceNumber + "', label='" + label +
               (condition != null ? "', condition='" + condition : "") +
               "', source=" + source + ", target=" + target + '}';
    }
}
