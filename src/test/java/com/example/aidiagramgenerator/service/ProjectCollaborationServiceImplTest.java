package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.exception.ProjectInvitationException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.ProjectInvitationRepository;
import com.example.aidiagramgenerator.repository.ProjectMemberRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the invitation acceptance flow that do not require a Spring context.
 */
class ProjectCollaborationServiceImplTest {

    private final ProjectAccessService accessService = mock(ProjectAccessService.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectMemberRepository memberRepository = mock(ProjectMemberRepository.class);
    private final ProjectInvitationRepository invitationRepository = mock(ProjectInvitationRepository.class);
    private final ApplicationUserRepository userRepository = mock(ApplicationUserRepository.class);
    private final InvitationTokenService tokenService = new InvitationTokenService();
    private final ProjectInvitationRateLimiter rateLimiter = mock(ProjectInvitationRateLimiter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private ProjectCollaborationServiceImpl service;

    private Project project;
    private ApplicationUser inviter;
    private ApplicationUser invitee;
    private String rawToken;
    private String tokenHash;
    private ProjectInvitation invitation;

    @BeforeEach
    void setUp() {
        service = new ProjectCollaborationServiceImpl(
                accessService, projectRepository, memberRepository, invitationRepository,
                userRepository, tokenService, rateLimiter, clock);

        inviter = new ApplicationUser("owner@example.com", "hash");
        invitee = new ApplicationUser("invitee@example.com", "hash");
        project = new Project(inviter, "Project");

        rawToken = tokenService.generateRawToken();
        tokenHash = tokenService.hashToken(rawToken);
        invitation = new ProjectInvitation(project, "invitee@example.com", ProjectRole.EDITOR, tokenHash, inviter,
                Instant.now(clock).plus(Duration.ofDays(7)));

        when(userRepository.findById(any())).thenReturn(Optional.of(invitee));
        when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
        when(memberRepository.existsByProjectIdAndUserId(any(), any())).thenReturn(false);
        when(memberRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptingAPendingInvitationCreatesAMemberWithTheInvitedRole() {
        UUID userId = UUID.randomUUID();

        ProjectInvitation accepted = service.acceptInvitation(rawToken, userId);

        assertThat(accepted.getStatus().name()).isEqualTo("ACCEPTED");

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(memberRepository).saveAndFlush(memberCaptor.capture());
        ProjectMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRole()).isEqualTo(ProjectRole.EDITOR);
        assertThat(savedMember.getUser()).isEqualTo(invitee);
        assertThat(savedMember.getProject()).isEqualTo(project);
    }

    @Test
    void acceptingAnAlreadyExpiredInvitationIsRejected() {
        ProjectInvitation expired = new ProjectInvitation(project, "invitee@example.com", ProjectRole.VIEWER, tokenHash,
                inviter, Instant.now(clock).minus(Duration.ofDays(1)));
        when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.acceptInvitation(rawToken, UUID.randomUUID()))
                .isInstanceOf(ProjectInvitationException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void acceptingWithAMismatchedEmailIsTreatedAsUnavailable() {
        ApplicationUser stranger = new ApplicationUser("stranger@example.com", "hash");
        when(userRepository.findById(any())).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.acceptInvitation(rawToken, UUID.randomUUID()))
                .isInstanceOf(ProjectInvitationException.class);
    }

    @Test
    void acceptingWhenAlreadyAMemberConflicts() {
        when(memberRepository.existsByProjectIdAndUserId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.acceptInvitation(rawToken, UUID.randomUUID()))
                .isInstanceOf(ProjectInvitationException.class)
                .hasMessageContaining("already a project member");
    }
}
