package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectInvitationStatus;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.util.UUID;

public record InvitationDecisionResponse(
        UUID projectId,
        String projectName,
        ProjectRole role,
        ProjectInvitationStatus status
) {
    public static InvitationDecisionResponse from(ProjectInvitation invitation) {
        return new InvitationDecisionResponse(
                invitation.getProject().getId(),
                invitation.getProject().getName(),
                invitation.getRole(),
                invitation.getStatus());
    }
}
