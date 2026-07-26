package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.request.LoginRequest;
import com.example.aidiagramgenerator.dto.request.RegisterRequest;
import com.example.aidiagramgenerator.dto.response.AuthenticatedUserResponse;
import com.example.aidiagramgenerator.exception.InvalidAuthRequestException;
import com.example.aidiagramgenerator.security.ApplicationUserPrincipal;
import com.example.aidiagramgenerator.service.AuthService;
import com.example.aidiagramgenerator.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticatedUserResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.register(request);
        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.getCurrentUser((ApplicationUserPrincipal) authentication.getPrincipal()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticatedUserResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        validateLoginRequest(request);
        String remoteAddress = httpRequest.getRemoteAddr();
        String email = request.getEmail();
        loginRateLimiter.checkAllowed(remoteAddress, email);
        try {
            Authentication authentication = authenticate(email, request.getPassword(), httpRequest, httpResponse);
            loginRateLimiter.recordSuccess(remoteAddress, email);
            return ResponseEntity.ok(authService.getCurrentUser((ApplicationUserPrincipal) authentication.getPrincipal()));
        } catch (BadCredentialsException ex) {
            loginRateLimiter.recordFailure(remoteAddress, email);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal ApplicationUserPrincipal principal) {
        return authService.getCurrentUser(principal);
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken());
    }

    private Authentication authenticate(
            String email,
            String password,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            return authentication;
        } catch (BadCredentialsException ex) {
            SecurityContextHolder.clearContext();
            throw ex;
        }
    }

    private static void validateLoginRequest(LoginRequest request) {
        if (request == null
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {
            throw new InvalidAuthRequestException("Invalid authentication request");
        }
    }
}
