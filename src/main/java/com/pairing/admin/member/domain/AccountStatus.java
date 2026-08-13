package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계정 상태. 백엔드와 이름이 정확히 같아야 한다. (DB에 문자열로 저장)
 *
 * <p><b>값을 하나라도 빠뜨리면 회원 목록 조회가 통째로 깨진다.</b> 문자열을 enum 으로 역매핑하는
 * 시점에 {@code IllegalArgumentException} 이 나기 때문이다. 백엔드의
 * {@code com.pairing.account.domain.model.AccountStatus} 와 항상 같이 고친다.
 *
 * <p><b>여기에 SUSPENDED 를 넣으면 안 된다.</b> 관리자 정지는 상태값이 아니라
 * Redis {@code SUSPEND:{accountId}} 키와 {@code account.suspended_at} 으로 표현한다.
 * 백엔드 enum 에 SUSPENDED 가 없어서, 이 서버가 status 에 그 값을 써 넣으면
 * <b>백엔드가 해당 계정을 읽는 순간 터진다.</b> 화면에 보여 줄 "정지" 라벨은
 * {@link MemberStatusFilter} 가 만든다.
 *
 * <p>{@link #LOCKED} 는 회원이 비밀번호를 5회 틀려 자동으로 잠긴 상태이고 본인이 이메일 인증으로
 * 푼다. 관리자가 건 정지와는 다르며, 한 값으로 뭉치면 요약 카드의 정지 건수에 실패 잠금이 섞인다.
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
