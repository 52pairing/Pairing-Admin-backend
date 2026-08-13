package com.pairing.admin.review.presentation.api.response;

import com.pairing.admin.review.domain.PartyRole;
import com.pairing.admin.review.infrastructure.persistence.SiteReviewJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 사이트 리뷰 목록 한 줄. 화면 표의 컬럼과 1:1 로 맞춘다.
 *
 * <p>관리자는 작성자 실명(클라이언트는 회사명)을 그대로 본다. 사용자 화면에서는 가려서 보여주지만,
 * 공개 여부를 판단하려면 누가 썼는지 알아야 한다.
 */
@Schema(description = "사이트 리뷰 목록 행")
public record SiteReviewRowResponse(

        @Schema(description = "리뷰 ID (설정 변경에 사용)", example = "1")
        Long siteReviewId,

        @Schema(description = "화면에 보이는 리뷰번호", example = "REV-001")
        String siteReviewNo,

        @Schema(description = "작성자 구분", example = "CLIENT")
        PartyRole writerRole,

        @Schema(description = "작성자 표시명. 클라이언트는 회사명, 프리랜서는 이름", example = "삼성전자")
        String writerName,

        @Schema(description = "별점 1~5", example = "5")
        int score,

        @Schema(description = "후기 내용", example = "매칭 속도가 빠르고 AI 협상 기능이 정말 유용했습니다.")
        String content,

        @Schema(description = "대상 프로젝트명. 프로젝트가 지워졌으면 null", example = "쇼핑몰 관리자 페이지")
        String projectTitle,

        @Schema(description = "홍보 활용 여부. true 면 메인 노출 후보", example = "true")
        boolean promoted,

        @Schema(description = "작성일", example = "2026-08-01T09:30:00")
        LocalDateTime createdAt
) {

    public static SiteReviewRowResponse from(SiteReviewJpaEntity review, String writerName, String projectTitle) {
        return new SiteReviewRowResponse(
                review.getId(),
                siteReviewNo(review),
                review.getWriterRole(),
                writerName,
                review.getScore(),
                review.getContent(),
                projectTitle,
                review.isPromoted(),
                review.getCreatedAt());
    }

    /**
     * 화면에 보이는 리뷰번호. 컬럼이 아니라 id 로 만든다.
     *
     * <p>와이어프레임은 {@code REV-001} 처럼 짧은 번호를 쓴다. id 를 네 자리로 채워 쓰되,
     * 만 건을 넘기면 자릿수가 늘어난다 — 자리수를 고정해 잘라내면 서로 다른 리뷰가 같은 번호가 된다.
     */
    private static String siteReviewNo(SiteReviewJpaEntity review) {
        return "REV-" + String.format("%03d", review.getId());
    }
}
