package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRepositoryRequest(
        @NotBlank
        @Size(max = 2048)
        String githubUrl
) {
}
