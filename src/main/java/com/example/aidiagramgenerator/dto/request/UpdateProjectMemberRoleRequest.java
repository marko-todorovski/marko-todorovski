package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(@NotNull ProjectRole role) {
}
