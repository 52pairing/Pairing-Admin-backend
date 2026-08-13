package com.pairing.admin.matching;

import com.pairing.admin.matching.infrastructure.persistence.MatchingAdminRepository;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectDiagnosticsResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매칭 진단 목록과 상세가 <b>같은 판정을 내리는지</b> 검증한다.
 *
 * <p>목록의 {@code issueCount}는 SQL 식으로, 상세의 {@code issues}는 자바로 판정한다. 같은 기준을
 * 두 언어로 두 곳에 쓴 것이라, 한쪽만 고치면 "목록은 2건인데 상세로 들어가면 3건"이 되어 관리자가
 * 어느 쪽을 믿어야 할지 알 수 없게 된다. <b>이 테스트가 그 갈라짐을 막는 유일한 장치다.</b>
 *
 * <p>판정을 바꿀 때는 {@code POSITION_ISSUE_EXPRESSION}(SQL)과 {@code buildPositionDiagnostics}(자바)를
 * 함께 고치고, 여기서 두 값이 여전히 같은지 확인할 것.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/shared-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AI 매칭 진단 목록/상세 일관성")
class MatchingAdminDiagnosticsTest {

    /** 다른 테스트와 안 겹치게 높은 고정 ID를 쓴다. H2 인메모리가 컨텍스트 사이에 남을 수 있다. */
    private static final long PAID_PROJECT = 9100L;
    private static final long PENDING_PROJECT = 9200L;
    private static final long CLEAN_PROJECT = 9300L;

    /**
     * 결함을 <b>한 포지션에 하나씩만</b> 심는다. 한 포지션에 두 결함을 겹쳐 심으면 판정식에서 검사
     * 하나를 빼도 그 포지션은 여전히 "문제"로 남아서 issueCount가 안 변한다 - 즉 목록/상세가
     * 갈라지는 것을 이 테스트가 못 잡는다(2026-08-13 변이 테스트로 실제로 확인함).
     */
    private static final long HEALTHY_POSITION = 9101L;
    private static final long NO_SNAPSHOT_POSITION = 9102L;
    private static final long NO_EMBEDDING_POSITION = 9103L;
    private static final long NO_ROUND_POSITION = 9104L;
    private static final long NO_CANDIDATE_POSITION = 9105L;
    /**
     * 라운드는 없는데 노출 후보만 남은 포지션. 정상 흐름에서는 후보가 라운드에 딸리므로 나오지 않지만,
     * 이 프로젝트는 더미 데이터를 SQL로 직접 넣어 왔기 때문에 실제로 생길 수 있는 상태다.
     *
     * <p>이 케이스가 없으면 <b>SQL 판정식의 라운드 검사를 지워도 테스트가 통과한다</b> - 라운드가
     * 없으면 후보도 없어서 후보 검사가 대신 걸리기 때문이다(변이 테스트로 확인함).
     */
    private static final long ORPHAN_CANDIDATE_POSITION = 9106L;

    @Autowired
    private MatchingAdminRepository matchingAdminRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();

        // 착수금 결제 완료. 결함을 하나씩만 가진 포지션 4개 + 정상 1개.
        insertProject(PAID_PROJECT, "결제 완료 프로젝트", "DEPOSIT_PAID");

        // 정상: 스냅샷 + 임베딩 + 라운드 + 노출 후보까지 다 있다.
        insertPosition(HEALTHY_POSITION, PAID_PROJECT);
        insertPositionSnapshot(PAID_PROJECT, HEALTHY_POSITION);
        insertPositionEmbedding(HEALTHY_POSITION);
        insertRound(HEALTHY_POSITION, PAID_PROJECT);
        insertCandidate(HEALTHY_POSITION, true);

        // 스냅샷만 없다.
        insertPosition(NO_SNAPSHOT_POSITION, PAID_PROJECT);
        insertPositionEmbedding(NO_SNAPSHOT_POSITION);
        insertRound(NO_SNAPSHOT_POSITION, PAID_PROJECT);
        insertCandidate(NO_SNAPSHOT_POSITION, true);

        // 임베딩만 없다.
        insertPosition(NO_EMBEDDING_POSITION, PAID_PROJECT);
        insertPositionSnapshot(PAID_PROJECT, NO_EMBEDDING_POSITION);
        insertRound(NO_EMBEDDING_POSITION, PAID_PROJECT);
        insertCandidate(NO_EMBEDDING_POSITION, true);

