package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.exception.DiagramAccessDeniedException;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidDiagramException;
import com.example.aidiagramgenerator.exception.ProjectNotFoundException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SavedDiagramServiceImpl implements SavedDiagramService {

    private static final int MAX_DIAGRAM_NAME_LENGTH = 150;
    private static final int MAX_DIAGRAM_DESCRIPTION_LENGTH = 1000;

    private final ApplicationUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DomainDiagramRepository diagramRepository;
    private final DiagramVersionRepository versionRepository;
    private final DiagramVersionService diagramVersionService;
    private final ProjectAccessService projectAccessService;

    public SavedDiagramServiceImpl(
            ApplicationUserRepository userRepository,
            ProjectRepository projectRepository,
            DomainDiagramRepository diagramRepository,
            DiagramVersionRepository versionRepository,
            DiagramVersionService diagramVersionService,
            ProjectAccessService projectAccessService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.diagramRepository = diagramRepository;
        this.versionRepository = versionRepository;
        this.diagramVersionService = diagramVersionService;
        this.projectAccessService = projectAccessService;
    }

    @Override
    @Transactional
    public Diagram saveGeneratedDiagram(
            UUID ownerId,
            UUID projectId,
            String name,
            String description,
            String originalPrompt,
            DiagramType diagramType,
            DiagramSourceFormat sourceFormat,
            String sourceCode,
            String modelUsed) {
        ApplicationUser owner = requireUser(ownerId);
        Project project = requireEditableProject(projectId, owner.getId());
        String normalizedName = normalizeName(name);
        String requiredSource = requireSourceCode(sourceCode);
        DiagramType requiredDiagramType = requireDiagramType(diagramType);
        DiagramSourceFormat requiredSourceFormat = requireSourceFormat(sourceFormat);
        if (requiredSourceFormat == DiagramSourceFormat.MERMAID) {
            throw new InvalidDiagramException(
                    "Saving new Mermaid diagrams is not supported until plant_uml_code is nullable");
        }
        String prompt = trimToNull(originalPrompt);
        String inputText = prompt != null ? prompt : normalizedName;

        Diagram diagram = new Diagram(inputText, requiredDiagramType, requiredSource);
        diagram.setOwner(owner);
        diagram.setProject(project);
        diagram.setName(normalizedName);
        diagram.setDescription(normalizeOptionalText(description, MAX_DIAGRAM_DESCRIPTION_LENGTH, "Diagram description"));
        diagram.setOriginalPrompt(prompt);
        diagram.setCurrentSourceCode(requiredSource);
        diagram.setSourceFormat(requiredSourceFormat);
        diagram.setModelUsed(trimToNull(modelUsed));
        diagram.setCurrentVersionNumber(null);

        Diagram savedDiagram = diagramRepository.saveAndFlush(diagram);
        diagramVersionService.createInitialVersion(savedDiagram.getId(), owner.getId());
        return diagramRepository.findById(savedDiagram.getId())
                .orElseThrow(() -> new DiagramNotFoundException("Saved diagram not found after version creation"));
    }

    @Override
    @Transactional
    public Diagram attachExistingGeneratedDiagram(
            UUID ownerId,
            UUID projectId,
            UUID diagramId,
            String name,
            String description) {
        ApplicationUser owner = requireUser(ownerId);
        Project project = requireEditableProject(projectId, owner.getId());
        Diagram diagram = diagramRepository.findById(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        if (diagram.getOwner() != null && !owner.getId().equals(diagram.getOwner().getId())) {
            throw new DiagramAccessDeniedException("Diagram is already owned by another user");
        }

        diagram.setOwner(owner);
        diagram.setProject(project);
        diagram.setName(normalizeName(name));
        diagram.setDescription(normalizeOptionalText(description, MAX_DIAGRAM_DESCRIPTION_LENGTH, "Diagram description"));
        if (diagram.getSourceFormat() == null) {
            diagram.setSourceFormat(DiagramSourceFormat.PLANTUML);
        }
        if (diagram.getOriginalPrompt() == null || diagram.getOriginalPrompt().isBlank()) {
            diagram.setOriginalPrompt(diagram.getInputText());
        }
        if (diagram.getCurrentSourceCode() == null || diagram.getCurrentSourceCode().isBlank()) {
            diagram.setCurrentSourceCode(diagram.getPlantUmlCode());
        }
        Diagram savedDiagram = diagramRepository.saveAndFlush(diagram);

        if (versionRepository.findMaximumVersionNumber(savedDiagram.getId()).isEmpty()) {
            diagramVersionService.createInitialVersion(savedDiagram.getId(), owner.getId());
        }
        return diagramRepository.findById(savedDiagram.getId())
                .orElseThrow(() -> new DiagramNotFoundException("Attached diagram not found after version creation"));
    }

    @Override
    @Transactional(readOnly = true)
    public Diagram getDiagramForOwner(UUID diagramId, UUID ownerId) {
        Diagram diagram = diagramRepository.findById(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        projectAccessService.requireDiagramViewer(diagram, requireId(ownerId, "Owner ID"));
        return diagram;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Diagram> getProjectDiagrams(UUID projectId, UUID ownerId) {
        projectAccessService.requireProjectViewer(projectId, ownerId);
        return diagramRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId);
    }

    @Override
    @Transactional
    public Diagram updateDiagramMetadata(UUID diagramId, UUID ownerId, String name, String description) {
        Diagram diagram = diagramRepository.findById(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        projectAccessService.requireDiagramEditor(diagram, ownerId);
        diagram.setName(normalizeName(name));
        diagram.setDescription(normalizeOptionalText(description, MAX_DIAGRAM_DESCRIPTION_LENGTH, "Diagram description"));
        return diagramRepository.save(diagram);
    }

    @Override
    @Transactional
    public void deleteDiagram(UUID diagramId, UUID ownerId) {
        Diagram diagram = diagramRepository.findById(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        projectAccessService.requireDiagramEditor(diagram, ownerId);
        diagramRepository.delete(diagram);
    }

    private ApplicationUser requireUser(UUID ownerId) {
        return userRepository.findById(requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + ownerId));
    }

    private Project requireEditableProject(UUID projectId, UUID ownerId) {
        projectAccessService.requireProjectEditor(projectId, ownerId);
        return projectRepository.findById(requireId(projectId, "Project ID"))
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidDiagramException("Diagram name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_DIAGRAM_NAME_LENGTH) {
            throw new InvalidDiagramException("Diagram name must be 150 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new InvalidDiagramException(fieldName + " is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidDiagramException("Diagram source code is required");
        }
        return sourceCode;
    }

    private static DiagramType requireDiagramType(DiagramType diagramType) {
        if (diagramType == null) {
            throw new InvalidDiagramException("Diagram type is required");
        }
        return diagramType;
    }

    private static DiagramSourceFormat requireSourceFormat(DiagramSourceFormat sourceFormat) {
        if (sourceFormat == null) {
            throw new InvalidDiagramException("Diagram source format is required");
        }
        return sourceFormat;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new InvalidDiagramException(fieldName + " is required");
        }
        return id;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
