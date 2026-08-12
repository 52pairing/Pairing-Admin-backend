package com.pairing.admin.review.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.review.domain.PartyRole;
import com.pairing.admin.review.domain.SiteReviewVisibility;
import com.pairing.admin.review.exception.SiteReviewErrorCode;
import com.pairing.admin.review.infrastructure.persistence.SiteReviewJpaEntity;
import com.pairing.admin.review.infrastructure.persistence.SiteReviewJpaRepository;
import com.pairing.admin.review.infrastructure.persistence.SiteReviewSpecs;
import com.pairing.admin.review.presentation.api.response.SiteReviewRowResponse;
import com.pairing.admin.review.presentation.api.response.SiteReviewSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사이트 리뷰 관리. (관리자 &gt; 사이트 리뷰 관리)
 *
 * <p>백엔드와 같은 site_review 테이블을 쓴다. 후기 작성·삭제는 하지 않고 공개·홍보 여부만 바꾼다.
 * 여기서 공개로 바꾸면 비로그인 메인 노출 대상이 되므로, 이 서비스의 실수가 곧 대외 노출이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteReviewAdminService {

    /** 그래프에 항상 표시하는 별점 구간. 0건이어도 빈 막대로 보여준다. */
    private static final List<Integer> SCORE_RANGE = List.of(5, 4, 3, 2, 1);

    private final SiteReviewJpaRepository siteReviewJpaRepository;

    /** 요약 카드 6개 + 별점 분포. 모두 필터와 무관한 전체 기준이다. */
    @Transactional(readOnly = true)
    public SiteReviewSummaryResponse getSummary() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();

        Double average = siteReviewJpaRepository.findAverageScore();
        long totalCount = siteReviewJpaRepository.count();
        long promotedCount = siteReviewJpaRepository.countByPromotedTrue();

        return new SiteReviewSummaryResponse(
                average == null ? 0.0 : average,
                totalCount,
                siteReviewJpaRepository.countByCreatedAtAfter(monthStart),
                promotedCount,
                totalCount - promotedCount,
                siteReviewJpaRepository.countByVisibility(SiteReviewVisibility.PUBLIC),
                scoreDistribution());
    }

    /**
     * 별점·작성자 구분·공개 여부·홍보 여부로 걸러낸다. keyword 는 회원명·내용·프로젝트명을 함께 검색한다.
     *
     * <p>작성자명과 프로젝트명은 site_review 에 없어서, 검색어에 걸리는 id 를 먼저 찾아
     * {@code IN} 조건으로 넘긴다. 검색어를 넣을 때만 두 번의 추가 조회가 생긴다.
     *
     * <p>검색어가 아주 흔한 단어면 {@code IN} 목록이 길어진다. 회원·프로젝트가 수만 건이 되면
     * 조회 전용 뷰나 검색 컬럼을 두는 쪽으로 바꿔야 한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<SiteReviewRowResponse> search(Integer score, PartyRole writerRole,
                                                      SiteReviewVisibility visibility, Boolean promoted,
                                                      String keyword, Pageable pageable) {

        Specification<SiteReviewJpaEntity> spec = Specification.allOf(
                SiteReviewSpecs.scoreEquals(score),
                SiteReviewSpecs.writerRoleEquals(writerRole),
                SiteReviewSpecs.visibilityEquals(visibility),
                SiteReviewSpecs.promotedEquals(promoted),
                SiteReviewSpecs.keywordMatches(keyword, matchingAccountIds(keyword), matchingProjectIds(keyword)));

        Page<SiteReviewJpaEntity> page = siteReviewJpaRepository.findAll(spec, pageable);

        // 작성자명·프로젝트명은 다른 테이블 값이다. 행마다 조회하면 페이지 크기만큼 쿼리가 늘어나므로
        // 이 페이지에 필요한 id 만 모아 한 번씩 읽는다.
        Map<Long, String> writerNames = writerNames(page.getContent());
        Map<Long, String> projectTitles = projectTitles(page.getContent());

        return PageResponse.from(page, review -> SiteReviewRowResponse.from(
                review,
                writerNames.getOrDefault(review.getWriterAccountId(), fallbackWriterName(review)),
                projectTitles.get(review.getProjectId())));
    }

    /**
     * 공개·홍보 설정을 바꾼다.
     *
     * <p>비공개 + 홍보 활용은 막는다. 메인에 나갈 수 없는 후기를 홍보로 골라두면, 나중에 공개로
     * 바꾸는 순간 검수 없이 홍보에 실린다.
     */
    @Transactional
    public SiteReviewRowResponse updateVisibility(Long siteReviewId, SiteReviewVisibility visibility,
                                                  boolean promoted, Long actorAdminId) {

        if (visibility == SiteReviewVisibility.PRIVATE && promoted) {
            throw new BusinessException(SiteReviewErrorCode.CANNOT_PROMOTE_PRIVATE);
        }

        SiteReviewJpaEntity review = siteReviewJpaRepository.findById(siteReviewId)
                .orElseThrow(() -> new BusinessException(SiteReviewErrorCode.SITE_REVIEW_NOT_FOUND));

        review.updateVisibility(visibility, promoted);

        // 대외 노출이 바뀌는 작업이다. 누가 무엇을 공개했는지 남긴다.
        log.info("[사이트 리뷰 공개 설정 변경] siteReviewId={}, visibility={}, promoted={}, actorAdminId={}",
                siteReviewId, visibility, promoted, actorAdminId);

        List<SiteReviewJpaEntity> one = List.of(review);
        return SiteReviewRowResponse.from(
                review,
                writerNames(one).getOrDefault(review.getWriterAccountId(), fallbackWriterName(review)),
                projectTitles(one).get(review.getProjectId()));
    }

    // ------------------------------------------------------------------

    private Map<Integer, Long> scoreDistribution() {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        SCORE_RANGE.forEach(score -> distribution.put(score, 0L));

        for (Object[] row : siteReviewJpaRepository.countGroupByScore()) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return distribution;
    }

    /** 검색어에 걸리는 작성자 계정 id. 검색어가 없으면 조회하지 않는다. */
    private List<Long> matchingAccountIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return siteReviewJpaRepository.findAccountIdsByNameLike(likePattern(keyword));
    }

    /** 검색어에 걸리는 프로젝트 id. 검색어가 없으면 조회하지 않는다. */
    private List<Long> matchingProjectIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return siteReviewJpaRepository.findProjectIdsByTitleLike(likePattern(keyword));
    }

    private String likePattern(String keyword) {
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private Map<Long, String> writerNames(List<SiteReviewJpaEntity> reviews) {
        Set<Long> accountIds = reviews.stream()
                .map(SiteReviewJpaEntity::getWriterAccountId)
                .collect(Collectors.toSet());
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return toMap(siteReviewJpaRepository.findWriterNames(accountIds));
    }

    private Map<Long, String> projectTitles(List<SiteReviewJpaEntity> reviews) {
        Set<Long> projectIds = reviews.stream()
                .map(SiteReviewJpaEntity::getProjectId)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return toMap(siteReviewJpaRepository.findProjectTitles(projectIds));
    }

    /** {@code [id, 문자열]} 행들을 맵으로. 값이 null 인 행은 버린다 — 맵에 없으면 호출부가 기본값을 쓴다. */
    private Map<Long, String> toMap(List<Object[]> rows) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[1] != null) {
                result.put(((Number) row[0]).longValue(), (String) row[1]);
            }
        }
        return result;
    }

    /** 계정이 탈퇴로 지워졌으면 이름을 알 수 없다. 표에 빈칸을 두지 않도록 구분만 보여준다. */
    private String fallbackWriterName(SiteReviewJpaEntity review) {
        return review.getWriterRole().getLabel();
    }
}
