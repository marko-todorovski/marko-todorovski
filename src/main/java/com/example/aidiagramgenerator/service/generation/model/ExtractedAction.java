package com.example.aidiagramgenerator.service.generation.model;

import java.util.Objects;

/**
 * Represents an extracted action (verb) from natural language text.
 * Actions are verbs that describe what entities do.
 */
public class ExtractedAction {

    /** The verb/action in its base (lemmatized) form. */
    private final String verb;

    /** The original verb as it appeared in the text. */
    private final String originalText;

    /** The subject performing the action (may be null). */
    private final String subject;

    /** The object receiving the action (may be null). */
    private final String object;

    /** The tense of the verb (e.g., "present", "past", "future"). */
    private final String tense;

    /** Whether this is a passive voice construction. */
    private final boolean passive;

    /** Confidence score from the NLP model (0.0 to 1.0). */
    private final double confidence;

    public ExtractedAction(String verb, String originalText, String subject, String object,
                           String tense, boolean passive, double confidence) {
        this.verb = verb;
        this.originalText = originalText;
        this.subject = subject;
        this.object = object;
        this.tense = tense;
        this.passive = passive;
        this.confidence = confidence;
    }

    /**
     * Convenience constructor for simple actions.
     */
    public ExtractedAction(String verb, String subject, String object) {
        this(verb, verb, subject, object, "present", false, 1.0);
    }

    public String getVerb() {
        return verb;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getSubject() {
        return subject;
    }

    public String getObject() {
        return object;
    }

    public String getTense() {
        return tense;
    }

    public boolean isPassive() {
        return passive;
    }

    public double getConfidence() {
        return confidence;
    }

    /**
     * Returns a formatted description of the action.
     * Example: "User creates Order"
     */
    public String toDescription() {
        StringBuilder sb = new StringBuilder();
        if (subject != null) {
            sb.append(subject).append(" ");
        }
        sb.append(verb);
        if (object != null) {
            sb.append(" ").append(object);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractedAction that = (ExtractedAction) o;
        return Objects.equals(verb, that.verb) &&
               Objects.equals(subject, that.subject) &&
               Objects.equals(object, that.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verb, subject, object);
    }

    @Override
    public String toString() {
        return "ExtractedAction{" +
                "verb='" + verb + '\'' +
                ", subject='" + subject + '\'' +
                ", object='" + object + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
