package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.dto.request.CreateProjectInvitationRequest;
import com.example.aidiagramgenerator.dto.request.UpdateProjectMemberRoleRequest;
import com.example.aidiagramgenerator.dto.response.CreatedProjectInvitationResponse;
import com.example.aidiagramgenerator.dto.response.ProjectInvitationSummaryResponse;
import com.example.aidiagramgenerator.dto.response.ProjectMemberResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.ProjectCollaborationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectCollaborationController {

    private final CurrentUser currentUser;
    private final ProjectCollaborationService collaborationService;
    private final Clock clock;

    public ProjectCollaborationController(
            CurrentUser currentUser,
            ProjectCollaborationService collaborationService,
            Clock clock) {
        this.currentUser = currentUser;
        this.collaborationService = collaborationService;
        this.clock = clock;
    }

    @GetMapping("/members")
    public List<ProjectMemberResponse> listMembers(@PathVariable UUID projectId) {
        UUID userId = currentUser.requireCurrentUserId();
        return collaborationService.listMembers(projectId, userId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @PutMapping("/members/{memberId}/role")
    public ProjectMemberResponse updateRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateProjectMemberRoleRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        ProjectMember member = collaborationService.updateMemberRole(projectId, memberId, ownerId, request.role());
        return ProjectMemberResponse.from(member);
    }

    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        collaborationService.removeMember(projectId, memberId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveProject(@PathVariable UUID projectId) {
        UUID userId = currentUser.requireCurrentUserId();
        collaborationService.leaveProject(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations")
    public ResponseEntity<CreatedProjectInvitationResponse> createInvitation(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectInvitationRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        ProjectCollaborationService.CreatedInvitation created =
                collaborationService.createInvitation(projectId, ownerId, request);
        ProjectInvitation invitation = created.invitation();
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/invitations/" + invitation.getId()))
                .body(CreatedProjectInvitationResponse.from(invitation, created.rawToken()));
    }

    @GetMapping("/invitations")
    public List<ProjectInvitationSummaryResponse> listInvitations(@PathVariable UUID projectId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return collaborationService.listInvitations(projectId, ownerId).stream()
                .map(invitation -> ProjectInvitationSummaryResponse.from(invitation, clock))
                .toList();
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public ProjectInvitationSummaryResponse revokeInvitation(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return ProjectInvitationSummaryResponse.from(
                collaborationService.revokeInvitation(projectId, invitationId, ownerId),
                clock);
    }
}
