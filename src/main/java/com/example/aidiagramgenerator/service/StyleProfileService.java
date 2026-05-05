package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.StyleProfile;

/**
 * Service interface for retrieving style profiles for diagram generation.
 * Style profiles define visual and layout properties for specific diagram types.
 */
public interface StyleProfileService {

    /**
     * Retrieves the style profile for the specified diagram type.
     *
     * @param type the diagram type
     * @return the StyleProfile for the given type
     * @throws IllegalArgumentException if type is null
     */
    StyleProfile getStyleProfile(DiagramType type);
}
