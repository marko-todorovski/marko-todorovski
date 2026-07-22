package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectInvitationStatus;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record ProjectInvitationSummaryResponse(
        UUID id,
        String email,
        ProjectRole role,
        ProjectInvitationStatus status,
        boolean expired,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt,
        Instant rejectedAt
) {
    public static ProjectInvitationSummaryResponse from(ProjectInvitation invitation, Clock clock) {
        return new ProjectInvitationSummaryResponse(
                invitation.getId(),
                invitation.getInvitedEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.isExpired(clock),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getRejectedAt());
    }
}
