package com.pairing.admin.settlement.infrastructure.persistence;

import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 거래·정산 관리 화면의 조회 전용 저장소. (요구사항 R39)
 *
 * <p>목록 한 줄에 필요한 값이 {@code settlement} 한 테이블에 다 있지 않다. 프로젝트명은
 * {@code project}, 회원명은 {@code account}(프리랜서) 또는 {@code client_profile}(클라이언트)
 * 에 있다. 그래서 <b>엔티티를 늘리지 않고 네이티브 쿼리 + 인터페이스 프로젝션</b>으로
 * 필요한 컬럼만 읽는다. ({@code MemberAdminQueryRepository} 와 같은 방침)
 *
 * <p>쓰기 메서드를 열지 않으려고 {@code JpaRepository} 가 아니라 {@link Repository} 를 상속한다.
 * <b>여기서 정산 상태를 바꾸면 실제 입금과 장부가 어긋난다.</b> 결제 처리 경로는 백엔드 하나뿐이어야 한다.
 *
 * <p>정렬은 <b>최근 생성순으로 고정</b>이다. 네이티브 쿼리에 정렬을 문자열로 이어 붙이면
 * SQL 주입 경로가 된다.
 *
 * <p>날짜·문자열 파라미터에 {@code CAST} 를 붙인 이유는 프로젝트 쪽과 같다. 조건을 비우면
 * 타입 없는 null 이 바인딩되어 PostgreSQL 이 거절한다.
 *
 * <p><b>도메인 타입이 {@link AccountJpaEntity} 인 것은 자리 표시자다.</b> 이유는
 * {@code ProjectAdminQueryRepository} 와 같다 — {@code settlement} 용 엔티티를 새로 만들면
 * 테스트의 {@code ddl-auto=create-drop} 이 {@code shared-tables.sql} 대신 그 엔티티로 테이블을
 * 만들어, 같은 테이블을 읽는 회원 관리 쿼리가 깨진다.
 */
public interface SettlementAdminQueryRepository extends Repository<AccountJpaEntity, Long> {

    // ==================================================================
    // 목록
    // ==================================================================

    /**
     * 정산 목록 한 줄.
     *
     * <p>R39 의 표 컬럼(정산번호·프로젝트·회원·유형·기준금액·수수료율·수수료·상태·기한·완료일)과 대응한다.
     */
    interface ListRow {
        Long getSettlementId();

        String getSettlementNo();

        Long getProjectId();

        String getProjectTitle();

        /** 납부자 계정 ID. {@code settlement.payer_account_id} 는 {@code account.id} 를 직접 가리킨다. */
        Long getPayerAccountId();

        /** 납부자 이름({@code account.name}). 클라이언트면 담당자명이다. */
        String getPayerName();

        /** 납부자가 클라이언트일 때의 회사명. 프리랜서면 null */
        String getCompanyName();

        String getPayerRole();

        String getPhase();

        /** 수수료를 매기는 기준이 된 금액. 프로젝트 예산 또는 계약 총액이다. */
        Long getBaseAmount();

        BigDecimal getFeeRate();

        /** 등급 할인. 비율이 아니라 <b>%p 차감</b>이다. 다이아/마스터만 1.00 이 붙는다. */
        BigDecimal getGradeDiscount();

        /** {@code fee_rate - grade_discount}. 실제로 적용된 요율이며 음수가 되지 않게 0 에서 막는다. */
        BigDecimal getEffectiveFeeRate();

        Long getFeeAmount();

        String getStatus();

        LocalDate getDueDate();

        LocalDateTime getPaidAt();

        LocalDateTime getCreatedAt();
    }

