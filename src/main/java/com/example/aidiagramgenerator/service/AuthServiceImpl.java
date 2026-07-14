package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.dto.request.RegisterRequest;
import com.example.aidiagramgenerator.dto.response.AuthenticatedUserResponse;
import com.example.aidiagramgenerator.exception.DuplicateEmailException;
import com.example.aidiagramgenerator.exception.InvalidAuthRequestException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.security.ApplicationUserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ApplicationUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(ApplicationUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AuthenticatedUserResponse register(RegisterRequest request) {
        validateRegistration(request);
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Email is already registered");
        }

        ApplicationUser user = new ApplicationUser(email, passwordEncoder.encode(request.getPassword()));
        user.setFirstName(trimRequired(request.getFirstName()));
        user.setLastName(trimRequired(request.getLastName()));

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(ApplicationUserPrincipal principal) {
        ApplicationUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UserNotFoundException("Current user not found"));
        return toResponse(user);
    }

    @Override
    public AuthenticatedUserResponse toResponse(ApplicationUser user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt());
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimRequired(String value) {
        return value == null ? null : value.trim();
    }

    private static void validateRegistration(RegisterRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("Invalid authentication request");
        }
        String email = normalizeEmail(request.getEmail());
        String firstName = trimRequired(request.getFirstName());
        String lastName = trimRequired(request.getLastName());
        String password = request.getPassword();
        if (email == null || email.isBlank() || email.length() > MAX_EMAIL_LENGTH
                || !BASIC_EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidAuthRequestException("Invalid authentication request");
        }
        if (password == null || password.isBlank()
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidAuthRequestException("Invalid authentication request");
        }
        if (firstName == null || firstName.isBlank() || firstName.length() > MAX_NAME_LENGTH
                || lastName == null || lastName.isBlank() || lastName.length() > MAX_NAME_LENGTH) {
            throw new InvalidAuthRequestException("Invalid authentication request");
        }
    }
}
