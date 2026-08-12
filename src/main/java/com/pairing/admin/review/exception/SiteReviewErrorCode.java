package com.pairing.admin.review.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SiteReviewErrorCode implements BaseErrorCode {

    SITE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_REVIEW_001",
            "사이트 리뷰를 찾을 수 없습니다."),

    /** 비공개인데 홍보 활용은 성립하지 않는다. 메인에 노출될 수 없는 후기를 홍보로 골라두면 조합만 어긋난다. */
    CANNOT_PROMOTE_PRIVATE(HttpStatus.BAD_REQUEST, "ADMIN_REVIEW_002",
            "비공개 리뷰는 홍보로 활용할 수 없습니다. 먼저 공개로 변경해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
