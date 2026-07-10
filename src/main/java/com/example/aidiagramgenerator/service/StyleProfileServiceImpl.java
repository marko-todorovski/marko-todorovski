package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.StyleProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Implementation of StyleProfileService providing predefined style profiles
 * for each diagram type. These profiles define layout direction, arrow styles,
 * and spacing rules used during diagram generation.
 */
@Service
public class StyleProfileServiceImpl implements StyleProfileService {

    private static final Logger logger = LoggerFactory.getLogger(StyleProfileServiceImpl.class);

    /**
     * Layout direction constants for PlantUML.
     */
    public static final String LAYOUT_TOP_TO_BOTTOM = "top to bottom direction";
    public static final String LAYOUT_LEFT_TO_RIGHT = "left to right direction";

    /**
     * Arrow style definitions for PlantUML.
     */
    public static final String ARROW_SOLID = "solid";
    public static final String ARROW_DASHED = "dashed";
    public static final String ARROW_DOTTED = "dotted";
    public static final String ARROW_BOLD = "bold";

    /**
     * Spacing rule definitions.
     */
    public static final String SPACING_COMPACT = "compact";
    public static final String SPACING_NORMAL = "normal";
    public static final String SPACING_EXPANDED = "expanded";

    /**
     * Predefined style profiles for each diagram type.
     */
    private final Map<DiagramType, StyleProfile> styleProfiles;

    public StyleProfileServiceImpl() {
        this.styleProfiles = initializeStyleProfiles();
        logger.info("StyleProfileService initialized with {} predefined profiles", styleProfiles.size());
    }

    @Override
    public StyleProfile getStyleProfile(DiagramType type) {
        if (type == null) {
            logger.error("Cannot retrieve style profile: diagram type is null");
            throw new IllegalArgumentException("Diagram type cannot be null");
        }

        StyleProfile profile = styleProfiles.get(type);
        if (profile == null) {
            logger.warn("No predefined style profile for type: {}, returning default", type);
            return createDefaultProfile(type);
        }

        logger.debug("Retrieved style profile for type: {}", type);
        return profile;
    }

    /**
     * Initializes predefined style profiles for all diagram types.
     *
     * @return map of diagram types to their style profiles
     */
    private Map<DiagramType, StyleProfile> initializeStyleProfiles() {
        Map<DiagramType, StyleProfile> profiles = new EnumMap<>(DiagramType.class);

        // Class Diagram - Top to bottom, solid arrows, normal spacing
        profiles.put(DiagramType.CLASS, new StyleProfile(
                DiagramType.CLASS,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_SOLID,
                SPACING_NORMAL
        ));

        // ER Diagram - Left to right for readability, solid arrows, expanded spacing
        profiles.put(DiagramType.ER, new StyleProfile(
                DiagramType.ER,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_SOLID,
                SPACING_EXPANDED
        ));

        // Sequence Diagram - Top to bottom (natural flow), dashed for async, compact
        profiles.put(DiagramType.SEQUENCE, new StyleProfile(
                DiagramType.SEQUENCE,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_DASHED,
                SPACING_COMPACT
        ));

        // Use Case Diagram - Left to right, solid arrows, normal spacing
        profiles.put(DiagramType.USE_CASE, new StyleProfile(
                DiagramType.USE_CASE,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_SOLID,
                SPACING_NORMAL
        ));

        // Component Diagram - Top to bottom for hierarchy, dotted for dependencies
        profiles.put(DiagramType.COMPONENT, new StyleProfile(
                DiagramType.COMPONENT,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_DOTTED,
                SPACING_NORMAL
        ));

        // Deployment Diagram - Left to right for infrastructure flow, bold arrows
        profiles.put(DiagramType.DEPLOYMENT, new StyleProfile(
                DiagramType.DEPLOYMENT,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_BOLD,
                SPACING_EXPANDED
        ));

        // Activity Diagram - Top to bottom (natural flow), solid arrows, normal spacing
        profiles.put(DiagramType.ACTIVITY, new StyleProfile(
                DiagramType.ACTIVITY,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_SOLID,
                SPACING_NORMAL
        ));

        // State Diagram - Top to bottom for state transitions, solid arrows, compact
        profiles.put(DiagramType.STATE, new StyleProfile(
                DiagramType.STATE,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_SOLID,
                SPACING_COMPACT
        ));

        // Object Diagram - Left to right to show instances, solid arrows, normal spacing
        profiles.put(DiagramType.OBJECT, new StyleProfile(
                DiagramType.OBJECT,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_SOLID,
                SPACING_NORMAL
        ));

        // Microservices Diagram - Left to right for service topology, dotted for async, expanded
        profiles.put(DiagramType.MICROSERVICES, new StyleProfile(
                DiagramType.MICROSERVICES,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_DOTTED,
                SPACING_EXPANDED
        ));

        // Collaboration Diagram - Left to right for interaction, dashed arrows, normal
        profiles.put(DiagramType.COLLABORATION, new StyleProfile(
                DiagramType.COLLABORATION,
                LAYOUT_LEFT_TO_RIGHT,
                ARROW_DASHED,
                SPACING_NORMAL
        ));

        return profiles;
    }

    /**
     * Creates a default style profile for diagram types without predefined styles.
     *
     * @param type the diagram type
     * @return a default StyleProfile
     */
    private StyleProfile createDefaultProfile(DiagramType type) {
        return new StyleProfile(
                type,
                LAYOUT_TOP_TO_BOTTOM,
                ARROW_SOLID,
                SPACING_NORMAL
        );
    }
}
