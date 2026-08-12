package com.pairing.admin.negotiation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ai_agent_log 테이블 매핑. <b>파이썬(AI 서버)이 쓰고, 이 서버는 읽기만 한다.</b>
 *
 * <p><b>매핑하지 않은 컬럼이 있고, 전부 의도한 것이다.</b>
 * <ul>
 *   <li>{@code request_json} / {@code response_json} — JSONB 다. 화면에 필요한 값은 라운드 번호
 *       하나뿐이라 통째로 끌어오지 않고 조회 쿼리에서 {@code request_json->>'round'} 로만 꺼낸다.
 *       프롬프트 원문에는 양측 마지노선이 들어 있어서, 엔티티에 얹어 두면 언젠가 응답에
 *       딸려 나간다.</li>
 *   <li>{@code cost_amount} — <b>전부 null 이다.</b> 모델 단가를 코드에 박지 않기로 한 판단이라
 *       (구글이 단가를 바꾸면 조용히 틀린 값이 쌓인다) 비용 칸은 만들지 않는다. 토큰 수가
 *       남아 있으므로 단가가 확정되면 나중에 역산할 수 있다.</li>
 * </ul>
 *
 * <p>{@code agent_type} 과 {@code status} 는 {@code @Enumerated} 대신 <b>문자열로</b> 받는다.
 * 이 표에 값을 넣는 쪽은 파이썬이고, 파이썬에 대리인이 하나 늘면 {@code @Enumerated} 는
 * 조회 자체를 예외로 끊어 버린다. 변환은 도메인 enum 의 {@code from(...)} 이 맡는다.
 */
@Entity
@Table(name = "ai_agent_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAgentLogJpaEntity {

    @Id
    private Long id;

    @Column(name = "agent_type", nullable = false, length = 30)
    private String agentType;

    /** 연관 도메인. 협상 호출은 {@code NEGOTIATION} 이다. */
    @Column(name = "ref_type", length = 30)
    private String refType;

    /** 연관 리소스 ID. {@code ref_type=NEGOTIATION} 이면 negotiation.id 다. */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** <b>모델 호출 구간만</b> 잰 시간이다. 사용자가 체감하는 총 왕복 시간이 아니다. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    /**
     * 호출 시각. <b>UTC 다.</b>
     *
     * <p>이 표만 UTC 이고 {@code negotiation_message}·{@code negotiation.agent_started_at} 은
     * KST 다. 두 화면을 나란히 놓고 보는 관리자에게 9시간 어긋난 시각을 주면 안 되므로,
     * 조회 쿼리에서 KST 로 맞춰 내보낸다.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
