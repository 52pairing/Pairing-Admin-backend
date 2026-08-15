package com.pairing.admin.matching;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.matching.application.MatchingAdminService;
import com.pairing.admin.matching.presentation.api.MatchingAdminController;
import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectDiagnosticsResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectSummaryResponse;
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
                new MatchingDiagnosticsResponse.EmbeddingInfo(true, "gemini-embedding-001", 768, 1),
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
                .andExpect(jsonPath("$.data.project.projectId").value(23))
                // 상태 코드마다 화면 문구가 함께 나가야 한다. 문구가 빠지면 관리자 화면에 영문 코드가
                // 그대로 찍힌다 - 실제로 그렇게 배포됐다. 코드는 분기용으로 그대로 남긴다.
                .andExpect(jsonPath("$.data.project.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.project.statusLabel").value("모집중"))
                .andExpect(jsonPath("$.data.project.paymentStatusLabel").value("착수금 결제 완료"))
                // 프로젝트와 코드가 같아도(RECRUITING) 포지션은 자기 표를 쓴다
                .andExpect(jsonPath("$.data.position.statusLabel").value("모집중"))
                .andExpect(jsonPath("$.data.round.roundTypeLabel").value("최초 추천"))
                .andExpect(jsonPath("$.data.round.statusLabel").value("완료"))
                .andExpect(jsonPath("$.data.lastAiLog.statusLabel").value("성공"));
    }

    @Test
    @DisplayName("falls back to the raw code when a status is unknown, and leaves absent statuses absent")
    void labelsUnknownAndMissingStatuses() throws Exception {
        // 백엔드가 상태 값을 추가하면 관리자 표가 먼저 낡는다. 그때 빈칸이 뜨면 관리자는 무슨
        // 상태인지조차 알 수 없으므로, 영어 코드라도 그대로 보이게 한다.
        MatchingDiagnosticsResponse response = new MatchingDiagnosticsResponse(
                new MatchingDiagnosticsResponse.ProjectInfo(23L, "Project", "BRAND_NEW_STATUS", "DEPOSIT_PAID"),
                new MatchingDiagnosticsResponse.PositionInfo(33L, "RECRUITING", "DEVELOPMENT", "BACKEND"),
                new MatchingDiagnosticsResponse.SnapshotInfo(true, true),
                new MatchingDiagnosticsResponse.EmbeddingInfo(true, "gemini-embedding-001", 768, 1),
                // 라운드가 없으면 상태도 없는 게 정상이다. "-" 같은 값으로 채우면 프론트가
                // "없음"과 "모르는 값"을 구분할 수 없다.
                new MatchingDiagnosticsResponse.RoundInfo(null, null, null, null),
                new MatchingDiagnosticsResponse.CountInfo(0, 0, 0),
                new MatchingDiagnosticsResponse.LastAiLogInfo(null, null, null)
        );
        when(matchingAdminService.findDiagnostics(23L, 33L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/matchings/diagnostics")
                        .param("projectId", "23")
                        .param("positionId", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.statusLabel").value("BRAND_NEW_STATUS"))
                .andExpect(jsonPath("$.data.round.statusLabel").doesNotExist())
                .andExpect(jsonPath("$.data.round.roundTypeLabel").doesNotExist())
                .andExpect(jsonPath("$.data.lastAiLog.statusLabel").doesNotExist());
    }

    @Test
    @DisplayName("finds diagnostics target projects")
    void findMatchingProjects() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        MatchingProjectSummaryResponse item = new MatchingProjectSummaryResponse(
                23L, "Project", "주식회사 페어링", "RECRUITING", "DEPOSIT_PAID",
                LocalDateTime.now(), 2, 1, LocalDateTime.now());
        when(matchingAdminService.findMatchingProjects(eq(false), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(item), pageable, 1)));

        mockMvc.perform(get("/api/v1/admin/matchings/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MATCHING_PROJECTS_FOUND"))
                .andExpect(jsonPath("$.data.content[0].projectId").value(23))
                .andExpect(jsonPath("$.data.content[0].issueCount").value(1))
                .andExpect(jsonPath("$.data.content[0].statusLabel").value("모집중"))
                .andExpect(jsonPath("$.data.content[0].paymentStatusLabel").value("착수금 결제 완료"));
    }

    @Test
    @DisplayName("passes onlyIssues through")
    void findMatchingProjectsWithOnlyIssues() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(matchingAdminService.findMatchingProjects(eq(true), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(), pageable, 0)));

        mockMvc.perform(get("/api/v1/admin/matchings/projects").param("onlyIssues", "true"))
                .andExpect(status().isOk());

        verify(matchingAdminService).findMatchingProjects(eq(true), any());
    }

    @Test
    @DisplayName("finds diagnostics for every position of a project")
    void findProjectDiagnostics() throws Exception {
        MatchingProjectDiagnosticsResponse.PositionDiagnostics position =
                new MatchingProjectDiagnosticsResponse.PositionDiagnostics(
                        new MatchingDiagnosticsResponse.PositionInfo(33L, "RECRUITING", "DEVELOPMENT", "BACKEND"),
                        true, false, null, null, 46,
                        new MatchingDiagnosticsResponse.RoundInfo(null, null, null, null),
                        new MatchingDiagnosticsResponse.CountInfo(0, 0, 0),
                        new MatchingDiagnosticsResponse.LastAiLogInfo(null, null, null),
                        List.of(MatchingProjectDiagnosticsResponse.IssueType.POSITION_EMBEDDING_MISSING.toIssue(),
                                MatchingProjectDiagnosticsResponse.IssueType.ROUND_MISSING.toIssue())
                );
        MatchingProjectDiagnosticsResponse response = new MatchingProjectDiagnosticsResponse(
                new MatchingDiagnosticsResponse.ProjectInfo(23L, "Project", "RECRUITING", "DEPOSIT_PAID"),
                true, 1, 1, List.of(position));
        when(matchingAdminService.findProjectDiagnostics(23L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/matchings/projects/23/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MATCHING_PROJECT_DIAGNOSTICS_FOUND"))
                .andExpect(jsonPath("$.data.issueCount").value(1))
                .andExpect(jsonPath("$.data.positions[0].position.positionId").value(33))
                .andExpect(jsonPath("$.data.positions[0].issues[0].code").value("POSITION_EMBEDDING_MISSING"))
                .andExpect(jsonPath("$.data.positions[0].issues[0].message").isNotEmpty())
                .andExpect(jsonPath("$.data.positions[0].positionEmbeddingDimension").doesNotExist());
    }
}
