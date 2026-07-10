package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.exception.DiagramAccessDeniedException;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.DiagramVersionNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidDiagramVersionException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class DiagramVersionServiceImpl implements DiagramVersionService {

    private final DomainDiagramRepository diagramRepository;
    private final DiagramVersionRepository versionRepository;
    private final ApplicationUserRepository userRepository;

    public DiagramVersionServiceImpl(
            DomainDiagramRepository diagramRepository,
            DiagramVersionRepository versionRepository,
            ApplicationUserRepository userRepository) {
        this.diagramRepository = diagramRepository;
        this.versionRepository = versionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public DiagramVersion createInitialVersion(UUID diagramId, UUID ownerId) {
        ApplicationUser owner = requireUser(ownerId);
        Diagram lockedDiagram = lockOwnedDiagram(diagramId, owner.getId());
        Optional<Integer> maximumVersion = versionRepository.findMaximumVersionNumber(lockedDiagram.getId());
        if (maximumVersion.isPresent()) {
            return versionRepository.findByDiagramIdAndVersionNumber(lockedDiagram.getId(), 1)
                    .orElseThrow(() -> new InvalidDiagramVersionException(
                            "Diagram already has versions but no initial version"));
        }

        String sourceCode = currentSourceFor(lockedDiagram);
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidDiagramVersionException("Initial diagram version requires source code");
        }

        DiagramVersion version = buildVersion(
                lockedDiagram,
                1,
                promptFor(lockedDiagram),
                sourceCode,
                formatFor(lockedDiagram),
                DiagramChangeType.GENERATED,
                lockedDiagram.getModelUsed(),
                owner);
        DiagramVersion saved = versionRepository.save(version);
        synchronizeDiagramSnapshot(
                lockedDiagram,
                saved.getPrompt(),
                saved.getSourceCode(),
                saved.getSourceFormat(),
                saved.getChangeType(),
                saved.getModelUsed(),
                saved.getVersionNumber());
        diagramRepository.save(lockedDiagram);
        return saved;
    }

    @Override
    @Transactional
    public Optional<DiagramVersion> createVersionIfChanged(
            UUID diagramId,
            UUID ownerId,
            String prompt,
            String sourceCode,
            DiagramSourceFormat sourceFormat,
            DiagramChangeType changeType,
            String modelUsed) {
        ApplicationUser owner = requireUser(ownerId);
        Diagram lockedDiagram = lockOwnedDiagram(diagramId, owner.getId());
        String requiredSource = requireSourceCode(sourceCode);
        DiagramSourceFormat requiredFormat = requireSourceFormat(sourceFormat);
        String normalizedPrompt = trimPrompt(prompt);
        DiagramChangeType requiredChangeType = changeType == null ? DiagramChangeType.EDITED : changeType;

        List<DiagramVersion> versions = versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(lockedDiagram.getId());
        if (!versions.isEmpty() && sameMeaningfulContent(versions.get(0), normalizedPrompt, requiredSource, requiredFormat)) {
            return Optional.empty();
        }

        int nextVersionNumber = getNextVersionNumberForLockedDiagram(lockedDiagram);
        DiagramVersion version = buildVersion(
                lockedDiagram,
                nextVersionNumber,
                normalizedPrompt,
                requiredSource,
                requiredFormat,
                requiredChangeType,
                modelUsed,
                owner);
        DiagramVersion saved = versionRepository.save(version);
        synchronizeDiagramSnapshot(
                lockedDiagram,
                saved.getPrompt(),
                saved.getSourceCode(),
                saved.getSourceFormat(),
                saved.getChangeType(),
                saved.getModelUsed(),
                saved.getVersionNumber());
        diagramRepository.save(lockedDiagram);
        return Optional.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagramVersion> getVersionHistory(UUID diagramId, UUID ownerId) {
        Diagram diagram = diagramRepository.findByIdAndOwnerId(requireId(diagramId, "Diagram ID"), requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found for owner"));
        return versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagram.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DiagramVersion getVersion(UUID diagramId, UUID ownerId, int versionNumber) {
        Diagram diagram = diagramRepository.findByIdAndOwnerId(requireId(diagramId, "Diagram ID"), requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found for owner"));
        if (versionNumber <= 0) {
            throw new InvalidDiagramVersionException("Version number must be positive");
        }
        return versionRepository.findByDiagramIdAndVersionNumber(diagram.getId(), versionNumber)
                .orElseThrow(() -> new DiagramVersionNotFoundException("Diagram version not found"));
    }

    @Override
    @Transactional
    public DiagramVersion restoreVersion(UUID diagramId, UUID ownerId, int versionNumber) {
        if (versionNumber <= 0) {
            throw new InvalidDiagramVersionException("Version number must be positive");
        }
        ApplicationUser owner = requireUser(ownerId);
        Diagram lockedDiagram = lockOwnedDiagram(diagramId, owner.getId());
        DiagramVersion historical = versionRepository.findByDiagramIdAndVersionNumber(
                        lockedDiagram.getId(),
                        versionNumber)
                .orElseThrow(() -> new DiagramVersionNotFoundException("Diagram version not found"));
        List<DiagramVersion> versions = versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(lockedDiagram.getId());
        if (!versions.isEmpty() && sameMeaningfulContent(
                versions.get(0),
                historical.getPrompt(),
                historical.getSourceCode(),
                historical.getSourceFormat())) {
            return versions.get(0);
        }

        int nextVersionNumber = getNextVersionNumberForLockedDiagram(lockedDiagram);
        DiagramVersion restored = buildVersion(
                lockedDiagram,
                nextVersionNumber,
                trimPrompt(historical.getPrompt()),
                historical.getSourceCode(),
                historical.getSourceFormat(),
                DiagramChangeType.RESTORED,
                historical.getModelUsed(),
                owner);
        DiagramVersion saved = versionRepository.save(restored);
        synchronizeDiagramSnapshot(
                lockedDiagram,
                saved.getPrompt(),
                saved.getSourceCode(),
                saved.getSourceFormat(),
                saved.getChangeType(),
                saved.getModelUsed(),
                saved.getVersionNumber());
        diagramRepository.save(lockedDiagram);
        return saved;
    }

    @Override
    public int getNextVersionNumberForLockedDiagram(Diagram lockedDiagram) {
        if (lockedDiagram == null || lockedDiagram.getId() == null) {
            throw new InvalidDiagramVersionException("Locked diagram is required");
        }
        return versionRepository.findMaximumVersionNumber(lockedDiagram.getId()).orElse(0) + 1;
    }

    private ApplicationUser requireUser(UUID ownerId) {
        return userRepository.findById(requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + ownerId));
    }

    private Diagram lockOwnedDiagram(UUID diagramId, UUID ownerId) {
        Diagram lockedDiagram = diagramRepository.findByIdForUpdate(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        if (lockedDiagram.getOwner() == null || !ownerId.equals(lockedDiagram.getOwner().getId())) {
            throw new DiagramAccessDeniedException("Diagram does not belong to owner");
        }
        return lockedDiagram;
    }

    private static DiagramVersion buildVersion(
            Diagram diagram,
            int versionNumber,
            String prompt,
            String sourceCode,
            DiagramSourceFormat sourceFormat,
            DiagramChangeType changeType,
            String modelUsed,
            ApplicationUser owner) {
        DiagramVersion version = new DiagramVersion(diagram, versionNumber, sourceCode, sourceFormat);
        version.setPrompt(trimPrompt(prompt));
        version.setChangeType(changeType);
        version.setModelUsed(trimToNull(modelUsed));
        version.setCreatedBy(owner);
        return version;
    }

    private static void synchronizeDiagramSnapshot(
            Diagram diagram,
            String prompt,
            String sourceCode,
            DiagramSourceFormat sourceFormat,
            DiagramChangeType changeType,
            String modelUsed,
            int versionNumber) {
        diagram.setCurrentSourceCode(sourceCode);
        diagram.setOriginalPrompt(prompt);
        diagram.setSourceFormat(sourceFormat);
        diagram.setCurrentVersionNumber(versionNumber);
        diagram.setModelUsed(trimToNull(modelUsed));
        if (sourceFormat == DiagramSourceFormat.PLANTUML) {
            diagram.setPlantUmlCode(sourceCode);
        }
    }

    private static boolean sameMeaningfulContent(
            DiagramVersion version,
            String prompt,
            String sourceCode,
            DiagramSourceFormat sourceFormat) {
        return Objects.equals(version.getPrompt(), trimPrompt(prompt))
                && Objects.equals(version.getSourceCode(), sourceCode)
                && version.getSourceFormat() == sourceFormat;
    }

    private static String currentSourceFor(Diagram diagram) {
        if (diagram.getCurrentSourceCode() != null && !diagram.getCurrentSourceCode().isBlank()) {
            return diagram.getCurrentSourceCode();
        }
        return diagram.getPlantUmlCode();
    }

    private static String promptFor(Diagram diagram) {
        if (diagram.getOriginalPrompt() != null && !diagram.getOriginalPrompt().isBlank()) {
            return trimPrompt(diagram.getOriginalPrompt());
        }
        return trimPrompt(diagram.getInputText());
    }

    private static DiagramSourceFormat formatFor(Diagram diagram) {
        return diagram.getSourceFormat() == null ? DiagramSourceFormat.PLANTUML : diagram.getSourceFormat();
    }

    private static String requireSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidDiagramVersionException("Diagram version source code is required");
        }
        return sourceCode;
    }

    private static DiagramSourceFormat requireSourceFormat(DiagramSourceFormat sourceFormat) {
        if (sourceFormat == null) {
            throw new InvalidDiagramVersionException("Diagram version source format is required");
        }
        return sourceFormat;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new InvalidDiagramVersionException(fieldName + " is required");
        }
        return id;
    }

    private static String trimPrompt(String prompt) {
        return prompt == null ? null : trimToNull(prompt);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
