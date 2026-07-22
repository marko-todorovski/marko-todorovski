package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.dto.request.CreateDiagramShareRequest;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.DiagramShareException;
import com.example.aidiagramgenerator.exception.DiagramVersionNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidDiagramVersionException;
import com.example.aidiagramgenerator.repository.DiagramShareRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DiagramShareServiceImpl implements DiagramShareService {

    private static final Logger log = LoggerFactory.getLogger(DiagramShareServiceImpl.class);
    private static final int TOKEN_GENERATION_ATTEMPTS = 5;
    private static final Duration MAX_EXPIRATION = Duration.ofDays(365);

    private final DomainDiagramRepository diagramRepository;
    private final DiagramVersionRepository versionRepository;
    private final DiagramShareRepository shareRepository;
    private final DiagramShareTokenService tokenService;
    private final PublicShareRateLimiter rateLimiter;
    private final Clock clock;
    private final ProjectAccessService projectAccessService;

    public DiagramShareServiceImpl(
            DomainDiagramRepository diagramRepository,
            DiagramVersionRepository versionRepository,
            DiagramShareRepository shareRepository,
            DiagramShareTokenService tokenService,
            PublicShareRateLimiter rateLimiter,
            Clock clock,
            ProjectAccessService projectAccessService) {
        this.diagramRepository = diagramRepository;
        this.versionRepository = versionRepository;
        this.shareRepository = shareRepository;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.projectAccessService = projectAccessService;
    }

    @Override
    @Transactional
    public CreatedShare createShare(UUID diagramId, UUID ownerId, CreateDiagramShareRequest request) {
        Diagram diagram = requireOwnedDiagram(diagramId, ownerId);
        Instant expiresAt = validateExpiration(request.expiresAt());
        int versionNumber = request.versionNumber() == null
                ? currentVersionNumber(diagram)
                : request.versionNumber();
        if (versionNumber <= 0) {
            throw new InvalidDiagramVersionException("Version number must be positive");
        }
        DiagramVersion version = versionRepository.findByDiagramIdAndVersionNumber(diagram.getId(), versionNumber)
                .orElseThrow(() -> new DiagramVersionNotFoundException("Diagram version not found"));

        for (int attempt = 0; attempt < TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String rawToken = tokenService.generateRawToken();
            String tokenHash = tokenService.hashToken(rawToken);
            if (shareRepository.existsByTokenHash(tokenHash)) {
                continue;
            }
            DiagramShare share = new DiagramShare(diagram, version, tokenHash);
            share.setExpiresAt(expiresAt);
            share.setAllowDownloads(request.allowDownloads() == null || request.allowDownloads());
            share.setTitleOverride(request.titleOverride());
            share.setDescriptionOverride(request.descriptionOverride());
            DiagramShare saved = shareRepository.saveAndFlush(share);
            log.info("Created diagram share id={} diagramId={} version={} ownerId={} expiresAt={}",
                    saved.getId(), diagram.getId(), version.getVersionNumber(), ownerId, expiresAt);
            return new CreatedShare(saved, rawToken);
        }

        throw new DiagramShareException(HttpStatus.CONFLICT, "TOKEN_COLLISION", "Could not create a unique share token");
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagramShare> listShares(UUID diagramId, UUID ownerId) {
        Diagram diagram = requireOwnedDiagram(diagramId, ownerId);
        return shareRepository.findAllByDiagramIdOrderByCreatedAtDesc(diagram.getId());
    }

    @Override
    @Transactional
    public DiagramShare revokeShare(UUID diagramId, UUID shareId, UUID ownerId) {
        requireOwnedDiagram(diagramId, ownerId);
        DiagramShare share = shareRepository.findByIdAndDiagramId(requireId(diagramId, "Diagram ID"), requireId(shareId, "Share ID"))
                .orElseThrow(() -> new DiagramShareException(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND", "Share not found"));
        if (share.isRevoked()) {
            throw new DiagramShareException(HttpStatus.CONFLICT, "SHARE_ALREADY_REVOKED", "Share is already revoked");
        }
        share.revoke(Instant.now(clock));
        DiagramShare saved = shareRepository.saveAndFlush(share);
        log.info("Revoked diagram share id={} diagramId={} ownerId={}", saved.getId(), diagramId, ownerId);
        return saved;
    }

    @Override
    @Transactional
    public DiagramShare resolvePublicShare(String rawToken, String remoteAddress, boolean recordAccess) {
        DiagramShare share = requireAccessibleShare(rawToken);
        if (!rateLimiter.allowView(remoteAddress, share.getTokenHash())) {
            throw new DiagramShareException(HttpStatus.TOO_MANY_REQUESTS, "SHARE_RATE_LIMITED", "Too many share requests");
        }
        if (recordAccess) {
            try {
                share.recordAccess(Instant.now(clock));
                share = shareRepository.saveAndFlush(share);
            } catch (RuntimeException e) {
                log.warn("Could not record public share access for share id={}: {}", share.getId(), e.getMessage());
            }
        }
        return share;
    }

    @Override
    @Transactional(readOnly = true)
    public DiagramShare resolvePublicShareForDownload(String rawToken, String remoteAddress) {
        DiagramShare share = requireAccessibleShare(rawToken);
        if (!rateLimiter.allowDownload(remoteAddress, share.getTokenHash())) {
            throw new DiagramShareException(HttpStatus.TOO_MANY_REQUESTS, "SHARE_RATE_LIMITED", "Too many share download requests");
        }
        if (!share.isAllowDownloads()) {
            throw new DiagramShareException(HttpStatus.FORBIDDEN, "DOWNLOADS_DISABLED", "Downloads are disabled for this shared diagram");
        }
        return share;
    }

    private DiagramShare requireAccessibleShare(String rawToken) {
        String tokenHash = tokenService.hashToken(rawToken);
        DiagramShare share = shareRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::shareUnavailable);
        if (!share.isAccessible(clock)) {
            throw shareUnavailable();
        }
        return share;
    }

    private Diagram requireOwnedDiagram(UUID diagramId, UUID ownerId) {
        Diagram diagram = diagramRepository.findById(requireId(diagramId, "Diagram ID"))
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        projectAccessService.requireDiagramOwner(diagram, requireId(ownerId, "Owner ID"));
        return diagram;
    }

    private Instant validateExpiration(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        Instant now = Instant.now(clock);
        if (!expiresAt.isAfter(now)) {
            throw new DiagramShareException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRATION", "Expiration must be in the future");
        }
        if (expiresAt.isAfter(now.plus(MAX_EXPIRATION))) {
            throw new DiagramShareException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRATION", "Expiration cannot be more than one year in the future");
        }
        return expiresAt;
    }

    private int currentVersionNumber(Diagram diagram) {
        Integer currentVersionNumber = diagram.getCurrentVersionNumber();
        if (currentVersionNumber == null || currentVersionNumber <= 0) {
            return versionRepository.findMaximumVersionNumber(diagram.getId())
                    .orElseThrow(() -> new DiagramVersionNotFoundException("Diagram version not found"));
        }
        return currentVersionNumber;
    }

    private DiagramShareException shareUnavailable() {
        return new DiagramShareException(HttpStatus.NOT_FOUND, "SHARE_NOT_AVAILABLE", "This shared diagram is unavailable.");
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new DiagramShareException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", fieldName + " is required");
        }
        return id;
    }
}
