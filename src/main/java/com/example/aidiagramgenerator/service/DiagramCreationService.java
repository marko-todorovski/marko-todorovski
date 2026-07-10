package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.service.DiagramGenerationService.DiagramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DiagramCreationService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramCreationService.class);

    private final DiagramGenerationService diagramGenerationService;
    private final DiagramRepository diagramRepository;

    public DiagramCreationService(DiagramGenerationService diagramGenerationService,
                                  DiagramRepository diagramRepository) {
        this.diagramGenerationService = diagramGenerationService;
        this.diagramRepository = diagramRepository;
    }

    @Transactional
    public DiagramResponse generateAndSave(InputType inputType,
                                           String inputContent,
                                           String textForGeneration,
                                           DiagramType requestedType) {
        DiagramResult result = diagramGenerationService.generateFromText(textForGeneration, requestedType);
        return save(inputType, inputContent, result);
    }

    @Transactional
    public DiagramResponse save(InputType inputType, String inputContent, DiagramResult result) {
        Diagram diagram = new Diagram(
                inputType,
                inputContent,
                result.getDiagramType(),
                result.getMermaidCode(),
                result.getExplanation());

        Optional<Diagram> existingOriginal = diagramRepository
                .findOriginalByInputContentAndInputType(inputContent, inputType);

        if (existingOriginal.isPresent()) {
            int maxVersion = diagramRepository
                    .findMaxVersionByInputContentAndInputType(inputContent, inputType)
                    .orElse(1);
            diagram.setVersionNumber(maxVersion + 1);
            diagram.setParentDiagramId(existingOriginal.get().getId());
            logger.info("Creating new version {} for diagram with parent id={}",
                    diagram.getVersionNumber(), existingOriginal.get().getId());
        } else {
            diagram.setVersionNumber(1);
            diagram.setParentDiagramId(null);
        }

        diagram = diagramRepository.save(diagram);
        logger.info("Diagram persisted with id={}, version={}", diagram.getId(), diagram.getVersionNumber());

        DiagramResponse response = new DiagramResponse(
                result.getDiagramType(),
                result.getMermaidCode(),
                result.getExplanation(),
                result.getDetectedKeywords(),
                result.getRulesTriggered());
        response.setId(diagram.getId());
        response.setGenerationMode(result.getGenerationMode());
        return response;
    }
}
