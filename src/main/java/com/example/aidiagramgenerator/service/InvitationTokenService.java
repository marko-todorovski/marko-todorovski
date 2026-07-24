package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

@Service
public class InvitationTokenService {

    private final SecureTokenSupport tokenSupport = new SecureTokenSupport();

    public String generateRawToken() {
        return tokenSupport.generateRawToken();
    }

    public String hashToken(String rawToken) {
        return tokenSupport.hashToken(rawToken, "Invitation token is required");
    }
}
