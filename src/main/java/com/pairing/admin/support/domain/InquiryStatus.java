package com.pairing.admin.support.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 1:1 문의 상태. 백엔드의 {@code inquiry.status} 와 값이 같아야 한다.
 *
 * <p>스키마 v12 에서 RECEIVED / IN_PROGRESS / CLOSED 를 없애고 두 가지로 줄였다.
 * 값을 추가하려면 백엔드 enum 과 DB CHECK 제약을 같이 고쳐야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum InquiryStatus {

    PENDING("대기중"),
    ANSWERED("답변완료");

    private final String label;
}
