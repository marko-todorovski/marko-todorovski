package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.exception.DiagramShareException;
import com.example.aidiagramgenerator.repository.DiagramShareRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for public share resolution: expiration and revocation are rejected without
 * requiring a Spring context.
 */
class DiagramShareServiceImplTest {

    private final DomainDiagramRepository diagramRepository = mock(DomainDiagramRepository.class);
    private final DiagramVersionRepository versionRepository = mock(DiagramVersionRepository.class);
    private final DiagramShareRepository shareRepository = mock(DiagramShareRepository.class);
    private final DiagramShareTokenService tokenService = new DiagramShareTokenService();
    private final PublicShareRateLimiter rateLimiter = mock(PublicShareRateLimiter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);

    private DiagramShareServiceImpl service;
    private String rawToken;
    private String tokenHash;

    @BeforeEach
    void setUp() {
        service = new DiagramShareServiceImpl(
                diagramRepository, versionRepository, shareRepository, tokenService, rateLimiter, clock, projectAccessService);
        rawToken = tokenService.generateRawToken();
        tokenHash = tokenService.hashToken(rawToken);
        when(rateLimiter.allowView(anyString(), anyString())).thenReturn(true);
        when(rateLimiter.allowDownload(anyString(), anyString())).thenReturn(true);
    }

    private DiagramShare buildShare() {
        Diagram diagram = new Diagram("prompt", DiagramType.CLASS, "@startuml\nclass A\n@enduml");
        DiagramVersion version = new DiagramVersion(diagram, 1, "@startuml\nclass A\n@enduml", DiagramSourceFormat.PLANTUML);
        DiagramShare share = new DiagramShare(diagram, version, tokenHash);
        share.setAllowDownloads(true);
        return share;
    }

    @Test
    void expiredShareIsRejectedOnPublicAccess() {
        DiagramShare share = buildShare();
        share.setExpiresAt(Instant.now(clock).minus(Duration.ofMinutes(1)));
        when(shareRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> service.resolvePublicShare(rawToken, "127.0.0.1", false))
                .isInstanceOf(DiagramShareException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void revokedShareIsRejectedEvenWhenNotExpired() {
        DiagramShare share = buildShare();
        share.setExpiresAt(Instant.now(clock).plus(Duration.ofDays(1)));
        share.revoke(Instant.now(clock));
        when(shareRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> service.resolvePublicShare(rawToken, "127.0.0.1", false))
                .isInstanceOf(DiagramShareException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void unknownTokenIsRejected() {
        when(shareRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolvePublicShare(rawToken, "127.0.0.1", false))
                .isInstanceOf(DiagramShareException.class);
    }

    @Test
    void activeUnexpiredShareIsAccessible() {
        DiagramShare share = buildShare();
        share.setExpiresAt(Instant.now(clock).plus(Duration.ofDays(1)));
        when(shareRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(share));

        DiagramShare resolved = service.resolvePublicShare(rawToken, "127.0.0.1", false);

        assertThat(resolved.isAccessible(clock)).isTrue();
    }
}
