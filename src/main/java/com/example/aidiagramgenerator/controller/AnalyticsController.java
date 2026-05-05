package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.response.ApiResponse;
import com.example.aidiagramgenerator.dto.response.GlobalDiagramAnalyticsResponse;
import com.example.aidiagramgenerator.service.DiagramAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for diagram analytics.
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Diagram Analytics", description = "APIs for diagram evaluation analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private final DiagramAnalyticsService diagramAnalyticsService;

    public AnalyticsController(DiagramAnalyticsService diagramAnalyticsService) {
        this.diagramAnalyticsService = diagramAnalyticsService;
    }

    /**
     * Get global analytics across all diagram evaluations.
     */
    @GetMapping("/global")
    @Operation(summary = "Get global diagram analytics",
               description = "Returns global averages and total number of evaluated diagrams")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analytics returned successfully",
                     content = @Content(schema = @Schema(implementation = GlobalDiagramAnalyticsResponse.class)))
    })
    public ResponseEntity<ApiResponse<GlobalDiagramAnalyticsResponse>> getGlobalAnalytics() {
        logger.info("Fetching global diagram analytics");

        GlobalDiagramAnalyticsResponse response = diagramAnalyticsService.getGlobalAnalytics();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
