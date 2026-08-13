package com.pairing.admin.project.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 결제 상태. 프로젝트 상태와 별개로 움직인다.
 *
 * <p>착수금 수수료를 내야 모집이 시작되므로, "등록 완료인데 모집이 안 시작된" 프로젝트를
 * 관리자가 구분하려면 이 값이 필요하다. 백엔드
 * {@code com.pairing.project.domain.model.ProjectPaymentStatus} 의 사본이다.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectPaymentStatus {

    DEPOSIT_PENDING("착수금 결제 대기"),
    DEPOSIT_PAID("착수금 결제 완료"),
    SUCCESS_FEE_PENDING("성공보수 결제 대기"),
    SUCCESS_FEE_PAID("성공보수 결제 완료"),
    PAYMENT_FAILED("결제 실패");

    private final String label;

    public static ProjectPaymentStatus find(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectPaymentStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        ProjectPaymentStatus status = find(code);
        return status == null ? code : status.label;
    }
}
