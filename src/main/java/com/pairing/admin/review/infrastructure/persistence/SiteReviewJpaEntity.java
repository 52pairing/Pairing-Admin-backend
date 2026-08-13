package com.pairing.admin.review.infrastructure.persistence;

import com.pairing.admin.review.domain.PartyRole;
import com.pairing.admin.review.domain.SiteReviewVisibility;
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
 * 관리자는 공개·홍보 여부만 바꾼다. 별점과 내용은 작성 후 아무도 바꿀 수 없다 —
 * 관리자가 문구를 다듬을 수 있으면 그건 더 이상 사용자 후기가 아니다.
 *
 * <p>그래서 세터 대신 {@link #updateVisibility} 하나만 열어 둔다.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private SiteReviewVisibility visibility;

    /** 홍보 활용 여부. 공개된 후기 중에서도 홍보로 쓸 것만 따로 고른다. */
    @Column(name = "promoted", nullable = false)
    private boolean promoted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * [관리자] 공개·홍보 설정 변경.
     *
     * <p>둘을 한 번에 받는다. 화면에서 "공개로 변경"과 "홍보 활용"이 따로 있지만, 비공개인데
     * 홍보 활용인 상태는 의미가 없어서 한 요청으로 묶어 어긋난 조합이 남지 않게 한다.
     */
    public void updateVisibility(SiteReviewVisibility visibility, boolean promoted) {
        this.visibility = visibility;
        this.promoted = promoted;
    }
}
