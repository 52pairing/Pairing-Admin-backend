package com.pairing.admin.negotiation.infrastructure.persistence;

import com.pairing.admin.negotiation.domain.ConditionType;
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
 * negotiation_condition 테이블 매핑. 읽기 전용.
 *
 * <p><b>마지노선({@code client_floor}·{@code freelancer_floor})은 일부러 매핑하지 않았다.</b>
 * 협상 설계상 마지노선은 상대에게 절대 노출하지 않는 값이고, 당사자 화면조차 자기 것만 본다.
 * 관리자 화면이 그 선을 보여 줘야 할 이유가 없는데 매핑해 두면 나중에 응답 DTO 에 딸려 나가기
 * 쉽다. 필요해지면 그때 근거를 남기고 열 것.
 */
@Entity
@Table(name = "negotiation_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationConditionJpaEntity {

    @Id
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 30)
    private ConditionType conditionType;

    /** 확정값. 합의 전에는 null 이고, 합의된 것만 계약으로 넘어간다. */
    @Column(name = "agreed_value")
    private String agreedValue;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "round_count", nullable = false)
    private int roundCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    public boolean isAgreed() {
        return "AGREED".equals(status);
    }
}
