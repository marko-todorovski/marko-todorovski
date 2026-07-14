package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.dto.request.RegisterRequest;
import com.example.aidiagramgenerator.dto.response.AuthenticatedUserResponse;
import com.example.aidiagramgenerator.security.ApplicationUserPrincipal;

public interface AuthService {

    AuthenticatedUserResponse register(RegisterRequest request);

    AuthenticatedUserResponse getCurrentUser(ApplicationUserPrincipal principal);

    AuthenticatedUserResponse toResponse(ApplicationUser user);
}