    /**
     * 정산 목록.
     *
     * <p>조건은 null 이면 건너뛴다.
     * <ul>
     *   <li>{@code status} — 결제 완료/대기/미납/실패/취소됨</li>
     *   <li>{@code phase} — 착수금 / 성공보수</li>
     *   <li>{@code payerRole} — 클라이언트 / 프리랜서</li>
     *   <li>{@code keyword} — 정산번호·프로젝트명·회원명·회사명을 함께 본다.
     *       R39 는 "정산번호 검색" 만 적고 있지만, 관리자가 특정 회원의 미납을 찾는 일이 잦아
     *       같은 칸에서 이름도 걸리게 했다. 정산번호는 대소문자를 가리지 않도록 ILIKE 를 쓴다.</li>
     *   <li>{@code fromDate} / {@code toDateExclusive} — <b>정산 생성일</b> 기준이다. 기한(due_date)이나
     *       완료일(paid_at)이 아니다. 완료일로 걸면 아직 안 낸 건이 통째로 빠진다.
     *       위쪽 경계는 포함하지 않는 날짜이며 서비스가 "종료일 + 1일" 을 계산해 넘긴다.</li>
     * </ul>
     *
     * <p>회원명은 역할에 따라 출처가 다르다. 프리랜서는 {@code account.name}, 클라이언트는
     * {@code client_profile.company_name} 이 화면에 맞는 이름이라 둘 다 내려 주고 화면이 고른다.
     */
    @Query(value = """
            SELECT s.id              AS settlementId,
                   s.settlement_no   AS settlementNo,
                   s.project_id      AS projectId,
                   p.title           AS projectTitle,
                   s.payer_account_id AS payerAccountId,
                   a.name            AS payerName,
                   cp.company_name   AS companyName,
                   s.payer_role      AS payerRole,
                   s.phase           AS phase,
                   s.base_amount     AS baseAmount,
                   s.fee_rate        AS feeRate,
                   s.grade_discount  AS gradeDiscount,
                   GREATEST(s.fee_rate - s.grade_discount, 0) AS effectiveFeeRate,
                   s.fee_amount      AS feeAmount,
                   s.status          AS status,
                   s.due_date        AS dueDate,
                   s.paid_at         AS paidAt,
                   s.created_at      AS createdAt
              FROM settlement s
              LEFT JOIN project p         ON p.id = s.project_id
              LEFT JOIN account a         ON a.id = s.payer_account_id
              LEFT JOIN client_profile cp ON cp.account_id = a.id AND cp.deleted_at IS NULL
             WHERE (CAST(:status    AS varchar) IS NULL OR s.status     = CAST(:status AS varchar))
               AND (CAST(:phase     AS varchar) IS NULL OR s.phase      = CAST(:phase AS varchar))
               AND (CAST(:payerRole AS varchar) IS NULL OR s.payer_role = CAST(:payerRole AS varchar))
               AND (CAST(:keyword AS varchar) IS NULL
                    OR s.settlement_no ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR p.title         ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR a.name          ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR cp.company_name ILIKE '%' || CAST(:keyword AS varchar) || '%')
               AND (CAST(:fromDate AS date) IS NULL OR s.created_at >= CAST(:fromDate AS date))
               AND (CAST(:toDateExclusive AS date) IS NULL OR s.created_at < CAST(:toDateExclusive AS date))
             ORDER BY s.created_at DESC, s.id DESC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ListRow> findPage(@Param("status") String status,
                           @Param("phase") String phase,
                           @Param("payerRole") String payerRole,
                           @Param("keyword") String keyword,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDateExclusive") LocalDate toDateExclusive,
                           @Param("size") int size,
                           @Param("offset") long offset);

    /** 목록 전체 건수. 페이지 계산용이라 검색 조건에 필요한 조인만 남긴다. */
    @Query(value = """
            SELECT COUNT(*)
              FROM settlement s
              LEFT JOIN project p         ON p.id = s.project_id
              LEFT JOIN account a         ON a.id = s.payer_account_id
              LEFT JOIN client_profile cp ON cp.account_id = a.id AND cp.deleted_at IS NULL
             WHERE (CAST(:status    AS varchar) IS NULL OR s.status     = CAST(:status AS varchar))
               AND (CAST(:phase     AS varchar) IS NULL OR s.phase      = CAST(:phase AS varchar))
               AND (CAST(:payerRole AS varchar) IS NULL OR s.payer_role = CAST(:payerRole AS varchar))
               AND (CAST(:keyword AS varchar) IS NULL
                    OR s.settlement_no ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR p.title         ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR a.name          ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR cp.company_name ILIKE '%' || CAST(:keyword AS varchar) || '%')
               AND (CAST(:fromDate AS date) IS NULL OR s.created_at >= CAST(:fromDate AS date))
               AND (CAST(:toDateExclusive AS date) IS NULL OR s.created_at < CAST(:toDateExclusive AS date))
            """, nativeQuery = true)
    long countPage(@Param("status") String status,
                   @Param("phase") String phase,
                   @Param("payerRole") String payerRole,
                   @Param("keyword") String keyword,
                   @Param("fromDate") LocalDate fromDate,
                   @Param("toDateExclusive") LocalDate toDateExclusive);

    // ==================================================================
    // 요약 카드
    // ==================================================================

    interface SummaryRow {
        /** 결제 완료된 수수료의 합. 플랫폼이 실제로 받은 돈이다. */
        Long getTotalRevenue();

        /** 이번 달에 결제 완료된 수수료의 합. 기준은 {@code paid_at} 이다. */
        Long getMonthlyRevenue();

        Long getPendingAmount();

        long getPendingCount();

        Long getOverdueAmount();

        long getOverdueCount();

        Long getFailedAmount();

        long getFailedCount();
    }

    /**
     * 목록 상단 요약 카드.
     *
     * <p>한 번의 스캔으로 여덟 값을 낸다. 카드마다 쿼리를 날리면 화면 하나에 여러 번 왕복한다.
     *
     * <p><b>"이번 달" 의 경계를 파라미터로 받는다.</b> SQL 의 {@code date_trunc('month', now())}
     * 를 쓰면 DB 서버의 타임존을 따르게 되는데, 애플리케이션은 KST 로 고정되어 있어
     * 매월 1일 새벽에 두 기준이 갈린다. 경계를 서비스에서 계산해 넘기면 그런 일이 없다.
     *
     * <p>합계는 대상이 없으면 null 이 되므로 {@code COALESCE} 로 0 을 만든다. 화면에서
     * "-" 와 "0원" 은 다른 뜻이라, 집계 결과가 비었다는 이유로 null 을 내보내지 않는다.
     *
     * <p>취소된 정산({@code CANCELED})은 어느 카드에도 들어가지 않는다. 낼 이유가 사라진
     * 건이라 받을 돈도 못 받은 돈도 아니다.
     */
    @Query(value = """
            SELECT COALESCE(SUM(s.fee_amount) FILTER (WHERE s.status = 'PAID'), 0)      AS totalRevenue,
                   COALESCE(SUM(s.fee_amount) FILTER (WHERE s.status = 'PAID'
                                                        AND s.paid_at >= CAST(:monthStart AS date)
                                                        AND s.paid_at <  CAST(:nextMonthStart AS date)), 0)
                                                                                        AS monthlyRevenue,
                   COALESCE(SUM(s.fee_amount) FILTER (WHERE s.status = 'PENDING'), 0)   AS pendingAmount,
                   COUNT(*)                   FILTER (WHERE s.status = 'PENDING')       AS pendingCount,
                   COALESCE(SUM(s.fee_amount) FILTER (WHERE s.status = 'OVERDUE'), 0)   AS overdueAmount,
                   COUNT(*)                   FILTER (WHERE s.status = 'OVERDUE')       AS overdueCount,
                   COALESCE(SUM(s.fee_amount) FILTER (WHERE s.status = 'FAILED'), 0)    AS failedAmount,
                   COUNT(*)                   FILTER (WHERE s.status = 'FAILED')        AS failedCount
              FROM settlement s
            """, nativeQuery = true)
    SummaryRow findSummary(@Param("monthStart") LocalDate monthStart,
                           @Param("nextMonthStart") LocalDate nextMonthStart);

    interface PenaltyRow {
        Long getPaidAmount();

        long getPaidCount();

        Long getPendingAmount();

        long getPendingCount();
    }

    /**
     * 요약 카드의 "위약금 수수료".
     *
     * <p>정산과 <b>다른 테이블</b>({@code penalty})에서 온다. 중도 파기 위약금은 수수료가 아니라
     * 당사자 사이의 배상이라 {@code settlement} 에 섞이지 않는다. 그래서 요약 쿼리에도 합치지 않고
     * 따로 읽는다 — 조인해서 한 번에 세면 정산 건수가 위약금 건수만큼 부풀어 다른 카드까지 틀어진다.
     *
     * <p><b>현재는 항상 0 이다.</b> 백엔드에 Penalty 도메인이 없어 이 테이블에 쓰는 코드가 아직
     * 없다({@code SettlementController} 의 위약금 엔드포인트도 고정 응답이다). 테이블은
     * {@code db/init/02-create-schema.sql} 로 만들어져 있으므로 쿼리는 정상 동작하고,
     * 백엔드가 위약금을 저장하기 시작하면 이 카드가 저절로 채워진다.
     */
    @Query(value = """
            SELECT COALESCE(SUM(pn.penalty_amount) FILTER (WHERE pn.status = 'PAID'), 0)    AS paidAmount,
                   COUNT(*)                        FILTER (WHERE pn.status = 'PAID')        AS paidCount,
                   COALESCE(SUM(pn.penalty_amount) FILTER (WHERE pn.status = 'PENDING'), 0) AS pendingAmount,
                   COUNT(*)                        FILTER (WHERE pn.status = 'PENDING')     AS pendingCount
              FROM penalty pn
            """, nativeQuery = true)
    PenaltyRow findPenaltySummary();

    // ==================================================================
    // 상세
    // ==================================================================

    /** 정산 상세. 목록 값에 실패 사유·승인번호·계약 정보를 더한다. */
    interface DetailRow {
        Long getSettlementId();

        String getSettlementNo();

        Long getProjectId();

        String getProjectTitle();

        String getProjectStatus();

        Long getContractId();

        String getContractNo();

        Long getPayerAccountId();

        String getPayerName();

        String getPayerEmail();

        String getPayerPhone();

        String getCompanyName();

        String getPayerRole();

        String getPhase();

        Long getBaseAmount();

        BigDecimal getFeeRate();

        BigDecimal getGradeDiscount();

        BigDecimal getEffectiveFeeRate();

        Long getFeeAmount();

        String getStatus();

        LocalDate getDueDate();

        LocalDateTime getPaidAt();

        /** 결제 승인번호. 결제 완료 건만 값이 있다. */
        String getApprovalNo();

        String getFailReason();

        String getOverdueReason();

        /** 결제수단 ID. 수단의 상세(카드사·마스킹 번호)는 회원 소유라 여기서 펼치지 않는다. */
        Long getPaymentMethodId();

        LocalDateTime getCreatedAt();
    }

    @Query(value = """
            SELECT s.id               AS settlementId,
                   s.settlement_no    AS settlementNo,
                   s.project_id       AS projectId,
                   p.title            AS projectTitle,
                   p.status           AS projectStatus,
                   s.contract_id      AS contractId,
                   c.contract_no      AS contractNo,
                   s.payer_account_id AS payerAccountId,
                   a.name             AS payerName,
                   a.email            AS payerEmail,
                   a.phone            AS payerPhone,
                   cp.company_name    AS companyName,
                   s.payer_role       AS payerRole,
                   s.phase            AS phase,
                   s.base_amount      AS baseAmount,
                   s.fee_rate         AS feeRate,
                   s.grade_discount   AS gradeDiscount,
                   GREATEST(s.fee_rate - s.grade_discount, 0) AS effectiveFeeRate,
                   s.fee_amount       AS feeAmount,
                   s.status           AS status,
                   s.due_date         AS dueDate,
                   s.paid_at          AS paidAt,
                   s.approval_no      AS approvalNo,
                   s.fail_reason      AS failReason,
                   s.overdue_reason   AS overdueReason,
                   s.payment_method_id AS paymentMethodId,
                   s.created_at       AS createdAt
              FROM settlement s
              LEFT JOIN project p         ON p.id = s.project_id
              LEFT JOIN contract c        ON c.id = s.contract_id
              LEFT JOIN account a         ON a.id = s.payer_account_id
              LEFT JOIN client_profile cp ON cp.account_id = a.id AND cp.deleted_at IS NULL
             WHERE s.id = :settlementId
            """, nativeQuery = true)
    Optional<DetailRow> findDetail(@Param("settlementId") Long settlementId);
}
