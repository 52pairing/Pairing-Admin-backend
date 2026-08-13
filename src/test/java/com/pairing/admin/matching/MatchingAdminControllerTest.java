package com.pairing.admin.matching;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.matching.application.MatchingAdminService;
import com.pairing.admin.matching.presentation.api.MatchingAdminController;
import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI matching admin controller")
class MatchingAdminControllerTest {

    private final MatchingAdminService matchingAdminService = mock(MatchingAdminService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MatchingAdminController(matchingAdminService))
            .build();

    @Test
    @DisplayName("starts bulk reindex")
    void reindexEmbeddings() throws Exception {
        mockMvc.perform(post("/api/v1/admin/matchings/embeddings/reindex"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("EMBEDDINGS_REINDEX_STARTED"));

        verify(matchingAdminService).reindexAll();
    }

    @Test
    @DisplayName("finds missing embeddings")
    void findMissingEmbeddings() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        EmbeddingMissingResponse.Item item = new EmbeddingMissingResponse.Item(
                "FREELANCER", 9L, "Kim", "ACTIVE", "active freelancer has no embedding",
                null, null, null);
        EmbeddingMissingResponse response = new EmbeddingMissingResponse(
                new EmbeddingMissingResponse.Summary(1, 0),
                PageResponse.from(new PageImpl<>(List.of(item), pageable, 1))
        );

        when(matchingAdminService.findMissingEmbeddings(eq("ALL"), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/matchings/embeddings/missing")
                        .param("targetType", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSING_EMBEDDINGS_FOUND"))
                .andExpect(jsonPath("$.data.summary.freelancerMissingCount").value(1))
                .andExpect(jsonPath("$.data.items.content[0].targetId").value(9));
    }

    @Test
    @DisplayName("starts one position reindex")
    void reindexPositionEmbedding() throws Exception {
        mockMvc.perform(post("/api/v1/admin/matchings/embeddings/positions/33/reindex")
                        .param("projectId", "23"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("POSITION_REINDEX_STARTED"));

        verify(matchingAdminService).reindexPosition(23L, 33L);
    }

    @Test
    @DisplayName("finds AI logs")
    void findAiLogs() throws Exception {
        AiLogResponse log = new AiLogResponse(
                1L, "EMBEDDING", "POSITION", 33L, "SUCCESS", null, LocalDateTime.now());
        when(matchingAdminService.findAiLogs(eq("EMBEDDING"), eq("POSITION"), eq(33L),
                eq("SUCCESS"), any(), any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/v1/admin/matchings/ai-logs")
                        .param("agentType", "EMBEDDING")
                        .param("refType", "POSITION")
                        .param("refId", "33")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_LOGS_FOUND"))
                .andExpect(jsonPath("$.data.content[0].agentType").value("EMBEDDING"));
    }

    @Test
    @DisplayName("finds diagnostics")
    void findDiagnostics() throws Exception {
        MatchingDiagnosticsResponse response = new MatchingDiagnosticsResponse(
                new MatchingDiagnosticsResponse.ProjectInfo(23L, "Project", "RECRUITING", "DEPOSIT_PAID"),
                new MatchingDiagnosticsResponse.PositionInfo(33L, "RECRUITING", "DEVELOPMENT", "BACKEND"),
                new MatchingDiagnosticsResponse.SnapshotInfo(true, true),
                new MatchingDiagnosticsResponse.EmbeddingInfo(true, "gemini-embedding-001", 1),
                new MatchingDiagnosticsResponse.RoundInfo(14L, 1, "INITIAL", "COMPLETED"),
                new MatchingDiagnosticsResponse.CountInfo(1, 1, 1),
                new MatchingDiagnosticsResponse.LastAiLogInfo("SUCCESS", LocalDateTime.now(), null)
        );
        when(matchingAdminService.findDiagnostics(23L, 33L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/matchings/diagnostics")
                        .param("projectId", "23")
                        .param("positionId", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MATCHING_DIAGNOSTICS_FOUND"))
                .andExpect(jsonPath("$.data.project.projectId").value(23));
    }
}
