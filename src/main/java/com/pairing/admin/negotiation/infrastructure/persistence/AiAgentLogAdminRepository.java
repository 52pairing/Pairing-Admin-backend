package com.pairing.admin.negotiation.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 토큰 사용량 탭의 {@code ai_agent_log} 조회. <b>읽기 전용</b>이다.
 *
 * <p>로그를 남기는 쪽은 파이썬이고, 남긴 뒤에는 아무도 고치지 않는다(운영 검토용 기록이다).
 * 쓰기 메서드를 열지 않으려고 {@code JpaRepository} 가 아니라 {@link Repository} 를 상속한다.
 *
 * <h2>왜 {@code ref_type}/{@code ref_id} 로 좁히는가</h2>
 * 이 표는 <b>협상 전용이 아니다.</b> 이력서 파싱·매칭·챗봇·계약서 호출이 전부 같이 쌓인다.
 * 좁히지 않으면 협상 화면에 남의 도메인 토큰이 섞여 <b>수치가 통째로 틀린다.</b>
 * 실측으로도 갈린다 — 협상 A2A 1회는 {@code latency_ms} 15,000~17,000 인데 다른 도메인 호출은
 * 400~5,000 이다.
 *
 * <h2>{@code agent_type} 은 왜 조건에 안 넣는가</h2>
 * {@code NEGOTIATOR} 만 걸러 내고 싶어지지만, 같은 협상 건에 가드({@code GUARD}) 호출이 붙으면
 * 그 토큰도 <b>그 협상이 쓴 토큰</b>이다. 빼고 보여 주면 사용량이 실제보다 적게 보인다.
 * 대신 줄마다 {@code agentType} 을 함께 내려 화면에서 구분할 수 있게 한다.
 */
public interface AiAgentLogAdminRepository extends Repository<AiAgentLogJpaEntity, Long> {

    /** 호출 1건. */
    interface CallRow {
        Long getLogId();

        /** 협상 라운드. 라운드를 안 싣는 호출도 있어 null 이 될 수 있다. */
        Integer getRoundNo();

        String getAgentType();

        String getModel();

        String getStatus();

        Integer getPromptTokens();

        Integer getOutputTokens();

        Integer getLatencyMs();

        Integer getRetryCount();

        String getErrorMessage();

        LocalDateTime getCalledAt();
    }

    /**
     * 협상 1건이 일으킨 모델 호출 목록. 시간 오름차순 — 협상 로그 탭과 읽는 순서를 맞춘다.
     *
     * <p><b>라운드 번호는 {@code request_json->>'round'} 에서 꺼낸다.</b> 별도 컬럼이 없어서다.
     * 숫자인지 확인하고 캐스팅하는 이유는, {@code ::int} 가 실패하면 <b>행 하나 때문에 조회
     * 전체가 예외로 끝나기</b> 때문이다. 이 값은 파이썬이 넣는 자유 형식 JSON 이라 이 서버가
     * 형식을 보장할 수 없다. 형식이 어긋나면 그 줄의 라운드만 비운다.
     *
     * <p><b>시각은 KST 로 바꿔서 내보낸다.</b> 이 표의 {@code created_at} 만 UTC 이고
     * {@code negotiation_message} 는 KST 라, 그대로 주면 관리자가 두 탭을 나란히 놓고 볼 때
     * 9시간 어긋난 표를 보게 된다. 보정을 화면에 맡기지 않는 이유는, 이게 화면이 알아야 할
     * 사정이 아니라 <b>표 두 개의 저장 기준이 다른 서버 사정</b>이기 때문이다.
     */
    @Query(value = """
            SELECT l.id                              AS logId,
                   CASE WHEN l.request_json->>'round' ~ '^[0-9]+$'
                        THEN (l.request_json->>'round')::int
                   END                               AS roundNo,
                   l.agent_type                      AS agentType,
                   l.model                           AS model,
                   l.status                          AS status,
                   l.prompt_tokens                   AS promptTokens,
                   l.output_tokens                   AS outputTokens,
                   l.latency_ms                      AS latencyMs,
                   l.retry_count                     AS retryCount,
                   l.error_message                   AS errorMessage,
                   l.created_at + INTERVAL '9 hours' AS calledAt
              FROM ai_agent_log l
             WHERE l.ref_type = 'NEGOTIATION'
               AND l.ref_id = :negotiationId
             ORDER BY l.created_at ASC, l.id ASC
            """, nativeQuery = true)
    List<CallRow> findCalls(@Param("negotiationId") Long negotiationId);

    /** 탭 상단 카드. */
    interface UsageRow {
        long getTotalCalls();

        long getSuccessCalls();

        long getFailedCalls();

        long getPromptTokens();

        long getOutputTokens();

        long getRetryCount();

        Double getAverageLatencyMs();

        Integer getMaxLatencyMs();

        /** 이 협상에 쓰인 모델명(중복 제거, 쉼표 구분). 모델을 갈아탄 뒤 수치를 비교할 때 쓴다. */
        String getModels();
    }

    /**
     * 협상 1건의 사용량 집계.
     *
     * <p>합계는 {@code COALESCE} 로 0 을 채운다. 호출이 한 건도 없으면(대리인이 아직 안 돈 협상)
     * {@code SUM} 이 NULL 로 오는데, 화면 카드에 빈칸이 뜨는 것보다 0 이 맞다.
     *
     * <p><b>평균 응답시간은 성공한 호출만</b> 센다. 실패는 대개 타임아웃이라 그 시간을 섞으면
     * "모델이 보통 얼마나 걸리나"라는 질문의 답이 망가진다. 대신 실패 건수를 따로 보여 준다.
     * 최댓값은 실패까지 포함한다 — 최악의 경우를 보려는 값이라 거르면 의미가 없다.
     */
    @Query(value = """
            SELECT COUNT(*)                                                  AS totalCalls,
                   COUNT(*) FILTER (WHERE l.status = 'SUCCESS')              AS successCalls,
                   COUNT(*) FILTER (WHERE l.status = 'FAILED')               AS failedCalls,
                   COALESCE(SUM(l.prompt_tokens), 0)                         AS promptTokens,
                   COALESCE(SUM(l.output_tokens), 0)                         AS outputTokens,
                   COALESCE(SUM(l.retry_count), 0)                           AS retryCount,
                   AVG(l.latency_ms) FILTER (WHERE l.status = 'SUCCESS')     AS averageLatencyMs,
                   MAX(l.latency_ms)                                         AS maxLatencyMs,
                   STRING_AGG(DISTINCT l.model, ', ')                        AS models
              FROM ai_agent_log l
             WHERE l.ref_type = 'NEGOTIATION'
               AND l.ref_id = :negotiationId
            """, nativeQuery = true)
    UsageRow findUsage(@Param("negotiationId") Long negotiationId);
}
