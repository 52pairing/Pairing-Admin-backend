package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 목록 화면의 "상태" 필터·배지 값.
 *
 * <p>{@link AccountStatus} 를 그대로 쓰지 않는 이유는 <b>정지가 상태 컬럼에 없기 때문</b>이다.
 * 정지는 {@code account.suspended_at} 으로 표현되고 status 는 ACTIVE 그대로 남는다.
 * 그래서 화면에 보이는 상태는 두 컬럼을 합쳐야 나온다.
 *
 * <p>합치는 규칙은 {@link #of} 한 곳에만 둔다. 목록·상세·요약 카드가 각자 계산하면
 * 한 화면은 "정지", 다른 화면은 "정상" 으로 보이는 일이 생긴다.
 *
 * <p>정지가 status 보다 우선한다. 정지된 회원의 status 는 대개 ACTIVE 인데 그대로
 * "정상" 이라고 표시하면 관리자가 정지 여부를 알 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum MemberStatusFilter {

    PENDING("가입 대기"),
    ACTIVE("정상"),
    LOCKED("잠금"),
    SUSPENDED("정지"),
    WITHDRAWN("탈퇴");

    private final String label;

    /**
     * 화면에 보여 줄 상태를 정한다.
     *
     * @param status    account.status 값
     * @param suspended account.suspended_at 이 채워져 있는가
     */
    public static MemberStatusFilter of(AccountStatus status, boolean suspended) {
        if (suspended) {
            return SUSPENDED;
        }
        return switch (status) {
            case PENDING -> PENDING;
            case ACTIVE -> ACTIVE;
            case LOCKED -> LOCKED;
            case WITHDRAWN -> WITHDRAWN;
        };
    }
}
