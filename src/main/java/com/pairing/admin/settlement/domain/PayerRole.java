package com.pairing.admin.settlement.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 납부자 구분({@code settlement.payer_role}). 화면의 클라이언트/프리랜서 필터다.
 *
 * <p>백엔드 {@code com.pairing.meta.domain.model.PartyRole} 의 사본이고, 회원 관리의
 * {@code Role} 과 값이 겹치지만 의미가 다르다. 이쪽은 "이 정산을 누가 내는가" 다.
 * 같은 프로젝트에서 클라이언트와 프리랜서가 <b>각자</b> 수수료를 내므로, 프로젝트 하나에
 * 두 역할의 정산이 나란히 생긴다.
 *
 * <p>{@code review.PartyRole} 과 값이 같지만 재사용하지 않는다. 리뷰 작성자와 정산 납부자는
 * 같이 움직일 이유가 없고, 한쪽 화면 사정으로 값을 고치면 다른 쪽이 조용히 따라 바뀐다.
 */
@Getter
@RequiredArgsConstructor
public enum PayerRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;

    public static PayerRole find(String code) {
        if (code == null) {
            return null;
        }
        for (PayerRole role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        PayerRole role = find(code);
        return role == null ? code : role.label;
    }
}
