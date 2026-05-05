package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UrlDiagramRequest;
import com.example.aidiagramgenerator.dto.request.XmlDiagramRequest;
import com.example.aidiagramgenerator.dto.response.DiagramResponse;

/**
 * Service interface for diagram generation operations
 */
public interface DiagramService {

    /**
     * Generate diagram from natural language text description
     * 
     * @param request TextDiagramRequest containing the text description
     * @return DiagramResponse with generated diagram code
     */
    DiagramResponse generateFromText(TextDiagramRequest request);

    /**
     * Generate diagram from XML input
     * 
     * @param request XmlDiagramRequest containing the XML content
     * @return DiagramResponse with generated diagram code
     */
    DiagramResponse generateFromXml(XmlDiagramRequest request);

    /**
     * Generate diagram from URL (e.g., GitHub repository)
     * 
     * @param request UrlDiagramRequest containing the URL
     * @return DiagramResponse with generated diagram code
     */
    DiagramResponse generateFromUrl(UrlDiagramRequest request);
}