        // 라운드만 없다. 라운드가 없으면 노출 후보도 있을 수 없으므로 후보를 안 심는다 -
        // 그래도 issues는 ROUND_MISSING 하나여야 한다(중복으로 세지 않는다).
        insertPosition(NO_ROUND_POSITION, PAID_PROJECT);
        insertPositionSnapshot(PAID_PROJECT, NO_ROUND_POSITION);
        insertPositionEmbedding(NO_ROUND_POSITION);

        // 라운드가 없는데 후보만 남아 있다(SQL 라운드 검사를 고립시키는 케이스).
        insertPosition(ORPHAN_CANDIDATE_POSITION, PAID_PROJECT);
        insertPositionSnapshot(PAID_PROJECT, ORPHAN_CANDIDATE_POSITION);
        insertPositionEmbedding(ORPHAN_CANDIDATE_POSITION);
        insertCandidate(ORPHAN_CANDIDATE_POSITION, true);

        // 라운드까지는 만들어졌는데 노출된 후보가 없다.
        insertPosition(NO_CANDIDATE_POSITION, PAID_PROJECT);
        insertPositionSnapshot(PAID_PROJECT, NO_CANDIDATE_POSITION);
        insertPositionEmbedding(NO_CANDIDATE_POSITION);
        insertRound(NO_CANDIDATE_POSITION, PAID_PROJECT);
        insertCandidate(NO_CANDIDATE_POSITION, false);

        // 결제 전이라 아무것도 없는 게 정상이다. 목록에 나오면 안 된다.
        insertProject(PENDING_PROJECT, "결제 전 프로젝트", "DEPOSIT_PENDING");
        insertPosition(9201L, PENDING_PROJECT);

