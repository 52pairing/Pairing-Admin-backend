package com.pairing.admin.project.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 상태. 화면 상단 탭이 이 값 그대로다.
 *
 * <p><b>백엔드 {@code com.pairing.project.domain.model.ProjectStatus} 의 사본이다.</b>
 * 값이 어긋나면 탭 카운트가 조용히 0 이 되므로 백엔드에서 상수가 늘면 여기도 맞춘다.
 *
 * <p>목록 필터로 쓸 때는 {@code null} 이 "전체" 다. 취소됨(CANCELED)도 목록에 남는다 —
 * 관리자는 취소된 프로젝트를 확인할 수 있어야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectStatus {

    REGISTERED("등록 완료"),
    RECRUITING("모집중"),
    NEGOTIATING("협상중"),
    CONTRACT_PENDING("계약 대기"),
    IN_PROGRESS("진행중"),
    COMPLETION_PENDING("완료 대기"),
    CLOSED("종료"),
    CANCELED("취소됨");

    private final String label;

    /**
     * 모르는 코드가 와도 예외를 던지지 않는다.
     *
     * <p>백엔드에 상태가 하나 추가됐는데 이 enum 에 없으면 {@code valueOf} 는
     * 목록 조회 전체를 터뜨린다. 관리 화면이 상태값 하나 때문에 죽는 것보다,
     * 그 줄만 {@code null} 로 두고 원문 코드를 함께 내려 주는 편이 낫다.
     */
    public static ProjectStatus find(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /** 모르는 코드면 코드 원문을 그대로 돌려준다. */
    public static String labelOf(String code) {
        ProjectStatus status = find(code);
        return status == null ? code : status.label;
    }
}
