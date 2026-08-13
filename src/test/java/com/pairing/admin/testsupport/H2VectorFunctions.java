package com.pairing.admin.testsupport;

/**
 * H2 에 없는 pgvector 함수를 테스트에서만 대신 채워 준다. {@code shared-tables.sql} 의
 * {@code CREATE ALIAS ... FOR "..."} 가 이 메서드를 가리킨다.
 *
 * <p>왜 자바 메서드로 두는가: H2 는 {@code CREATE ALIAS ... AS $$ ... $$} 로 자바 소스를 인라인할 수도
 * 있지만, 스프링의 스크립트 러너가 {@code ;} 를 기준으로 문장을 쪼개기 때문에 자바 본문이 그 자리에서
 * 갈라진다. 메서드를 가리키는 형태는 SQL 한 줄에 {@code ;} 가 하나뿐이라 그 문제가 없다.
 */
public final class H2VectorFunctions {

    private H2VectorFunctions() {
    }

    /**
     * {@code vector_dims(embedding)} 대역. 운영에서는 저장된 벡터의 실제 차원이 나오고, 테스트에서는
     * {@code position_embedding.embedding} 에 넣어 둔 숫자 문자열을 그대로 돌려준다
     * (테스트가 확인할 것은 "차원 값이 응답까지 실려 오는가" 하나뿐이다).
     */
    public static Integer vectorDims(String embedding) {
        if (embedding == null || embedding.isBlank()) {
            return null;
        }
        return Integer.valueOf(embedding.trim());
    }
}
