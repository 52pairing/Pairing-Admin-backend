package com.pairing.admin.support.infrastructure.persistence;

import com.pairing.admin.member.domain.Role;
import com.pairing.admin.support.domain.InquiryStatus;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

/**
 * 문의 목록 검색 조건 조각들. {@code AccountSpecs} 와 같은 규칙이다 —
 * 값이 없으면 {@code null} 을 돌려주고, {@code and(null)} 이 그 조건을 무시한다.
 */
public final class InquirySpecs {

    private InquirySpecs() {
    }

    public static Specification<InquiryJpaEntity> writerRoleEquals(Role writerRole) {
        if (writerRole == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("writerRole"), writerRole);
    }

    public static Specification<InquiryJpaEntity> statusEquals(InquiryStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * 회원명·제목·문의번호를 한 번에 검색한다. 화면 검색창이 하나라서 입력값이 무엇인지 알 수 없다.
     *
     * <p>문의번호({@code QNA-20260805-0012})는 컬럼이 아니라 작성일과 id 로 만든 값이라
     * 그대로 LIKE 를 걸 수 없다. 그래서 같은 규칙으로 DB 에서 문자열을 조립해 비교한다.
     * 관리자가 번호 전체를 붙여넣든 뒤 네 자리만 넣든 걸리게 하려고 부분 일치로 둔다.
     */
    public static Specification<InquiryJpaEntity> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String pattern = "%" + keyword.trim().toLowerCase() + "%";

        return (root, query, cb) -> {
            Expression<String> inquiryNo = cb.concat(
                    cb.concat(cb.literal("qna-"), cb.function("to_char", String.class,
                            root.get("createdAt"), cb.literal("YYYYMMDD"))),
                    cb.concat(cb.literal("-"), cb.function("lpad", String.class,
                            root.get("id").as(String.class), cb.literal(4), cb.literal("0"))));

            return cb.or(
                    cb.like(cb.lower(root.get("writerName")), pattern),
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(inquiryNo, pattern));
        };
    }
}
