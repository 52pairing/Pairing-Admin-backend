package com.pairing.admin.review.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 리뷰 작성자 구분. 백엔드의 {@code site_review.writer_role} 과 값이 같아야 한다.
 *
 * <p>회원 관리의 {@code Role} 과 값이 겹치지만 같은 것이 아니다. {@code Role} 에는 ADMIN 이 있고
 * 리뷰 작성자에는 ADMIN 이 올 수 없다. DB CHECK 제약도 이 두 값만 허용한다.
 */
@Getter
@RequiredArgsConstructor
public enum PartyRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;
}
