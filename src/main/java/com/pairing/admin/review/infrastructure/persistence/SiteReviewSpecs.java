package com.pairing.admin.review.infrastructure.persistence;

import com.pairing.admin.review.domain.PartyRole;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 사이트 리뷰 목록 검색 조건 조각들. 값이 없으면 {@code null} 을 돌려주고,
 * {@code and(null)} 이 그 조건을 무시한다.
 */
public final class SiteReviewSpecs {

    private SiteReviewSpecs() {
    }

    public static Specification<SiteReviewJpaEntity> scoreEquals(Integer score) {
        if (score == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("score"), score);
    }

    public static Specification<SiteReviewJpaEntity> writerRoleEquals(PartyRole writerRole) {
        if (writerRole == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("writerRole"), writerRole);
    }

    /** 홍보 활용 여부. {@code null} 이면 홍보/미홍보를 모두 보여준다. */
    public static Specification<SiteReviewJpaEntity> promotedEquals(Boolean promoted) {
        if (promoted == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("promoted"), promoted);
    }

    /**
     * 회원명·내용·프로젝트명 통합 검색.
     *
     * <p>화면 검색창이 하나라서 입력값이 무엇인지 알 수 없다. 세 가지를 OR 로 묶는다.
     *
     * <p>작성자명과 프로젝트명은 site_review 에 없다. 그래서 서비스가 먼저 검색어에 걸리는
     * {@code accountIds}/{@code projectIds} 를 찾아 넘겨주고, 여기서는 id 로만 비교한다.
     * 조인으로 한 번에 처리하면 페이징 카운트 쿼리까지 조인이 붙어 느려지고,
     * 다른 테이블 엔티티를 이 도메인에 끌어와야 한다.
     *
     * <p>빈 목록은 조건에서 뺀다. {@code IN ()} 은 SQL 문법 오류다.
     */
    public static Specification<SiteReviewJpaEntity> keywordMatches(String keyword,
                                                                    Collection<Long> accountIds,
                                                                    Collection<Long> projectIds) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String pattern = "%" + keyword.trim().toLowerCase() + "%";

        return (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(cb.lower(root.get("content")), pattern));

            if (accountIds != null && !accountIds.isEmpty()) {
                matches.add(root.get("writerAccountId").in(accountIds));
            }
            if (projectIds != null && !projectIds.isEmpty()) {
                matches.add(root.get("projectId").in(projectIds));
            }

            return cb.or(matches.toArray(new Predicate[0]));
        };
    }
}
