package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계정 상태. 백엔드와 이름이 정확히 같아야 한다. (DB에 문자열로 저장)
 *
 * <p>주의: 백엔드는 <b>정지(SUSPENDED)</b> 를 이 컬럼이 아니라 Redis 에서 관리한다(스키마 v12 결정).
 * 이 서버는 Redis 를 보지 않으므로, 관리자 화면의 "정지"는 여기서 LOCKED 로 처리한다.
 * 백엔드의 Redis 기반 정지와 완전히 연동하려면 Redis 를 함께 공유하도록 확장해야 한다. (README 참고)
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus {

    PENDING("가입 대기"),
    ACTIVE("정상"),
    LOCKED("잠금"),
    WITHDRAWN("탈퇴");

    private final String label;
}
