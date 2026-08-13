package com.pairing.admin.matching.infrastructure.persistence;

import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectDiagnosticsResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingProjectSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingAdminRepository {

    /**
     * 착수금 결제가 끝난 프로젝트의 payment_status 값. 이 상태부터 모집이 시작되고 임베딩·스냅샷·
     * 추천 라운드가 만들어진다. {@code DEPOSIT_PENDING}/{@code PAYMENT_FAILED}는 아직 아무것도
     * 없는 게 정상이라 진단 목록에서 뺀다.
     */
    private static final String DEPOSIT_PAID_STATUSES =
            "'DEPOSIT_PAID', 'SUCCESS_FEE_PENDING', 'SUCCESS_FEE_PAID'";

    /**
     * 포지션 1건이 문제인지 판정하는 SQL 식(1이면 문제). 목록의 issueCount 계산에 쓴다.
     *
     * <p>판정 항목은 {@code buildPositionDiagnostics}의 issues와 같아야 한다 - 목록에서 "문제 2건"인데
     * 상세로 들어가면 3건이면 관리자가 어느 쪽을 믿어야 할지 알 수 없다. <b>한쪽을 고치면 반드시
     * 다른 쪽도 고칠 것.</b>
     */
    private static final String POSITION_ISSUE_EXPRESSION = """
            CASE WHEN pp.id IS NULL THEN 0
                 WHEN NOT EXISTS (SELECT 1 FROM matching_snapshot ms
                                   WHERE ms.position_id = pp.id AND ms.snapshot_type = 'POSITION')
                   OR NOT EXISTS (SELECT 1 FROM position_embedding pe WHERE pe.position_id = pp.id)
                   OR NOT EXISTS (SELECT 1 FROM matching_round mr WHERE mr.position_id = pp.id)
                   OR NOT EXISTS (SELECT 1 FROM matching_candidate mc
                                   WHERE mc.position_id = pp.id AND mc.is_exposed = TRUE)
                 THEN 1 ELSE 0 END
            """;

    private static final String MISSING_FREELANCER_SQL = """
            SELECT 'FREELANCER' AS target_type,
                   fp.id AS target_id,
                   COALESCE(a.name, CONCAT('Freelancer #', fp.id)) AS display_name,
                   a.status AS status,
                   'active freelancer has no embedding' AS reason,
                   fe.model AS model,
                   (SELECT l.status
                      FROM ai_agent_log l
                     WHERE l.agent_type = 'EMBEDDING'
                       AND l.ref_type = 'FREELANCER'
                       AND l.ref_id = fp.id
                     ORDER BY l.created_at DESC, l.id DESC
                     LIMIT 1) AS last_log_status,
                   (SELECT l.created_at
                      FROM ai_agent_log l
                     WHERE l.agent_type = 'EMBEDDING'
                       AND l.ref_type = 'FREELANCER'
                       AND l.ref_id = fp.id
                     ORDER BY l.created_at DESC, l.id DESC
                     LIMIT 1) AS last_log_at
              FROM freelancer_profile fp
              JOIN account a ON a.id = fp.account_id
              JOIN resume r ON r.account_id = fp.account_id AND r.status = 'COMPLETED'
              LEFT JOIN freelancer_embedding fe ON fe.freelancer_id = fp.id
             WHERE a.status = 'ACTIVE'
               AND fp.ai_matching_agreed = TRUE
               AND fp.matching_paused = FALSE
               AND fe.freelancer_id IS NULL
             GROUP BY fp.id, a.name, a.status, fe.model
            """;

    private static final String MISSING_POSITION_SQL = """
            SELECT 'POSITION' AS target_type,
                   pp.id AS target_id,
                   CONCAT(p.title, ' / ', pp.job_role) AS display_name,
                   pp.status AS status,
                   'recruiting position has no embedding' AS reason,
                   pe.model AS model,
                   (SELECT l.status
                      FROM ai_agent_log l
                     WHERE l.agent_type = 'EMBEDDING'
                       AND l.ref_type = 'POSITION'
                       AND l.ref_id = pp.id
                     ORDER BY l.created_at DESC, l.id DESC
                     LIMIT 1) AS last_log_status,
                   (SELECT l.created_at
                      FROM ai_agent_log l
                     WHERE l.agent_type = 'EMBEDDING'
                       AND l.ref_type = 'POSITION'
                       AND l.ref_id = pp.id
                     ORDER BY l.created_at DESC, l.id DESC
                     LIMIT 1) AS last_log_at
              FROM project_position pp
              JOIN project p ON p.id = pp.project_id
              LEFT JOIN position_embedding pe ON pe.position_id = pp.id
             WHERE p.status = 'RECRUITING'
               AND pp.status = 'RECRUITING'
               AND pe.position_id IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public long countMissingFreelancers() {
        return count("SELECT COUNT(*) FROM (" + MISSING_FREELANCER_SQL + ") missing");
    }

    public long countMissingPositions() {
        return count("SELECT COUNT(*) FROM (" + MISSING_POSITION_SQL + ") missing");
    }

    public PageImpl<EmbeddingMissingResponse.Item> findMissingEmbeddings(String targetType, Pageable pageable) {
        String normalized = normalizeTargetType(targetType);
        String baseSql = switch (normalized) {
            case "FREELANCER" -> MISSING_FREELANCER_SQL;
            case "POSITION" -> MISSING_POSITION_SQL;
            default -> MISSING_FREELANCER_SQL + "\nUNION ALL\n" + MISSING_POSITION_SQL;
        };

        long total = count("SELECT COUNT(*) FROM (" + baseSql + ") missing");
        String pageSql = "SELECT * FROM (" + baseSql + ") missing "
                + "ORDER BY target_type, target_id LIMIT ? OFFSET ?";
        List<EmbeddingMissingResponse.Item> content = jdbcTemplate.query(
                pageSql,
                missingItemMapper(),
                pageable.getPageSize(),
                pageable.getOffset()
        );
        return new PageImpl<>(content, pageable, total);
    }

    public List<Long> findReindexableFreelancerIds() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT fp.id
                  FROM freelancer_profile fp
                  JOIN account a ON a.id = fp.account_id
                  JOIN resume r ON r.account_id = fp.account_id AND r.status = 'COMPLETED'
                 WHERE a.status = 'ACTIVE'
                   AND fp.ai_matching_agreed = TRUE
                   AND fp.matching_paused = FALSE
                 ORDER BY fp.id
                """, Long.class);
    }

    public List<PositionRef> findReindexablePositions() {
        return jdbcTemplate.query("""
                SELECT DISTINCT pp.project_id, pp.id AS position_id
                  FROM project_position pp
                  JOIN project p ON p.id = pp.project_id
                 WHERE p.status = 'RECRUITING'
                   AND pp.status = 'RECRUITING'
                 ORDER BY pp.id
                """, (rs, rowNum) -> new PositionRef(rs.getLong("project_id"), rs.getLong("position_id")));
    }

    /**
     * 프리랜서 임베딩 원문 조각. <b>본서버({@code FreelancerEmbeddingTextBuilder})와 같은 텍스트가
     * 나와야 한다.</b>
     *
     * <p>담는 것은 <b>자기소개 + 학과 전부 + 경력 담당업무</b> 셋뿐이다. 조건(직군·직무·근무방식·단가·
     * 연차·스킬)은 <b>일부러 넣지 않는다</b> - 2026-08-11 재설계로 임베딩에서 전부 빠졌다. 임베딩은
     * 숫자의 크기를 비교하지 못하고 연차는 방향이 반대로 작동해서(포지션이 "3년 이상"이면 숫자가
     * 같은 "3년"이 "10년"보다 가깝게 나온다) DB 조건점수가 처리한다.
     *
     * <p>여기와 본서버가 다른 텍스트를 만들면 <b>어느 경로로 재색인했느냐에 따라 같은 사람의 벡터가
     * 달라진다.</b> source_hash도 갈려서 서로 스킵하지 않고 계속 덮어쓰고, 포지션 벡터와 짝이 맞지
     * 않아 유사도 자체가 무의미해진다. 한쪽을 고치면 반드시 다른 쪽도 고칠 것.
     *
     * <p>{@code resume_education}/{@code resume_career}는 element collection 테이블이라 {@code id}
     * 컬럼이 없다. 정렬은 {@code sort_order}로만 한다(2026-08-13 실제 스키마 확인).
     */
    public List<String> findFreelancerEmbeddingSource(Long freelancerId) {
        List<String> parts = new ArrayList<>();
        findLatestCompletedResume(freelancerId).ifPresent(ref -> {
            parts.add(ref.selfIntroduction());
            parts.addAll(jdbcTemplate.queryForList(
                    "SELECT major FROM resume_education WHERE resume_id = ? ORDER BY sort_order",
                    String.class,
                    ref.resumeId()
            ));
            parts.addAll(jdbcTemplate.queryForList(
                    "SELECT job_description FROM resume_career WHERE resume_id = ? ORDER BY sort_order",
                    String.class,
                    ref.resumeId()
            ));
        });
        return parts;
    }

    public List<String> findPositionEmbeddingSource(Long projectId, Long positionId) {
        return jdbcTemplate.query("""
                SELECT p.title,
                       p.current_situation,
                       p.main_task,
                       p.detail_scope,
                       p.extra_note,
                       pp.job_category,
                       pp.job_role,
                       pp.preferred_note
                  FROM project p
                  JOIN project_position pp ON pp.project_id = p.id
                 WHERE p.id = ?
                   AND pp.id = ?
                """, rs -> {
            if (!rs.next()) {
                return List.of();
            }
            List<String> parts = new ArrayList<>();
            parts.add(rs.getString("title"));
            parts.add(rs.getString("current_situation"));
            parts.add(rs.getString("main_task"));
            parts.add(rs.getString("detail_scope"));
            parts.add(rs.getString("extra_note"));
            parts.add(rs.getString("job_category"));
            parts.add(rs.getString("job_role"));
            parts.add(rs.getString("preferred_note"));
            parts.addAll(jdbcTemplate.queryForList(
                    "SELECT skill_code FROM position_skill WHERE position_id = ? ORDER BY skill_code",
                    String.class,
                    positionId
            ));
            return parts;
        }, projectId, positionId);
    }

    public PageImpl<AiLogResponse> findAiLogs(String agentType, String refType, Long refId, String status,
                                              LocalDateTime from, LocalDateTime to, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        appendStringFilter(where, params, "agent_type", "agentType", agentType);
        appendStringFilter(where, params, "ref_type", "refType", refType);
        appendStringFilter(where, params, "status", "status", status);
        if (refId != null) {
            where.append(" AND ref_id = :refId");
            params.addValue("refId", refId);
        }
        if (from != null) {
            where.append(" AND created_at >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            where.append(" AND created_at <= :to");
            params.addValue("to", to);
        }

        long total = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_log" + where,
                params,
                Long.class
        );
        List<AiLogResponse> content = namedJdbcTemplate.query("""
                        SELECT id, agent_type, ref_type, ref_id, status, error_message, created_at
                          FROM ai_agent_log
                        """ + where + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                params,
                aiLogMapper()
        );
        return new PageImpl<>(content, pageable, total);
    }

    public MatchingDiagnosticsResponse findDiagnostics(Long projectId, Long positionId) {
        return new MatchingDiagnosticsResponse(
                findProjectInfo(projectId),
                findPositionInfo(positionId),
                findSnapshotInfo(projectId, positionId),
                findEmbeddingInfo(positionId),
                findLatestRound(projectId, positionId),
                findCountInfo(projectId, positionId),
                findLastAiLog(projectId, positionId)
        );
    }

    /**
     * 진단 대상 프로젝트 목록. 착수금 결제가 끝난 것만 나온다 - 임베딩·스냅샷·라운드는 모집 시작
     * 시점에 만들어지므로, 결제 전 프로젝트를 섞으면 정상인 건이 전부 문제처럼 보인다.
     *
     * @param onlyIssues true면 문제 포지션이 하나 이상인 프로젝트만
     */
    public PageImpl<MatchingProjectSummaryResponse> findMatchingProjects(boolean onlyIssues, Pageable pageable) {
        String having = onlyIssues ? " HAVING SUM(" + POSITION_ISSUE_EXPRESSION + ") > 0" : "";
        long total = count("SELECT COUNT(*) FROM ("
                + "SELECT p.id FROM project p"
                + " LEFT JOIN project_position pp ON pp.project_id = p.id"
                + " WHERE p.payment_status IN (" + DEPOSIT_PAID_STATUSES + ")"
                + " GROUP BY p.id" + having + ") counted");

        List<MatchingProjectSummaryResponse> content = jdbcTemplate.query(
                "SELECT p.id AS project_id, p.title, cp.company_name AS client_name,"
                        + " p.status, p.payment_status, p.recruit_started_at,"
                        + " COUNT(pp.id) AS position_count,"
                        + " COALESCE(SUM(" + POSITION_ISSUE_EXPRESSION + "), 0) AS issue_count,"
                        + " (SELECT MAX(l.created_at) FROM ai_agent_log l"
                        + "   WHERE l.ref_type = 'POSITION'"
                        + "     AND l.ref_id IN (SELECT x.id FROM project_position x"
                        + "                       WHERE x.project_id = p.id)) AS last_ai_log_at"
                        + " FROM project p"
                        // client_id 는 account.id 가 아니라 client_profile.id 다. 회사명이 없어도
                        // 프로젝트는 목록에 나와야 하므로 LEFT JOIN 이다.
                        + " LEFT JOIN client_profile cp ON cp.id = p.client_id"
                        + " LEFT JOIN project_position pp ON pp.project_id = p.id"
                        + " WHERE p.payment_status IN (" + DEPOSIT_PAID_STATUSES + ")"
                        + " GROUP BY p.id, p.title, cp.company_name, p.status, p.payment_status,"
                        + "          p.recruit_started_at" + having
                        + " ORDER BY issue_count DESC, p.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new MatchingProjectSummaryResponse(
                        rs.getLong("project_id"),
                        rs.getString("title"),
                        rs.getString("client_name"),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        toLocalDateTime(rs.getTimestamp("recruit_started_at")),
                        rs.getInt("position_count"),
                        rs.getInt("issue_count"),
                        toLocalDateTime(rs.getTimestamp("last_ai_log_at"))
                ),
                pageable.getPageSize(),
                pageable.getOffset()
        );
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 프로젝트 1건의 모든 포지션 진단.
     *
     * <p>포지션마다 {@link #findDiagnostics(Long, Long)}와 같은 헬퍼를 다시 쓴다. 포지션 수만큼 쿼리가
     * 나가지만(프로젝트당 보통 1~5개) 한 덩어리 SQL로 합치면 단건 조회와 판정식이 갈라질 수 있어서,
     * 같은 값이 나오는 것을 우선했다. 관리자 진단 화면이라 호출 빈도도 낮다.
     */
    public MatchingProjectDiagnosticsResponse findProjectDiagnostics(Long projectId) {
        MatchingDiagnosticsResponse.ProjectInfo project = findProjectInfo(projectId);
        boolean projectSnapshotExists = exists("""
                SELECT 1 FROM matching_snapshot
                 WHERE project_id = ?
                   AND snapshot_type = 'PROJECT'
                 LIMIT 1
                """, projectId);

        List<MatchingProjectDiagnosticsResponse.PositionDiagnostics> positions = new ArrayList<>();
        for (Long positionId : findPositionIds(projectId)) {
            positions.add(buildPositionDiagnostics(projectId, positionId));
        }

        int issueCount = (int) positions.stream()
                .filter(position -> !position.issues().isEmpty())
                .count();
        return new MatchingProjectDiagnosticsResponse(project, projectSnapshotExists, positions.size(),
                issueCount, positions);
    }

    private List<Long> findPositionIds(Long projectId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM project_position WHERE project_id = ? ORDER BY id", Long.class, projectId);
    }

    private MatchingProjectDiagnosticsResponse.PositionDiagnostics buildPositionDiagnostics(
            Long projectId, Long positionId) {
        MatchingDiagnosticsResponse.SnapshotInfo snapshots = findSnapshotInfo(projectId, positionId);
        MatchingDiagnosticsResponse.EmbeddingInfo embeddings = findEmbeddingInfo(positionId);
        MatchingDiagnosticsResponse.RoundInfo round = findLatestRound(projectId, positionId);
        MatchingDiagnosticsResponse.CountInfo counts = findCountInfo(projectId, positionId);

        List<MatchingProjectDiagnosticsResponse.Issue> issues = new ArrayList<>();
        if (!snapshots.positionSnapshotExists()) {
            issues.add(MatchingProjectDiagnosticsResponse.IssueType.POSITION_SNAPSHOT_MISSING.toIssue());
        }
        if (!embeddings.positionEmbeddingExists()) {
            issues.add(MatchingProjectDiagnosticsResponse.IssueType.POSITION_EMBEDDING_MISSING.toIssue());
        }
        if (round.roundId() == null) {
            issues.add(MatchingProjectDiagnosticsResponse.IssueType.ROUND_MISSING.toIssue());
        } else if (counts.exposedCandidateCount() == 0) {
            // 라운드가 없으면 노출 후보가 0인 게 당연하다. 원인이 하나인데 두 줄로 보이면
            // 목록의 issueCount가 부풀어 우선순위 판단을 흐린다.
            issues.add(MatchingProjectDiagnosticsResponse.IssueType.NO_EXPOSED_CANDIDATE.toIssue());
        }

        return new MatchingProjectDiagnosticsResponse.PositionDiagnostics(
                findPositionInfo(positionId),
                snapshots.positionSnapshotExists(),
                embeddings.positionEmbeddingExists(),
                embeddings.positionModel(),
                embeddings.positionDimension(),
                embeddings.freelancerEmbeddingCount(),
                round,
                counts,
                findLastAiLog(projectId, positionId),
                issues
        );
    }

    private MatchingDiagnosticsResponse.ProjectInfo findProjectInfo(Long projectId) {
        return queryOptional("""
                SELECT id, title, status, payment_status
                  FROM project
                 WHERE id = ?
                """, (rs, rowNum) -> new MatchingDiagnosticsResponse.ProjectInfo(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("payment_status")
        ), projectId).orElse(new MatchingDiagnosticsResponse.ProjectInfo(projectId, null, null, null));
    }

    private MatchingDiagnosticsResponse.PositionInfo findPositionInfo(Long positionId) {
        return queryOptional("""
                SELECT id, status, job_category, job_role
                  FROM project_position
                 WHERE id = ?
                """, (rs, rowNum) -> new MatchingDiagnosticsResponse.PositionInfo(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getString("job_category"),
                rs.getString("job_role")
        ), positionId).orElse(new MatchingDiagnosticsResponse.PositionInfo(positionId, null, null, null));
    }

    private MatchingDiagnosticsResponse.SnapshotInfo findSnapshotInfo(Long projectId, Long positionId) {
        boolean projectSnapshotExists = exists("""
                SELECT 1 FROM matching_snapshot
                 WHERE project_id = ?
                   AND snapshot_type = 'PROJECT'
                 LIMIT 1
                """, projectId);
        boolean positionSnapshotExists = exists("""
                SELECT 1 FROM matching_snapshot
                 WHERE position_id = ?
                   AND snapshot_type = 'POSITION'
                 LIMIT 1
                """, positionId);
        return new MatchingDiagnosticsResponse.SnapshotInfo(projectSnapshotExists, positionSnapshotExists);
    }

    private MatchingDiagnosticsResponse.EmbeddingInfo findEmbeddingInfo(Long positionId) {
        // vector_dims 는 pgvector 함수다. 저장된 벡터의 실제 차원을 읽는 유일한 방법이라 그대로 쓴다
        // (모델 설정값을 그대로 내려주면 정작 잡으려는 "옛 차원 벡터가 섞였다"를 못 잡는다).
        // H2 테스트에는 이 함수가 없어서 shared-tables.sql 이 같은 이름의 별칭을 만들어 둔다.
        Optional<PositionEmbeddingRef> embedding = queryOptional(
                "SELECT model, vector_dims(embedding) AS dimension "
                        + "FROM position_embedding WHERE position_id = ?",
                (rs, rowNum) -> new PositionEmbeddingRef(
                        rs.getString("model"),
                        (Integer) rs.getObject("dimension")
                ),
                positionId
        );
        Optional<String> positionModel = embedding.map(PositionEmbeddingRef::model);
        long freelancerEmbeddingCount = count("""
                SELECT COUNT(DISTINCT fe.freelancer_id)
                  FROM freelancer_embedding fe
                  JOIN freelancer_profile fp ON fp.id = fe.freelancer_id
                  JOIN account a ON a.id = fp.account_id
                  JOIN freelancer_condition fc ON fc.account_id = fp.account_id
                  JOIN condition_skill cs ON cs.condition_id = fc.id
                  JOIN project_position pp ON pp.id = ?
                  JOIN position_skill ps ON ps.position_id = pp.id AND ps.skill_code = cs.skill_code
                 WHERE a.status = 'ACTIVE'
                   AND fp.ai_matching_agreed = TRUE
                   AND fp.matching_paused = FALSE
                   AND fc.job_category = pp.job_category
                   AND fc.job_role = pp.job_role
                """, positionId);
        return new MatchingDiagnosticsResponse.EmbeddingInfo(
                positionModel.isPresent(),
                positionModel.orElse(null),
                embedding.map(PositionEmbeddingRef::dimension).orElse(null),
                freelancerEmbeddingCount
        );
    }

    private MatchingDiagnosticsResponse.RoundInfo findLatestRound(Long projectId, Long positionId) {
        return queryOptional("""
                SELECT id, round_no, round_type, status
                  FROM matching_round
                 WHERE project_id = ?
                   AND position_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new MatchingDiagnosticsResponse.RoundInfo(
                rs.getLong("id"),
                rs.getInt("round_no"),
                rs.getString("round_type"),
                rs.getString("status")
        ), projectId, positionId).orElse(new MatchingDiagnosticsResponse.RoundInfo(null, null, null, null));
    }

    private MatchingDiagnosticsResponse.CountInfo findCountInfo(Long projectId, Long positionId) {
        long candidateCount = count("SELECT COUNT(*) FROM matching_candidate WHERE position_id = ?", positionId);
        long exposedCandidateCount = count("""
                SELECT COUNT(*) FROM matching_candidate
                 WHERE position_id = ?
                   AND is_exposed = TRUE
                """, positionId);
        long requestCount = count("""
                SELECT COUNT(*) FROM matching_request
                 WHERE project_id = ?
                   AND position_id = ?
                """, projectId, positionId);
        return new MatchingDiagnosticsResponse.CountInfo(candidateCount, exposedCandidateCount, requestCount);
    }

    private MatchingDiagnosticsResponse.LastAiLogInfo findLastAiLog(Long projectId, Long positionId) {
        return queryOptional("""
                SELECT status, created_at, error_message
                  FROM ai_agent_log
                 WHERE agent_type IN ('EMBEDDING', 'MATCHER', 'GUARD')
                   AND (
                         (ref_type = 'POSITION' AND ref_id = ?)
                      OR (ref_type = 'PROJECT' AND ref_id = ?)
                   )
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new MatchingDiagnosticsResponse.LastAiLogInfo(
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                rs.getString("error_message")
        ), positionId, projectId).orElse(new MatchingDiagnosticsResponse.LastAiLogInfo(null, null, null));
    }

    private Optional<ResumeRef> findLatestCompletedResume(Long freelancerId) {
        return queryOptional("""
                SELECT r.id, r.self_introduction
                  FROM freelancer_profile fp
                  JOIN resume r ON r.account_id = fp.account_id
                 WHERE fp.id = ?
                   AND r.status = 'COMPLETED'
                 ORDER BY r.updated_at DESC NULLS LAST, r.id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new ResumeRef(
                rs.getLong("id"),
                rs.getString("self_introduction")
        ), freelancerId);
    }


    private RowMapper<EmbeddingMissingResponse.Item> missingItemMapper() {
        return (rs, rowNum) -> new EmbeddingMissingResponse.Item(
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getString("model"),
                rs.getString("last_log_status"),
                toLocalDateTime(rs.getTimestamp("last_log_at"))
        );
    }

    private RowMapper<AiLogResponse> aiLogMapper() {
        return (rs, rowNum) -> new AiLogResponse(
                rs.getLong("id"),
                rs.getString("agent_type"),
                rs.getString("ref_type"),
                getNullableLong(rs, "ref_id"),
                rs.getString("status"),
                rs.getString("error_message"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        );
    }

    private static void appendStringFilter(StringBuilder where, MapSqlParameterSource params,
                                           String column, String paramName, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = :").append(paramName);
            params.addValue(paramName, value);
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private boolean exists(String sql, Object... args) {
        return !jdbcTemplate.query(sql, (rs, rowNum) -> 1, args).isEmpty();
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return "ALL";
        }
        return targetType.trim().toUpperCase(Locale.ROOT);
    }

    private static Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record PositionRef(Long projectId, Long positionId) {
    }

    private record PositionEmbeddingRef(String model, Integer dimension) {
    }

    private record ResumeRef(Long resumeId, String selfIntroduction) {
    }
}
