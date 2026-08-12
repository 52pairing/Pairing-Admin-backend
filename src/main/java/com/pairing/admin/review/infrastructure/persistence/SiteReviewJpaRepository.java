package com.pairing.admin.review.infrastructure.persistence;

import com.pairing.admin.review.domain.SiteReviewVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SiteReviewJpaRepository
        extends JpaRepository<SiteReviewJpaEntity, Long>, JpaSpecificationExecutor<SiteReviewJpaEntity> {

    long countByPromotedTrue();

    long countByVisibility(SiteReviewVisibility visibility);

    /** 이번 달 작성 건수. */
    long countByCreatedAtAfter(LocalDateTime from);

    /** 평균 별점. 후기가 한 건도 없으면 null 이다. */
    @Query("SELECT AVG(r.score) FROM SiteReviewJpaEntity r")
    Double findAverageScore();

    /**
     * 별점 분포 그래프용 집계.
     *
     * <p>없는 별점은 결과에 아예 안 나온다. 5~1 을 0 으로 채우는 일은 호출부가 한다 —
     * 그래프는 0건인 별점도 빈 막대로 보여줘야 한다.
     *
     * @return {@code [score, count]} 배열들
     */
    @Query("SELECT r.score, COUNT(r) FROM SiteReviewJpaEntity r GROUP BY r.score")
    List<Object[]> countGroupByScore();

    /**
     * 작성자 표시명. 클라이언트는 회사명, 프리랜서는 이름을 쓴다.
     *
     * <p>account·client_profile 은 다른 도메인 테이블이라 엔티티로 매핑하지 않고 필요한 값만 읽는다.
     * 행마다 되물으면 페이지 크기만큼 쿼리가 늘어나므로 id 목록을 한 번에 받는다.
     *
     * <p>회사명이 비어 있으면 계정 이름으로 대체한다. 기업 정보를 아직 안 채운 클라이언트가 있다.
     *
     * @return {@code [accountId, displayName]} 배열들
     */
    @Query(value = """
            SELECT a.id,
                   COALESCE(NULLIF(c.company_name, ''), a.name)
            FROM account a
            LEFT JOIN client_profile c
                   ON c.account_id = a.id AND c.deleted_at IS NULL
            WHERE a.id IN (:accountIds)
            """, nativeQuery = true)
    List<Object[]> findWriterNames(@Param("accountIds") Collection<Long> accountIds);

    /**
     * 프로젝트명. project 는 다른 도메인 테이블이라 위와 같은 이유로 필요한 값만 읽는다.
     *
     * @return {@code [projectId, title]} 배열들
     */
    @Query(value = """
            SELECT p.id, p.title
            FROM project p
            WHERE p.id IN (:projectIds)
            """, nativeQuery = true)
    List<Object[]> findProjectTitles(@Param("projectIds") Collection<Long> projectIds);

    /**
     * 검색어에 걸리는 작성자 계정 id.
     *
     * <p>회원명 검색을 위해 필요하다. 작성자명은 site_review 에 없어서 이 테이블만으로는 걸 수 없다.
     * 계정 이름과 회사명 둘 다 본다 — 관리자는 화면에 보이는 값(클라이언트면 회사명)으로 검색한다.
     */
    @Query(value = """
            SELECT a.id
            FROM account a
            LEFT JOIN client_profile c
                   ON c.account_id = a.id AND c.deleted_at IS NULL
            WHERE LOWER(a.name) LIKE :pattern
               OR LOWER(COALESCE(c.company_name, '')) LIKE :pattern
            """, nativeQuery = true)
    List<Long> findAccountIdsByNameLike(@Param("pattern") String pattern);

    /** 검색어에 걸리는 프로젝트 id. 프로젝트명 검색을 위해 필요하다. */
    @Query(value = """
            SELECT p.id
            FROM project p
            WHERE LOWER(p.title) LIKE :pattern
            """, nativeQuery = true)
    List<Long> findProjectIdsByTitleLike(@Param("pattern") String pattern);
}
