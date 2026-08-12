package com.pairing.admin.negotiation.infrastructure.persistence;

import com.pairing.admin.negotiation.domain.NegotiationStatus;
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
 * negotiation 테이블 매핑. <b>백엔드와 공유하는 테이블</b>이고 이 서버는 읽기만 한다.
 *
 * <p>협상은 매칭 수락으로 생기고 대리인·사람 응답으로만 바뀐다. 관리자가 값을 고칠 일이 없어
 * 세터도 생성자도 열지 않는다. 여기서 협상 상태를 손대면 진행 중인 계약·정산까지 어긋난다.
 *
 * <p><b>화면에 필요한 컬럼만 매핑했다.</b> {@code ddl-auto=validate} 는 매핑한 컬럼의 존재만
 * 검증하므로 나머지(마지노선·읽음시각 등)를 빼도 기동에 문제가 없고, 관리자가 볼 이유도 없다.
 * 특히 {@code floor_amount} 같은 마지노선 계열은 <b>당사자에게도 상대 것을 감추는 값</b>이라
 * 굳이 이 서버로 끌어오지 않는다.
 */
@Entity
@Table(name = "negotiation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationJpaEntity {

    @Id
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NegotiationStatus status;

    @Column(name = "total_round", nullable = false)
    private int totalRound;

    /** 합의된 <b>월 단가</b>(원). 총액이 아니다. 타결 전 null. */
    @Column(name = "agreed_amount")
    private Long agreedAmount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "end_reason")
    private String endReason;
}
