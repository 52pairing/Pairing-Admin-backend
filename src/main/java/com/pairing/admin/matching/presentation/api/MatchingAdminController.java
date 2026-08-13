package com.pairing.admin.matching.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.matching.application.MatchingAdminService;
import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/matchings")
@RequiredArgsConstructor
@Tag(name = "70. AI Matching", description = "AI matching admin APIs")
public class MatchingAdminController {

    private final MatchingAdminService matchingAdminService;

    @PostMapping("/embeddings/reindex")
    @Operation(summary = "Start bulk embedding reindex")
    public ResponseEntity<ApiResponse<Void>> reindexEmbeddings() {
        matchingAdminService.reindexAll();
        return ResponseEntity.accepted()
                .body(ApiResponse.accepted("EMBEDDINGS_REINDEX_STARTED", "Embedding reindex started."));
    }

    @GetMapping("/embeddings/missing")
    @Operation(summary = "Find missing matching embeddings",
            description = "targetType supports ALL, FREELANCER, POSITION.")
    public ResponseEntity<ApiResponse<EmbeddingMissingResponse>> findMissingEmbeddings(
            @Parameter(example = "ALL") @RequestParam(defaultValue = "ALL") String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("MISSING_EMBEDDINGS_FOUND", "Lookup succeeded.",
                matchingAdminService.findMissingEmbeddings(targetType, pageable)));
    }

    @PostMapping("/embeddings/freelancers/{freelancerId}/reindex")
    @Operation(summary = "Start one freelancer embedding reindex")
    public ResponseEntity<ApiResponse<Void>> reindexFreelancerEmbedding(@PathVariable Long freelancerId) {
        matchingAdminService.reindexFreelancer(freelancerId);
        return ResponseEntity.accepted()
                .body(ApiResponse.accepted("FREELANCER_REINDEX_STARTED", "Freelancer reindex started."));
    }

    @PostMapping("/embeddings/positions/{positionId}/reindex")
    @Operation(summary = "Start one position embedding reindex")
    public ResponseEntity<ApiResponse<Void>> reindexPositionEmbedding(
            @PathVariable Long positionId,
            @RequestParam Long projectId
    ) {
        matchingAdminService.reindexPosition(projectId, positionId);
        return ResponseEntity.accepted()
                .body(ApiResponse.accepted("POSITION_REINDEX_STARTED", "Position reindex started."));
    }

    @GetMapping("/ai-logs")
    @Operation(summary = "Find AI logs",
            description = "Matching LLM logs use agentType=MATCHER. Embedding logs use agentType=EMBEDDING.")
    public ResponseEntity<ApiResponse<PageResponse<AiLogResponse>>> findAiLogs(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String refType,
            @RequestParam(required = false) Long refId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AiLogResponse> response =
                matchingAdminService.findAiLogs(agentType, refType, refId, status, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("AI_LOGS_FOUND", "Lookup succeeded.", response));
    }

    @GetMapping("/diagnostics")
    @Operation(summary = "Find matching diagnostics")
    public ResponseEntity<ApiResponse<MatchingDiagnosticsResponse>> findDiagnostics(
            @RequestParam Long projectId,
            @RequestParam Long positionId
    ) {
        return ResponseEntity.ok(ApiResponse.success("MATCHING_DIAGNOSTICS_FOUND", "Lookup succeeded.",
                matchingAdminService.findDiagnostics(projectId, positionId)));
    }
}
