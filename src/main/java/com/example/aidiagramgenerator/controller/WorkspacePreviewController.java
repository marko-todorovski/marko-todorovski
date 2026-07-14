package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.request.PreviewDiagramRequest;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace/preview")
public class WorkspacePreviewController {

    private final CurrentUser currentUser;
    private final DiagramRenderingService renderingService;

    public WorkspacePreviewController(CurrentUser currentUser, DiagramRenderingService renderingService) {
        this.currentUser = currentUser;
        this.renderingService = renderingService;
    }

    @PostMapping
    public ResponseEntity<?> preview(@Valid @RequestBody PreviewDiagramRequest request) {
        currentUser.requireCurrentUserId();
        String sourceCode = request.sourceCode();
        if (!sourceCode.trim().startsWith("@start")) {
            return error(HttpStatus.BAD_REQUEST, "PlantUML source must start with @start");
        }

        String requestedFormat = request.outputFormat() == null ? "SVG" : request.outputFormat().trim();
        if (!"SVG".equalsIgnoreCase(requestedFormat)) {
            return error(HttpStatus.BAD_REQUEST, "Only SVG preview is supported");
        }

        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(renderingService.renderToSvg(sourceCode));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "PlantUML source is required");
        } catch (DiagramRenderingException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "PlantUML preview could not be rendered");
        }
    }

    private static ResponseEntity<PreviewError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PreviewError("PREVIEW_RENDER_FAILED", message));
    }

    private record PreviewError(String code, String message) {
    }
}
