package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectInvitationStatus;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.dto.request.CreateProjectInvitationRequest;
import com.example.aidiagramgenerator.exception.ProjectInvitationException;
import com.example.aidiagramgenerator.exception.ProjectNotFoundException;
import com.example.aidiagramgenerator.exception.ProjectPermissionException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.ProjectInvitationRepository;
import com.example.aidiagramgenerator.repository.ProjectMemberRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProjectCollaborationServiceImpl implements ProjectCollaborationService {

    private static final int DEFAULT_EXPIRY_DAYS = 7;
    private static final int MIN_EXPIRY_DAYS = 1;
    private static final int MAX_EXPIRY_DAYS = 30;
    private static final int TOKEN_ATTEMPTS = 5;

    private final ProjectAccessService accessService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final ApplicationUserRepository userRepository;
    private final InvitationTokenService tokenService;
    private final ProjectInvitationRateLimiter rateLimiter;
    private final Clock clock;

    public ProjectCollaborationServiceImpl(
            ProjectAccessService accessService,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            ProjectInvitationRepository invitationRepository,
            ApplicationUserRepository userRepository,
            InvitationTokenService tokenService,
            ProjectInvitationRateLimiter rateLimiter,
            Clock clock) {
        this.accessService = accessService;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMember> listMembers(UUID projectId, UUID userId) {
        accessService.requireProjectViewer(projectId, userId);
        return memberRepository.findAllByProjectIdOrderByJoinedAtAsc(projectId);
    }

    @Override
    @Transactional
    public CreatedInvitation createInvitation(UUID projectId, UUID ownerId, CreateProjectInvitationRequest request) {
        accessService.requireProjectOwner(projectId, ownerId);
        rateLimiter.check(projectId, ownerId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));
        ApplicationUser inviter = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        ProjectRole role = requireInvitableRole(request.role());
        String email = normalizeEmail(request.email());
        int expiresInDays = request.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : request.expiresInDays();
        if (expiresInDays < MIN_EXPIRY_DAYS || expiresInDays > MAX_EXPIRY_DAYS) {
            throw badRequest("INVALID_INVITATION_EXPIRY", "Invitation expiry must be between 1 and 30 days");
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (memberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
                throw conflict("USER_ALREADY_MEMBER", "User is already a project member");
            }
        });
        if (invitationRepository.existsByProjectIdAndInvitedEmailAndStatus(projectId, email, ProjectInvitationStatus.PENDING)) {
            throw conflict("DUPLICATE_PENDING_INVITATION", "A pending invitation already exists for this email");
        }

        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(expiresInDays));
        for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
            String rawToken = tokenService.generateRawToken();
            String tokenHash = tokenService.hashToken(rawToken);
            if (invitationRepository.existsByTokenHash(tokenHash)) {
                continue;
            }
            ProjectInvitation invitation = new ProjectInvitation(project, email, role, tokenHash, inviter, expiresAt);
            return new CreatedInvitation(invitationRepository.saveAndFlush(invitation), rawToken);
        }
        throw conflict("INVITATION_TOKEN_COLLISION", "Could not create a unique invitation token");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectInvitation> listInvitations(UUID projectId, UUID ownerId) {
        accessService.requireProjectOwner(projectId, ownerId);
        return invitationRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Override
    @Transactional
    public ProjectInvitation revokeInvitation(UUID projectId, UUID invitationId, UUID ownerId) {
        accessService.requireProjectOwner(projectId, ownerId);
        ProjectInvitation invitation = invitationRepository.findByIdAndProjectId(invitationId, projectId)
                .orElseThrow(this::unavailable);
        if (!invitation.isPending()) {
            throw conflict("INVITATION_NOT_PENDING", "Invitation is not pending");
        }
        invitation.revoke(Instant.now(clock));
        return invitationRepository.saveAndFlush(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectInvitation getInvitationMetadata(String rawToken) {
        ProjectInvitation invitation = requireAvailableInvitation(rawToken);
        if (!invitation.isAcceptable(clock)) {
            throw unavailable();
        }
        return invitation;
    }

    @Override
    @Transactional
    public ProjectInvitation acceptInvitation(String rawToken, UUID userId) {
        ApplicationUser user = requireUser(userId);
        ProjectInvitation invitation = requireAvailableInvitation(rawToken);
        requireEmailMatch(invitation, user);
        if (!invitation.isAcceptable(clock)) {
            throw unavailable();
        }
        if (memberRepository.existsByProjectIdAndUserId(invitation.getProject().getId(), user.getId())) {
            throw conflict("USER_ALREADY_MEMBER", "User is already a project member");
        }
        ProjectMember member = new ProjectMember(invitation.getProject(), user, invitation.getRole());
        member.setInvitedBy(invitation.getInvitedBy());
        memberRepository.saveAndFlush(member);
        invitation.accept(user, Instant.now(clock));
        return invitationRepository.saveAndFlush(invitation);
    }

    @Override
    @Transactional
    public ProjectInvitation rejectInvitation(String rawToken, UUID userId) {
        ApplicationUser user = requireUser(userId);
        ProjectInvitation invitation = requireAvailableInvitation(rawToken);
        requireEmailMatch(invitation, user);
        if (!invitation.isAcceptable(clock)) {
            throw unavailable();
        }
        invitation.reject(Instant.now(clock));
        return invitationRepository.saveAndFlush(invitation);
    }

    @Override
    @Transactional
    public ProjectMember updateMemberRole(UUID projectId, UUID memberId, UUID ownerId, ProjectRole role) {
        accessService.requireProjectOwner(projectId, ownerId);
        if (role == ProjectRole.OWNER) {
            throw badRequest("INVALID_MEMBER_ROLE", "OWNER cannot be assigned through this endpoint");
        }
        ProjectMember member = memberRepository.findByIdWithUserAndProject(memberId)
                .filter(candidate -> candidate.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ProjectNotFoundException("Project member not found"));
        if (member.getRole() == ProjectRole.OWNER) {
            throw denied("Project owner cannot be changed through this endpoint");
        }
        member.changeRole(role);
        return memberRepository.saveAndFlush(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID projectId, UUID memberId, UUID ownerId) {
        accessService.requireProjectOwner(projectId, ownerId);
        ProjectMember member = memberRepository.findByIdWithUserAndProject(memberId)
                .filter(candidate -> candidate.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ProjectNotFoundException("Project member not found"));
        if (member.getRole() == ProjectRole.OWNER) {
            throw denied("Project owner cannot be removed.");
        }
        memberRepository.delete(member);
    }

    @Override
    @Transactional
    public void leaveProject(UUID projectId, UUID userId) {
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));
        if (member.getRole() == ProjectRole.OWNER) {
            throw denied("Project owner cannot leave the project.");
        }
        memberRepository.delete(member);
    }

    private ProjectInvitation requireAvailableInvitation(String rawToken) {
        String tokenHash = tokenService.hashToken(rawToken);
        return invitationRepository.findByTokenHash(tokenHash).orElseThrow(this::unavailable);
    }

    private ApplicationUser requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void requireEmailMatch(ProjectInvitation invitation, ApplicationUser user) {
        if (!invitation.getInvitedEmail().equals(normalizeEmail(user.getEmail()))) {
            throw unavailable();
        }
    }

    private static ProjectRole requireInvitableRole(ProjectRole role) {
        if (role != ProjectRole.EDITOR && role != ProjectRole.VIEWER) {
            throw badRequest("INVALID_INVITATION_ROLE", "Only EDITOR or VIEWER can be invited");
        }
        return role;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private ProjectInvitationException unavailable() {
        return new ProjectInvitationException(HttpStatus.NOT_FOUND, "INVITATION_NOT_AVAILABLE", "Invitation is unavailable.");
    }

    private static ProjectInvitationException badRequest(String code, String message) {
        return new ProjectInvitationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ProjectInvitationException conflict(String code, String message) {
        return new ProjectInvitationException(HttpStatus.CONFLICT, code, message);
    }

    private static ProjectPermissionException denied(String message) {
        return new ProjectPermissionException(message);
    }
}
