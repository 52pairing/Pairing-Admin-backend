package com.pairing.admin.review.infrastructure.persistence;

import com.pairing.admin.review.domain.PartyRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * site_review 테이블 매핑. <b>백엔드와 공유하는 테이블</b>이다.
 *
 * <p>이 서버는 후기를 <b>만들지도 지우지도 않는다.</b> 작성은 사용자가 계약 완료 후 백엔드에서 하고,
 * 관리자는 홍보 활용 여부만 바꾼다. 별점과 내용은 작성 후 아무도 바꿀 수 없다 —
 * 관리자가 문구를 다듬을 수 있으면 그건 더 이상 사용자 후기가 아니다.
 *
 * <p>그래서 세터 대신 {@link #updatePromoted} 하나만 열어 둔다.
 */
@Entity
@Table(name = "site_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "writer_account_id", nullable = false)
    private Long writerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_role", nullable = false, length = 10)
    private PartyRole writerRole;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "content", length = 500)
    private String content;

    /**
     * 홍보 활용 여부. 사용자에게 후기가 보이느냐를 결정하는 <b>유일한</b> 스위치다.
     *
     * <p>테이블에는 {@code visibility} 컬럼이 남아 있지만 매핑하지 않는다. 공개/비공개는
     * 아무것도 바꾸지 않는 스위치였다 — 후기 원문은 어차피 이 관리 화면 밖으로 나가지 않고,
     * 사용자는 홍보로 고른 것만 본다. 끄면 이미 안 보이는데 그 위에 공개 여부를 또 두면
     * 관리자가 두 번 눌러야 하고, 두 값이 어긋난 조합까지 관리해야 한다.
     */
    @Column(name = "promoted", nullable = false)
    private boolean promoted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** [관리자] 홍보 활용 설정 변경. */
    public void updatePromoted(boolean promoted) {
        this.promoted = promoted;
    }
}
