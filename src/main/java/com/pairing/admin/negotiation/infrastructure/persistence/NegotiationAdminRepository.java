package com.pairing.admin.negotiation.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI Agent 관리 화면의 협상 조회. <b>읽기 전용</b>이다.
 *
 * <p>엔티티 매핑 대신 네이티브 쿼리 + 인터페이스 프로젝션을 쓴다. 목록 한 줄에 필요한
 * 프로젝트명·클라이언트명·프리랜서명이 {@code project}, {@code client_profile},
 * {@code freelancer_profile}, {@code account} 네 테이블에 흩어져 있는데, 화면에 쓰지도 않을
 * 엔티티 네 개를 이 서버에 다시 매핑하면 <b>백엔드와 스키마가 어긋날 때 검증만 두 배로 깨진다.</b>
 * 이 서버는 {@code ddl-auto=validate} 라 컬럼 하나만 달라도 기동이 막힌다.
 *
 * <p>협상은 관리자가 만들거나 고치지 않는다. 쓰기 메서드를 열지 않으려고
 * {@code JpaRepository} 가 아니라 {@link Repository} 를 상속한다.
 */
public interface NegotiationAdminRepository extends Repository<NegotiationJpaEntity, Long> {

    Optional<NegotiationJpaEntity> findById(Long id);

    /** 목록 한 줄. 관리자는 마스킹 없이 양측 이름을 본다. */
    interface ListRow {
        Long getNegotiationId();

        Long getProjectId();

        String getProjectTitle();

        /**
         * 클라이언트 <b>회사명</b>({@code client_profile.company_name}).
         *
         * <p>같은 계정의 {@code account.name} 은 <b>대표자명</b>이라 다른 값이다. 화면의
         * "클라이언트" 는 발주한 회사를 가리키므로 회사명이 맞고, 협상 도메인의 당사자 이름
         * 조회도 같은 컬럼을 쓴다. 대표자명으로 바꾸면 관리자 화면만 다른 이름을 보게 된다.
         */
        String getClientName();

        /**
         * 프리랜서 이름({@code account.name}).
         *
         * <p>이쪽은 개인이라 프로필에 이름이 없고 계정에서 가져온다. 클라이언트와 출처가
         * 다른 것은 의도된 것이다.
         */
        String getFreelancerName();

        String getStatus();

        Integer getTotalRound();

        LocalDateTime getStartedAt();

        LocalDateTime getEndedAt();
    }

    /**
     * 협상 목록.
     *
     * <p>{@code :status} · {@code :keyword} 가 null 이면 그 조건을 건너뛴다. 키워드는
     * 프로젝트명·클라이언트명·프리랜서명을 함께 본다(대소문자 무시).
     *
     * <p>정렬을 파라미터로 받지 않고 <b>시작일 역순으로 고정</b>한다. 네이티브 쿼리에 정렬을
     * 문자열로 이어 붙이면 SQL 주입 경로가 되고, 관리자 화면에서 최근 협상부터 보는 것 말고
     * 다른 정렬을 쓸 일이 아직 없다.
     */
    @Query(value = """
            SELECT n.id                AS negotiationId,
                   n.project_id        AS projectId,
                   p.title             AS projectTitle,
                   c.company_name      AS clientName,
                   fa.name             AS freelancerName,
                   n.status            AS status,
                   n.total_round       AS totalRound,
                   n.started_at        AS startedAt,
                   n.ended_at          AS endedAt
              FROM negotiation n
              JOIN project p            ON p.id  = n.project_id
              LEFT JOIN client_profile c     ON c.id  = p.client_id
              LEFT JOIN freelancer_profile f ON f.id  = n.freelancer_id
              LEFT JOIN account fa           ON fa.id = f.account_id
             WHERE (:status IS NULL OR n.status = :status)
               AND (:keyword IS NULL
                    OR p.title  ILIKE '%' || :keyword || '%'
                    OR c.company_name ILIKE '%' || :keyword || '%'
                    OR fa.name  ILIKE '%' || :keyword || '%')
             ORDER BY n.started_at DESC, n.id DESC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ListRow> findPage(@Param("status") String status,
                           @Param("keyword") String keyword,
                           @Param("size") int size,
                           @Param("offset") long offset);

    /** 목록 전체 건수. 페이지 계산용이라 조인은 검색 조건에 필요한 것만 남긴다. */
    @Query(value = """
            SELECT COUNT(*)
              FROM negotiation n
              JOIN project p            ON p.id  = n.project_id
              LEFT JOIN client_profile c     ON c.id  = p.client_id
              LEFT JOIN freelancer_profile f ON f.id  = n.freelancer_id
              LEFT JOIN account fa           ON fa.id = f.account_id
             WHERE (:status IS NULL OR n.status = :status)
               AND (:keyword IS NULL
                    OR p.title  ILIKE '%' || :keyword || '%'
                    OR c.company_name ILIKE '%' || :keyword || '%'
                    OR fa.name  ILIKE '%' || :keyword || '%')
            """, nativeQuery = true)
    long countPage(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 상세 화면 머리말. 목록과 같은 조인이지만 한 건만 읽는다.
     *
     * <p>목록 쿼리를 재사용하지 않는 이유는 목록이 페이징·검색 파라미터에 묶여 있어서다.
     * 상세에서 그 파라미터에 더미값을 넣어 부르면 나중에 목록 조건이 바뀔 때 상세가 조용히
     * 같이 망가진다.
     */
    @Query(value = """
            SELECT n.id                AS negotiationId,
                   n.project_id        AS projectId,
                   p.title             AS projectTitle,
                   c.company_name      AS clientName,
                   fa.name             AS freelancerName,
                   n.status            AS status,
                   n.total_round       AS totalRound,
                   n.started_at        AS startedAt,
                   n.ended_at          AS endedAt
              FROM negotiation n
              JOIN project p            ON p.id  = n.project_id
              LEFT JOIN client_profile c     ON c.id  = p.client_id
              LEFT JOIN freelancer_profile f ON f.id  = n.freelancer_id
              LEFT JOIN account fa           ON fa.id = f.account_id
             WHERE n.id = :negotiationId
            """, nativeQuery = true)
    Optional<ListRow> findHeader(@Param("negotiationId") Long negotiationId);

    /** 상단 카드 집계. */
    interface SummaryRow {
        long getTotal();

        long getInProgress();

        long getAgreed();

        long getFailed();

        Double getAverageRound();

        Double getAverageDurationDays();
    }

    /**
     * 목록 화면 상단 카드.
     *
     * <p>평균 소요일수는 <b>종료된 협상만</b> 센다. 진행 중인 건을 "지금까지 걸린 시간"으로
     * 섞으면 오래 열려 있는 협상 하나가 평균을 계속 끌어올려 수치가 의미를 잃는다.
     * 대상이 없으면 NULL 이 나오므로 받는 쪽에서 0 으로 바꾼다.
     */
    @Query(value = """
            SELECT COUNT(*)                                                        AS total,
                   COUNT(*) FILTER (WHERE n.status = 'IN_PROGRESS')                AS inProgress,
                   COUNT(*) FILTER (WHERE n.status = 'AGREED')                     AS agreed,
                   COUNT(*) FILTER (WHERE n.status = 'FAILED')                     AS failed,
                   AVG(n.total_round)                                              AS averageRound,
                   AVG(EXTRACT(EPOCH FROM (n.ended_at - n.started_at)) / 86400.0)
                       FILTER (WHERE n.ended_at IS NOT NULL)                       AS averageDurationDays
              FROM negotiation n
            """, nativeQuery = true)
    SummaryRow findSummary();
}
