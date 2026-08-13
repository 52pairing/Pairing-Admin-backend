package com.pairing.admin.project.domain;

import java.util.Locale;
import java.util.Map;

/**
 * {@code project} · {@code project_position} · {@code contract} 에 문자열로 저장된 코드값의 한글 표시명.
 *
 * <p><b>백엔드 {@code com.pairing.meta.domain.model} 패키지의 사본이다.</b>
 * enum 이 아니라 {@code Map} 으로 둔 이유는 {@code MemberCodeLabels} 와 같다 — 백엔드에 값이
 * 추가됐는데 여기에 없으면 {@code valueOf} 가 조회 전체를 터뜨리지만, 맵은 코드 원문을 그대로
 * 돌려주므로 화면에 {@code AI_ML} 같은 코드가 보일 뿐 조회는 계속된다.
 *
 * <p>근무 방식 {@code ANY} 는 <b>"혼합"</b> 이다. enum 이름이 아니라 화면 용어를 따른다
 * ({@code negotiation.ConditionValueLabels} 와 같은 표기). 근무 형태의 {@code ANY} 는
 * "모두 가능" 이라 서로 다르니 한 맵에 합치지 않는다.
 */
public final class ProjectCodeLabels {

    private ProjectCodeLabels() {
    }

    /**
     * 코드를 한글 표시명으로 바꾼다.
     *
     * @return 모르는 코드면 코드 원문. null 이면 null
     */
    public static String labelOf(Map<String, String> labels, String code) {
        if (code == null) {
            return null;
        }
        return labels.getOrDefault(code.trim().toUpperCase(Locale.ROOT), code);
    }

    public static final Map<String, String> JOB_CATEGORY = Map.of(
            "DEVELOPMENT", "개발",
            "DESIGN", "디자인");

    /** 근무 방식. {@code ANY} 를 "모두 가능" 이 아니라 "혼합" 으로 부르는 것이 화면 용어다. */
    public static final Map<String, String> WORK_STYLE = Map.of(
            "REMOTE", "재택",
            "ONSITE", "상주",
            "ANY", "혼합");

    public static final Map<String, String> WORK_FORM = Map.of(
            "FULL_TIME", "풀타임",
            "PART_TIME", "파트타임",
            "ANY", "모두 가능");

    public static final Map<String, String> PERIOD_UNIT = Map.of(
            "MONTH", "개월",
            "WEEK", "주");

    /** {@code project_position.status}. 프로젝트 상태와 값 체계가 다르다. */
    public static final Map<String, String> POSITION_STATUS = Map.of(
            "RECRUITING", "모집중",
            "NEGOTIATING", "협상중",
            "CONTRACT_PENDING", "계약 대기",
            "CONFIRMED", "확정",
            "CLOSED", "마감",
            "CANCELED", "취소됨");

    /**
     * 직무. 피그마 목록은 "개발 · 프론트엔드" 처럼 짧게 쓰지만, 백엔드 라벨은
     * "프론트엔드 개발자" 라 여기서도 백엔드 표기를 따른다. 두 화면이 같은 직무를 다르게
     * 부르면 검색 결과를 대조할 수 없다.
     */
    public static final Map<String, String> JOB_ROLE = Map.ofEntries(
            Map.entry("FRONTEND", "프론트엔드 개발자"),
            Map.entry("BACKEND", "백엔드 개발자"),
            Map.entry("FULLSTACK", "풀스택 개발자"),
            Map.entry("WEB_PUBLISHER", "웹 퍼블리셔"),
            Map.entry("IOS", "iOS 개발자"),
            Map.entry("ANDROID", "Android 개발자"),
            Map.entry("CROSS_PLATFORM", "크로스플랫폼 개발자"),
            Map.entry("DATA_ENGINEER", "데이터 엔지니어"),
            Map.entry("DATA_ANALYST", "데이터 분석가"),
            Map.entry("AI_ML", "AI·ML 엔지니어"),
            Map.entry("DEVOPS", "DevOps 엔지니어"),
            Map.entry("CLOUD_INFRA", "클라우드·인프라 엔지니어"),
            Map.entry("DBA", "DBA"),
            Map.entry("SECURITY", "보안 엔지니어"),
            Map.entry("GAME", "게임 개발자"),
            Map.entry("BLOCKCHAIN", "블록체인 개발자"),
            Map.entry("EMBEDDED", "임베디드·하드웨어 개발자"),
            Map.entry("QA", "QA 엔지니어"),
            Map.entry("UX_UI_DESIGNER", "UX·UI 디자이너"),
            Map.entry("PRODUCT_DESIGNER", "제품 디자이너"),
            Map.entry("WEB_DESIGNER", "웹 디자이너"),
            Map.entry("GRAPHIC_DESIGNER", "그래픽 디자이너"),
            Map.entry("BX_DESIGNER", "BX·브랜드 디자이너"),
            Map.entry("ILLUSTRATOR", "일러스트레이터"),
            Map.entry("MOTION_DESIGNER", "모션·영상 디자이너"),
            Map.entry("THREE_D_DESIGNER", "3D 디자이너"));
}
