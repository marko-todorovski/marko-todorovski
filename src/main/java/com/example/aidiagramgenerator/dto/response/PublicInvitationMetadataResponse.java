package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.time.Instant;

public record PublicInvitationMetadataResponse(
        String projectName,
        ProjectRole role,
        Instant expiresAt,
        boolean requiresAuthentication
) {
    public static PublicInvitationMetadataResponse from(ProjectInvitation invitation) {
        return new PublicInvitationMetadataResponse(
                invitation.getProject().getName(),
                invitation.getRole(),
                invitation.getExpiresAt(),
                true);
    }
}
