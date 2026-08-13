package com.pairing.admin.project.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계약 상태. 프로젝트 상세의 "계약 정보" 카드에 쓴다.
 *
 * <p>백엔드 {@code com.pairing.contract.domain.model.ContractStatus} 의 사본이다.
 * 프로젝트 상태와 이름이 겹치는 값(IN_PROGRESS · COMPLETION_PENDING)이 있지만 라벨이 다르다.
 * 계약 쪽 COMPLETION_PENDING 은 "정산 대기" 이고 프로젝트 쪽은 "완료 대기" 다.
 * 한 화면에 둘이 같이 나오므로 라벨을 섞으면 안 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {

    DRAFT("작성 중"),
    SIGN_PENDING("서명 대기"),
    SIGNED("체결 완료"),
    IN_PROGRESS("진행중"),
    COMPLETION_PENDING("정산 대기"),
    COMPLETED("종료"),
    REJECTED("서명 거부"),
    TERMINATED("중도 파기");

    private final String label;

    public static ContractStatus find(String code) {
        if (code == null) {
            return null;
        }
        for (ContractStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        ContractStatus status = find(code);
        return status == null ? code : status.label;
    }
}
