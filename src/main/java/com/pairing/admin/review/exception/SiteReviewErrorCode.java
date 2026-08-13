package com.pairing.admin.review.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SiteReviewErrorCode implements BaseErrorCode {

    SITE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_REVIEW_001",
            "사이트 리뷰를 찾을 수 없습니다.");

    // ADMIN_REVIEW_002(비공개 리뷰는 홍보 불가)는 공개/비공개 개념을 없애면서 사라졌다.
    // 번호는 재사용하지 않는다 — 프론트에 남아 있는 옛 분기가 엉뚱한 메시지를 띄운다.

    private final HttpStatus status;
    private final String code;
    private final String message;
}
