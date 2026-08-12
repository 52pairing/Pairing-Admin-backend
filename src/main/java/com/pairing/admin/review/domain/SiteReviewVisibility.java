package com.pairing.admin.review.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사이트 리뷰 공개 여부.
 *
 * <p><b>기본값은 PUBLIC 이다.</b> 사후 관리 방식이다 — 후기는 대부분 문제가 없는데 관리자가
 * 하나하나 열어 공개로 바꾸면 그 일이 밀리는 동안 후기가 하나도 안 보인다. 그래서 공개로 두고,
 * 부적절한 내용이 보이면 PRIVATE 으로 내린다.
 *
 * <p>공개라고 바로 메인에 뜨지는 않는다. 메인 노출은 홍보 활용까지 켜야 한다.
 * <b>공개는 기본값, 홍보는 선별</b>이다.
 */
@Getter
@RequiredArgsConstructor
public enum SiteReviewVisibility {

    PRIVATE("비공개"),
    PUBLIC("공개");

    private final String label;
}
