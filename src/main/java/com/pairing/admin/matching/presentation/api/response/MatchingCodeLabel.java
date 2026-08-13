package com.pairing.admin.matching.presentation.api.response;

import java.util.Map;

/**
 * 매칭 진단 응답에 실리는 상태 코드의 <b>화면 문구</b>.
 *
 * <p>진단 응답의 상태 값은 전부 백엔드가 소유한 테이블에서 읽은 원본 코드다({@code DEPOSIT_PAID},
 * {@code EXHAUSTED} 등). 관리자 화면은 사람이 보는 화면이라 코드를 그대로 찍으면 안 된다.
 *
 * <p><b>문구를 서버가 내려준다.</b> 프론트가 코드↔문구 표를 들고 있으면 상태 값이 하나 늘 때마다
 * 양쪽 배포를 맞춰야 하고, 어긋나면 관리자 화면에 영문 코드가 노출된다. {@code issues}를 이미 그렇게
 * 처리했는데({@link MatchingProjectDiagnosticsResponse.IssueType}) 상태 값만 코드로 내보내고 있어서
 * 실제로 관리자 화면에 영어가 찍혔다. 같은 원칙을 상태 값에도 적용한다.
 *
 * <p><b>왜 백엔드 enum 을 가져다 쓰지 않는가.</b> 관리자는 별 레포이고, 백엔드가 소유한 테이블을
 * 엔티티 없이 네이티브 SQL 로만 읽는다(그래서 스키마 변경에 끌려다니지 않는다). 그 대가로 이 표는
 * 백엔드 enum 의 복사본이다. 백엔드가 값을 추가하면 여기에도 추가해야 한다.
 *
 * <p>그래서 <b>모르는 코드는 코드 자체를 돌려준다.</b> 빈칸이나 "알 수 없음"으로 덮으면 관리자가
 * 무슨 상태인지조차 알 수 없다. 영어로라도 보이는 편이 진단 화면에서는 낫고, 영어가 보이면 그게
 * "표에 값을 추가할 때가 됐다"는 신호가 된다.
 */
public final class MatchingCodeLabel {

    private MatchingCodeLabel() {
    }

    /** 백엔드 {@code project.domain.model.ProjectStatus}. */
    private static final Map<String, String> PROJECT_STATUS = Map.of(
            "REGISTERED", "등록 완료",
            "RECRUITING", "모집중",
            "NEGOTIATING", "협상중",
            "CONTRACT_PENDING", "계약 대기",
            "IN_PROGRESS", "진행중",
            "COMPLETION_PENDING", "완료 대기",
            "CLOSED", "종료",
            "CANCELED", "취소됨");

    /** 백엔드 {@code project.domain.model.ProjectPaymentStatus}. */
    private static final Map<String, String> PAYMENT_STATUS = Map.of(
            "DEPOSIT_PENDING", "착수금 결제 대기",
            "DEPOSIT_PAID", "착수금 결제 완료",
            "SUCCESS_FEE_PENDING", "성공보수 결제 대기",
            "SUCCESS_FEE_PAID", "성공보수 결제 완료",
            "PAYMENT_FAILED", "결제 실패");

    /**
     * 백엔드 {@code project.domain.model.PositionStatus}.
     *
     * <p>{@code RECRUITING}/{@code CLOSED}는 프로젝트 상태에도 있는 코드지만 문구가 다르다
     * (프로젝트 "종료" vs 포지션 "모집 종료"). 표를 하나로 합치면 안 된다.
     */
    private static final Map<String, String> POSITION_STATUS = Map.of(
            "RECRUITING", "모집중",
            "FILLED", "모집 완료",
            "CLOSED", "모집 종료");

    /** 백엔드 {@code matching.domain.model.MatchingRoundStatus}. */
    private static final Map<String, String> ROUND_STATUS = Map.of(
            "RUNNING", "진행중",
            "COMPLETED", "완료",
            "FAILED", "실패",
            "EXHAUSTED", "후보 소진");

    /** 백엔드 {@code matching.domain.model.RecommendationType}. */
    private static final Map<String, String> ROUND_TYPE = Map.of(
            "INITIAL", "최초 추천",
            "FREE", "무료 재추천",
            "PAID", "유료 재추천");

    /** AI 서버가 {@code ai_agent_log.status}에 쓰는 값({@code ai_log/repository.py}). */
    private static final Map<String, String> AI_LOG_STATUS = Map.of(
            "SUCCESS", "성공",
            "FAILED", "실패");

    public static String projectStatus(String code) {
        return labelOf(PROJECT_STATUS, code);
    }

    public static String paymentStatus(String code) {
        return labelOf(PAYMENT_STATUS, code);
    }

    public static String positionStatus(String code) {
        return labelOf(POSITION_STATUS, code);
    }

    public static String roundStatus(String code) {
        return labelOf(ROUND_STATUS, code);
    }

    public static String roundType(String code) {
        return labelOf(ROUND_TYPE, code);
    }

    public static String aiLogStatus(String code) {
        return labelOf(AI_LOG_STATUS, code);
    }

    /**
     * {@code null}은 {@code null}로 남긴다. 라운드나 AI 로그가 아예 없으면 상태도 없는 게 정상이고,
     * 그걸 "-" 같은 문자열로 채우면 프론트가 "없음"과 "모르는 값"을 구분할 수 없다.
     */
    private static String labelOf(Map<String, String> labels, String code) {
        if (code == null) {
            return null;
        }
        return labels.getOrDefault(code, code);
    }
}
