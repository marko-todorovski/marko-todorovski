package com.example.aidiagramgenerator.exception;

import com.example.aidiagramgenerator.controller.PublicDiagramShareController;
import com.example.aidiagramgenerator.dto.response.WorkspaceErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = PublicDiagramShareController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicShareExceptionHandler {

    @ExceptionHandler(DiagramShareException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleDiagramShare(DiagramShareException ex) {
        if ("SHARE_NOT_AVAILABLE".equals(ex.getCode())) {
            return unavailable();
        }
        return ResponseEntity.status(ex.getStatus()).body(WorkspaceErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return unavailable();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WorkspaceErrorResponse> handleUnexpected(Exception ex) {
        return unavailable();
    }

    private static ResponseEntity<WorkspaceErrorResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(WorkspaceErrorResponse.of("SHARE_NOT_AVAILABLE", "This shared diagram is unavailable."));
    }
}
