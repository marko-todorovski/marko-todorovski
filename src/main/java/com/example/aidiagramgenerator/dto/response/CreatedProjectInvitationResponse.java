package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record CreatedProjectInvitationResponse(
        UUID invitationId,
        String email,
        ProjectRole role,
        Instant expiresAt,
        String invitationToken,
        String invitationUrl
) {
    public static CreatedProjectInvitationResponse from(ProjectInvitation invitation, String rawToken) {
        return new CreatedProjectInvitationResponse(
                invitation.getId(),
                invitation.getInvitedEmail(),
                invitation.getRole(),
                invitation.getExpiresAt(),
                rawToken,
                "/#/invitations/" + rawToken);
    }
}
