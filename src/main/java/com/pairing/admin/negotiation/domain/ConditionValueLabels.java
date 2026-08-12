package com.pairing.admin.negotiation.domain;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 조건값을 <b>사람이 읽는 표기</b>로 바꾼다.
 *
 * <p>협상 DB 는 값을 계약이 그대로 쓸 수 있는 형태로 저장한다 — {@code "3900000"}, {@code "5 MONTH"},
 * {@code "ONSITE"}. 관리자 화면에 그 코드값이 그대로 찍히고 있어서 변환을 <b>서버에 둔다.</b>
 *
 * <p>프론트에 표를 두지 않는 이유: 같은 코드가 당사자 화면·관리자 화면·계약서에서 각각 번역되면
 * 표기가 갈리고, 백엔드에 enum 값이 하나 늘 때마다 화면 수만큼 고쳐야 한다. 실제로 근무 방식의
 * {@code ANY} 를 화면이 "혼합"으로 부르기로 한 것도 여기 한 곳에만 적어 두면 된다.
 *
 * <p>해석할 수 없는 값은 <b>원문을 그대로 돌려준다.</b> 관리자 화면은 장애 분석에도 쓰는데
 * 이상한 값이 들어왔을 때 "-" 로 지워 버리면 무엇이 잘못됐는지 볼 수가 없다.
 */
public final class ConditionValueLabels {

    private static final Pattern PERIOD = Pattern.compile("^\\s*(\\d+)\\s*(MONTH|WEEK)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    private ConditionValueLabels() {
    }

    /** 조건 타입에 맞는 표기로 바꾼다. 값이 없으면 null. */
    public static String of(ConditionType type, String value) {
        if (type == null || value == null || value.isBlank()) {
            return null;
        }
        return switch (type) {
            case AMOUNT -> amount(value);
            case PERIOD -> period(value);
            case WORK_STYLE -> workStyle(value);
            case WORK_FORM -> workForm(value);
            // START_DATE 는 "2026-09-01" 로 이미 읽을 수 있고, SCOPE/OTHER 는 자유 텍스트다.
            case START_DATE, SCOPE, OTHER -> value;
        };
    }

    /** {@code "3900000"} → {@code "월 3,900,000원"}. 협상 금액은 <b>월 단가</b>라 "월"을 붙인다. */
    public static String amount(String value) {
        String digits = value.replace(",", "").trim();
        if (!digits.matches("\\d+")) {
            return value;
        }
        return "월 " + MONEY.format(Long.parseLong(digits)) + "원";
    }

    /** {@code "5 MONTH"} → {@code "5개월"}, {@code "2 WEEK"} → {@code "2주"}. */
    public static String period(String value) {
        Matcher matcher = PERIOD.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        String unit = matcher.group(2).toUpperCase(Locale.ROOT).equals("WEEK") ? "주" : "개월";
        return matcher.group(1) + unit;
    }

    private static String workStyle(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "REMOTE" -> "재택";
            case "ONSITE" -> "상주";
            // 화면 용어는 "혼합"이다. enum 이름(ANY)을 그대로 쓰면 사용자가 무슨 뜻인지 알 수 없다.
            case "ANY" -> "혼합";
            default -> value;
        };
    }

    private static String workForm(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FULL_TIME" -> "풀타임";
            case "PART_TIME" -> "파트타임";
            case "ANY" -> "모두 가능";
            default -> value;
        };
    }
}
