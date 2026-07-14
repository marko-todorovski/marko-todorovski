package com.example.aidiagramgenerator.exception;

import com.example.aidiagramgenerator.controller.AuthController;
import com.example.aidiagramgenerator.dto.response.AuthErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<AuthErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthErrorResponse("DUPLICATE_EMAIL", "Email is already registered"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse("INVALID_CREDENTIALS", "Invalid email or password"));
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidAuthRequest(InvalidAuthRequestException ex) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse("INVALID_AUTH_REQUEST", "Invalid authentication request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse("INVALID_AUTH_REQUEST", "Invalid authentication request"));
    }
}
