package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.dto.request.CreateDiagramShareRequest;

import java.util.List;
import java.util.UUID;

public interface DiagramShareService {

    CreatedShare createShare(UUID diagramId, UUID ownerId, CreateDiagramShareRequest request);

    List<DiagramShare> listShares(UUID diagramId, UUID ownerId);

    DiagramShare revokeShare(UUID diagramId, UUID shareId, UUID ownerId);

    DiagramShare resolvePublicShare(String rawToken, String remoteAddress, boolean recordAccess);

    DiagramShare resolvePublicShareForDownload(String rawToken, String remoteAddress);

    record CreatedShare(DiagramShare share, String rawToken) {
    }
}
