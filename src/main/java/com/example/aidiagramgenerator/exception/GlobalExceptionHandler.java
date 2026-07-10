package com.example.aidiagramgenerator.exception;

import com.example.aidiagramgenerator.dto.response.ApiResponse;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 404 – Diagram not found ─────────────────────────────────────────

    @ExceptionHandler(DiagramNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDiagramNotFound(DiagramNotFoundException ex) {
        logger.warn("Diagram not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ── 400 – Invalid diagram request ────────────────────────────────────

    @ExceptionHandler(InvalidDiagramRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDiagramRequest(InvalidDiagramRequestException ex) {
        logger.error("Invalid diagram request: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── 400 – Illegal argument ───────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Illegal argument: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    // ── 400 – Unreadable / malformed request body ────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.warn("Malformed request body: {}", ex.getMessage());
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body: " + message);
    }
    // ── 400 – Bean-validation (@Valid) errors ────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        logger.error("Validation failed", ex);

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // ── 500 – Diagram generation failure ─────────────────────────────────

    @ExceptionHandler(DiagramGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDiagramGeneration(DiagramGenerationException ex) {
        logger.error("Diagram generation failed: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    // ── 500 – Diagram rendering failure ──────────────────────────────────

    @ExceptionHandler(DiagramRenderingException.class)
    public ResponseEntity<ApiResponse<Void>> handleDiagramRendering(DiagramRenderingException ex) {
        logger.error("Diagram rendering failed: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    // ── 405 – Method not allowed ──────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Method not allowed: {} {}", ex.getMethod(), ex.getMessage());
        String message = "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.";
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            message += " Supported methods: " + ex.getSupportedHttpMethods();
        }
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, message);
    }

    // ── 500 – Catch-all ──────────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found: " + ex.getResourcePath());
    }

    // ── 500 – Catch-all ──────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ── Response builder ─────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String errorMessage) {
        return ResponseEntity.status(status).body(ApiResponse.error(errorMessage));
    }
}
