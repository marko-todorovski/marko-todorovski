package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.dto.request.CreateProjectInvitationRequest;

import java.util.List;
import java.util.UUID;

public interface ProjectCollaborationService {

    List<ProjectMember> listMembers(UUID projectId, UUID userId);

    CreatedInvitation createInvitation(UUID projectId, UUID ownerId, CreateProjectInvitationRequest request);

    List<ProjectInvitation> listInvitations(UUID projectId, UUID ownerId);

    ProjectInvitation revokeInvitation(UUID projectId, UUID invitationId, UUID ownerId);

    ProjectInvitation getInvitationMetadata(String rawToken);

    ProjectInvitation acceptInvitation(String rawToken, UUID userId);

    ProjectInvitation rejectInvitation(String rawToken, UUID userId);

    ProjectMember updateMemberRole(UUID projectId, UUID memberId, UUID ownerId, ProjectRole role);

    void removeMember(UUID projectId, UUID memberId, UUID ownerId);

    void leaveProject(UUID projectId, UUID userId);

    record CreatedInvitation(ProjectInvitation invitation, String rawToken) {
    }
}
