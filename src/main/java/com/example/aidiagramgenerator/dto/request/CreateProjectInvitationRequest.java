package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectInvitationRequest(
        @NotBlank @Email String email,
        @NotNull ProjectRole role,
        @Min(1) @Max(30) Integer expiresInDays
) {
}
