package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        UUID userId,
        String displayName,
        ProjectRole role,
        Instant joinedAt
) {
    public static ProjectMemberResponse from(ProjectMember member) {
        ApplicationUser user = member.getUser();
        return new ProjectMemberResponse(
                member.getId(),
                user.getId(),
                displayName(user),
                member.getRole(),
                member.getJoinedAt());
    }

    private static String displayName(ApplicationUser user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
