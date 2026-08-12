package com.pairing.admin.negotiation.infrastructure.persistence;

import com.pairing.admin.negotiation.domain.NegotiationMessageType;
import com.pairing.admin.negotiation.domain.SenderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * negotiation_message 테이블 매핑. 읽기 전용.
 *
 * <p>이 테이블은 <b>해시 체인으로 봉인</b>돼 있다({@code prev_hash}·{@code content_hash}).
 * 분쟁 시 위변조를 검증하는 기록이라 관리자 서버가 한 줄이라도 고치면 체인이 끊긴다.
 * 그래서 해시 컬럼은 매핑조차 하지 않는다 — 무결성 검증은 백엔드의 {@code /log-integrity} 가 한다.
 *
 * <p>한 라운드에 <b>조건별로 여러 발언</b>이 들어간다. 와이어 초안이 "1라운드 = 제안 1건"을
 * 가정했는데 실제 구조가 달라서, 라운드 아코디언 안을 대화 표로 바꿨다.
 */
@Entity
@Table(name = "negotiation_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationMessageJpaEntity {

    @Id
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    /** 어느 쟁점에 대한 발언인지. 시스템 안내는 쟁점이 없어 null 이다. */
    @Column(name = "condition_id")
    private Long conditionId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private NegotiationMessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "proposed_value")
    private String proposedValue;

    /** 사람이 누른 응답(ACCEPT/REJECT). 대리인 발언은 null. */
    @Column(name = "response", length = 20)
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