        // 결제 완료 + 문제 없음.
        insertProject(CLEAN_PROJECT, "정상 프로젝트", "DEPOSIT_PAID");
        insertPosition(9301L, CLEAN_PROJECT);
        insertPositionSnapshot(CLEAN_PROJECT, 9301L);
        insertPositionEmbedding(9301L);
        insertRound(9301L, CLEAN_PROJECT);
        insertCandidate(9301L, true);
    }

    @Test
    @DisplayName("목록의 issueCount와 상세의 issueCount가 같다")
    void listAndDetailAgreeOnIssueCount() {
        int listIssueCount = findSummary(PAID_PROJECT).issueCount();
        int detailIssueCount = matchingAdminRepository.findProjectDiagnostics(PAID_PROJECT).issueCount();

        assertThat(listIssueCount)
                .as("목록(SQL)과 상세(자바)의 판정이 갈리면 관리자가 어느 쪽을 믿어야 할지 알 수 없다")
                .isEqualTo(detailIssueCount)
                .isEqualTo(5);
    }

    @Test
    @DisplayName("착수금 결제 전 프로젝트는 목록에 나오지 않는다")
    void excludesProjectsBeforeDeposit() {
        List<Long> projectIds = findProjects(false).stream()
                .map(MatchingProjectSummaryResponse::projectId)
                .toList();

        assertThat(projectIds).contains(PAID_PROJECT, CLEAN_PROJECT);
        assertThat(projectIds)
                .as("결제 전에는 임베딩·스냅샷·라운드가 없는 게 정상이라, 섞으면 전부 문제처럼 보인다")
                .doesNotContain(PENDING_PROJECT);
    }

    @Test
    @DisplayName("onlyIssues=true면 문제 없는 프로젝트가 빠진다")
    void onlyIssuesFiltersHealthyProjects() {
        List<Long> projectIds = findProjects(true).stream()
                .map(MatchingProjectSummaryResponse::projectId)
                .toList();

        assertThat(projectIds).contains(PAID_PROJECT);
        assertThat(projectIds).doesNotContain(CLEAN_PROJECT);
    }

    @Test
    @DisplayName("포지션별 issues 코드가 상태와 맞는다")
    void reportsIssueCodesPerPosition() {
        MatchingProjectDiagnosticsResponse detail =
                matchingAdminRepository.findProjectDiagnostics(PAID_PROJECT);

        assertThat(issuesOf(detail, HEALTHY_POSITION)).isEmpty();
        assertThat(issuesOf(detail, NO_SNAPSHOT_POSITION))
                .containsExactly("POSITION_SNAPSHOT_MISSING");
        assertThat(issuesOf(detail, NO_EMBEDDING_POSITION))
                .containsExactly("POSITION_EMBEDDING_MISSING");
        assertThat(issuesOf(detail, NO_ROUND_POSITION))
                .as("라운드가 없으면 노출 후보 0은 당연한 결과다. 중복으로 세면 issueCount가 부풀어 "
                        + "우선순위 판단이 흐려진다")
                .containsExactly("ROUND_MISSING");
        assertThat(issuesOf(detail, NO_CANDIDATE_POSITION))
                .containsExactly("NO_EXPOSED_CANDIDATE");
    }

    @Test
    @DisplayName("프로젝트 스냅샷 없음을 상세가 알려준다")
    void reportsMissingProjectSnapshot() {
        assertThat(matchingAdminRepository.findProjectDiagnostics(PAID_PROJECT).projectSnapshotExists())
                .isFalse();

        jdbcTemplate.update("INSERT INTO matching_snapshot (project_id, snapshot_type) VALUES (?, 'PROJECT')",
                PAID_PROJECT);

        assertThat(matchingAdminRepository.findProjectDiagnostics(PAID_PROJECT).projectSnapshotExists())
                .isTrue();
    }

    private List<String> issuesOf(MatchingProjectDiagnosticsResponse detail, long positionId) {
        return detail.positions().stream()
                .filter(item -> item.position().positionId() == positionId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("포지션이 상세에 없다: " + positionId))
                .issues();
    }

    private MatchingProjectSummaryResponse findSummary(long projectId) {
        return findProjects(false).stream()
                .filter(item -> item.projectId() == projectId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("프로젝트가 목록에 없다: " + projectId));
    }

    private List<MatchingProjectSummaryResponse> findProjects(boolean onlyIssues) {
        return matchingAdminRepository.findMatchingProjects(onlyIssues, PageRequest.of(0, 100)).getContent();
    }

    private void clean() {
        List<Long> projectIds = List.of(PAID_PROJECT, PENDING_PROJECT, CLEAN_PROJECT);
        for (Long projectId : projectIds) {
            jdbcTemplate.update("DELETE FROM matching_candidate WHERE position_id IN "
                    + "(SELECT id FROM project_position WHERE project_id = ?)", projectId);
            jdbcTemplate.update("DELETE FROM position_embedding WHERE position_id IN "
                    + "(SELECT id FROM project_position WHERE project_id = ?)", projectId);
            jdbcTemplate.update("DELETE FROM matching_round WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM matching_request WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM matching_snapshot WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM project_position WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    private void insertProject(long projectId, String title, String paymentStatus) {
        jdbcTemplate.update(
                "INSERT INTO project (id, title, status, payment_status) VALUES (?, ?, 'RECRUITING', ?)",
                projectId, title, paymentStatus);
    }

    private void insertPosition(long positionId, long projectId) {
        jdbcTemplate.update("INSERT INTO project_position (id, project_id, status, job_category, job_role) "
                        + "VALUES (?, ?, 'RECRUITING', 'DEVELOPMENT', 'BACKEND')",
                positionId, projectId);
    }

    private void insertPositionSnapshot(long projectId, long positionId) {
        jdbcTemplate.update("INSERT INTO matching_snapshot (project_id, position_id, snapshot_type) "
                + "VALUES (?, ?, 'POSITION')", projectId, positionId);
    }

    private void insertPositionEmbedding(long positionId) {
        jdbcTemplate.update("INSERT INTO position_embedding (position_id, model) "
                + "VALUES (?, 'gemini-embedding-001')", positionId);
    }

    private void insertRound(long positionId, long projectId) {
        jdbcTemplate.update("INSERT INTO matching_round (project_id, position_id, round_no, round_type, status) "
                + "VALUES (?, ?, 1, 'INITIAL', 'COMPLETED')", projectId, positionId);
    }

    private void insertCandidate(long positionId, boolean exposed) {
        jdbcTemplate.update("INSERT INTO matching_candidate (position_id, is_exposed) VALUES (?, ?)",
                positionId, exposed);
    }
}
