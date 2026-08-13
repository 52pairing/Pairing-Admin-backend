package com.pairing.admin.project.domain;

/**
 * 상세 화면의 "계약유형".
 *
 * <p><b>DB 에 이 값을 담는 컬럼이 없다.</b> {@code project} 에도 {@code contract} 에도 계약의
 * 종류를 구분하는 컬럼이 없고, 플랫폼이 중개하는 계약은 전부 프리랜서 도급 계약이라
 * 사실상 상수다. 그래서 값을 지어내지 않고 <b>고정값이라는 사실을 코드에 남긴다.</b>
 *
 * <p>정규직·파견 같은 다른 계약 형태가 생기면 그때는 여기가 아니라 백엔드
 * {@code contract} 테이블에 컬럼을 만들어야 한다. 관리자 서버가 고정값을 계속 늘리면
 * 화면과 데이터가 조용히 어긋난다.
 *
 * <p>혼동하기 쉬운 값이 둘 있는데 이것과 다르다.
 * <ul>
 *   <li>{@code work_form} — 풀타임 / 파트타임 (근무 형태)</li>
 *   <li>{@code work_style} — 재택 / 상주 / 혼합 (근무 방식)</li>
 * </ul>
 */
public final class ContractType {

    /** 화면에 그대로 찍히는 고정 문구. */
    public static final String FREELANCE = "프리랜서";

    private ContractType() {
    }
}
