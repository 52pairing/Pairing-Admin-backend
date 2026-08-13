package com.pairing.admin.matching.infrastructure.persistence;

import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
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
              JOIN resume r ON r.freelancer_id = fp.id AND r.status = 'COMPLETED'
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
                  JOIN resume r ON r.freelancer_id = fp.id AND r.status = 'COMPLETED'
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

    public List<String> findFreelancerEmbeddingSource(Long freelancerId) {
        Optional<ResumeRef> resume = findLatestCompletedResume(freelancerId);
        List<String> parts = new ArrayList<>();
        parts.addAll(findFreelancerConditionSource(freelancerId));
        resume.ifPresent(ref -> {
            parts.add(ref.selfIntroduction());
            parts.addAll(jdbcTemplate.queryForList(
                    "SELECT major FROM resume_education WHERE resume_id = ? ORDER BY sort_order, id",
                    String.class,
                    ref.resumeId()
            ));
            parts.addAll(jdbcTemplate.queryForList(
                    "SELECT job_description FROM resume_career WHERE resume_id = ? ORDER BY sort_order, id",
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
        Optional<String> positionModel = queryOptional(
                "SELECT model FROM position_embedding WHERE position_id = ?",
                (rs, rowNum) -> rs.getString("model"),
                positionId
        );
        long freelancerEmbeddingCount = count("""
                SELECT COUNT(DISTINCT fe.freelancer_id)
                  FROM freelancer_embedding fe
                  JOIN freelancer_profile fp ON fp.id = fe.freelancer_id
                  JOIN account a ON a.id = fp.account_id
                  JOIN freelancer_condition fc ON fc.freelancer_id = fp.id
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
                         ref_id = ?
                      OR ref_id = ?
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
                SELECT id, self_introduction
                  FROM resume
                 WHERE freelancer_id = ?
                   AND status = 'COMPLETED'
                 ORDER BY completed_at DESC NULLS LAST, id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new ResumeRef(
                rs.getLong("id"),
                rs.getString("self_introduction")
        ), freelancerId);
    }

    private List<String> findFreelancerConditionSource(Long freelancerId) {
        return jdbcTemplate.query("""
                SELECT fc.job_category,
                       fc.job_role,
                       fc.work_style,
                       fc.work_form,
                       fc.pay_unit,
                       fc.pay_amount,
                       fc.min_accept_amount,
                       fc.available_from,
                       fc.period_value,
                       fc.period_unit,
                       fc.career_years,
                       cs.skill_code,
                       cs.skill_level
                  FROM freelancer_condition fc
                  LEFT JOIN condition_skill cs ON cs.condition_id = fc.id
                 WHERE fc.freelancer_id = ?
                 ORDER BY cs.skill_code
                """, rs -> {
            List<String> parts = new ArrayList<>();
            while (rs.next()) {
                parts.add(rs.getString("job_category"));
                parts.add(rs.getString("job_role"));
                parts.add(rs.getString("work_style"));
                parts.add(rs.getString("work_form"));
                parts.add(rs.getString("pay_unit"));
                parts.add(String.valueOf(rs.getObject("pay_amount")));
                parts.add(String.valueOf(rs.getObject("min_accept_amount")));
                parts.add(String.valueOf(rs.getObject("available_from")));
                parts.add(String.valueOf(rs.getObject("period_value")));
                parts.add(rs.getString("period_unit"));
                parts.add(String.valueOf(rs.getObject("career_years")));
                parts.add(rs.getString("skill_code"));
                parts.add(rs.getString("skill_level"));
            }
            return parts;
        }, freelancerId);
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

    private record ResumeRef(Long resumeId, String selfIntroduction) {
    }
}
