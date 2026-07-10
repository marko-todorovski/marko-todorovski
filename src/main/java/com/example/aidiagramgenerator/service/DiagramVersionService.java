package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramVersionService {

    DiagramVersion createInitialVersion(UUID diagramId, UUID ownerId);

    Optional<DiagramVersion> createVersionIfChanged(
            UUID diagramId,
            UUID ownerId,
            String prompt,
            String sourceCode,
            DiagramSourceFormat sourceFormat,
            DiagramChangeType changeType,
            String modelUsed);

    List<DiagramVersion> getVersionHistory(UUID diagramId, UUID ownerId);

    DiagramVersion getVersion(UUID diagramId, UUID ownerId, int versionNumber);

    DiagramVersion restoreVersion(UUID diagramId, UUID ownerId, int versionNumber);

    int getNextVersionNumberForLockedDiagram(Diagram lockedDiagram);
}
