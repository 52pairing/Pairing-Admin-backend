package com.pairing.admin.member.infrastructure.persistence;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import org.springframework.data.jpa.domain.Specification;

/**
 * 회원 목록 검색 조건 조각들.
 *
 * <p>각 메서드는 값이 없으면 {@code null} 을 돌려준다. {@code Specification.and(null)} 은
 * 그 조건을 무시하므로, 호출부에서 if 문 없이 조건을 이어 붙일 수 있다.
 */
public final class AccountSpecs {

    private AccountSpecs() {
    }

    /** 탈퇴(소프트 삭제)한 계정은 목록에서 제외한다. */
    public static Specification<AccountJpaEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<AccountJpaEntity> roleEquals(Role role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<AccountJpaEntity> statusEquals(AccountStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * 이름·이메일·휴대폰 부분 일치.
     *
     * <p>데이터가 쌓이면 이 LIKE 검색이 풀스캔이 된다. 회원 수가 늘어나면
     * 검색 대상 컬럼에 인덱스를 두거나(pg_trgm 등) 검색 전용 컬럼을 검토한다.
     */
    public static Specification<AccountJpaEntity> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String pattern = "%" + keyword.trim().toLowerCase() + "%";

        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(root.get("phone"), pattern));
    }
}
