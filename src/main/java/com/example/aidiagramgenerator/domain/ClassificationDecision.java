package com.example.aidiagramgenerator.domain;

/**
 * Represents the action the system should take based on classification confidence.
 *
 * <ul>
 *   <li>{@link #AUTO}    — confidence ≥ 70%: proceed and auto-generate the diagram</li>
 *   <li>{@link #SUGGEST} — confidence 40–69%: return a suggestion asking the user to confirm</li>
 *   <li>{@link #CLARIFY} — confidence &lt; 40%: request more descriptive input from the user</li>
 * </ul>
 */
public enum ClassificationDecision {

    /**
     * Confidence is high enough to generate the diagram automatically (≥ 70%).
     */
    AUTO,

    /**
     * Confidence is moderate; suggest a diagram type to the user and ask for confirmation (40–69%).
     */
    SUGGEST,

    /**
     * Confidence is too low to proceed; ask the user for more information (&lt; 40%).
     */
    CLARIFY
}
