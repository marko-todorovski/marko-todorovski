package com.example.aidiagramgenerator.exception;

import com.example.aidiagramgenerator.controller.DiagramVersionController;
import com.example.aidiagramgenerator.controller.DiagramAiAssistantController;
import com.example.aidiagramgenerator.controller.DiagramShareController;
import com.example.aidiagramgenerator.controller.ProjectCollaborationController;
import com.example.aidiagramgenerator.controller.ProjectController;
import com.example.aidiagramgenerator.controller.ProjectInvitationController;
import com.example.aidiagramgenerator.controller.SavedDiagramController;
import com.example.aidiagramgenerator.dto.response.WorkspaceErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
        ProjectController.class,
        SavedDiagramController.class,
        DiagramVersionController.class,
        DiagramAiAssistantController.class,
        DiagramShareController.class,
        ProjectCollaborationController.class,
        ProjectInvitationController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_USER_NOT_FOUND", "Authenticated user no longer exists");
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleProjectNotFound(ProjectNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
    }

    @ExceptionHandler(ProjectNotEmptyException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleProjectNotEmpty(ProjectNotEmptyException ex) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_EMPTY", "Project contains diagrams and cannot be deleted");
    }

    @ExceptionHandler({DiagramNotFoundException.class, DiagramAccessDeniedException.class})
    public ResponseEntity<WorkspaceErrorResponse> handleDiagramNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, "DIAGRAM_NOT_FOUND", "Diagram not found");
    }

    @ExceptionHandler(DiagramVersionNotFoundException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleVersionNotFound(DiagramVersionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "DIAGRAM_VERSION_NOT_FOUND", "Diagram version not found");
    }

    @ExceptionHandler(InvalidProjectException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleInvalidProject(InvalidProjectException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PROJECT", ex.getMessage());
    }

    @ExceptionHandler(InvalidDiagramException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleInvalidDiagram(InvalidDiagramException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DIAGRAM", ex.getMessage());
    }

    @ExceptionHandler(InvalidDiagramVersionException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleInvalidVersion(InvalidDiagramVersionException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DIAGRAM_VERSION", ex.getMessage());
    }

    @ExceptionHandler(DiagramAiException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleDiagramAi(DiagramAiException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(DiagramShareException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleDiagramShare(DiagramShareException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ProjectInvitationException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleProjectInvitation(ProjectInvitationException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ProjectPermissionException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleProjectPermission(ProjectPermissionException ex) {
        return error(HttpStatus.FORBIDDEN, "PROJECT_PERMISSION_DENIED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PATH_VALUE", "Invalid path value");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication required");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "Request conflicts with existing data");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<WorkspaceErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported");
    }

    private static ResponseEntity<WorkspaceErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(WorkspaceErrorResponse.of(code, message));
    }
}
