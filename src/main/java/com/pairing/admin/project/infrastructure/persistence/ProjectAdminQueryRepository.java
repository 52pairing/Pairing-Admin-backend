package com.pairing.admin.project.infrastructure.persistence;

import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 프로젝트 관리 화면의 조회 전용 저장소. (요구사항 R38)
 *
 * <p>목록 한 줄에 필요한 값이 {@code project} 한 테이블에 다 있지 않다. 클라이언트명은
 * {@code client_profile}, 직군·직무는 {@code project_position}, 계약금액은 {@code contract}
 * 에 있다. 그래서 <b>엔티티를 늘리지 않고 네이티브 쿼리 + 인터페이스 프로젝션</b>으로 필요한
 * 컬럼만 읽는다. ({@code MemberAdminQueryRepository}, {@code NegotiationAdminRepository} 와 같은 방침)
 *
 * <p>쓰기 메서드를 열지 않으려고 {@code JpaRepository} 가 아니라 {@link Repository} 를 상속한다.
 * 프로젝트 상태는 클라이언트의 행동과 매칭·계약 흐름으로만 바뀐다.
 *
 * <p><b>{@code deleted_at IS NULL} 로 거른다.</b> 회원과 달리 프로젝트의 삭제는 "취소" 와 다른
 * 사건이다. 등록 취소는 {@code status = 'CANCELED'} 로 목록에 남고(피그마의 "취소됨" 탭),
 * {@code deleted_at} 은 보존기간이 끝나 지운 데이터라 화면에 있을 이유가 없다.
 *
 * <p>정렬은 <b>최근 등록순으로 고정</b>이다. 네이티브 쿼리에 정렬을 문자열로 이어 붙이면
 * SQL 주입 경로가 된다.
 *
 * <p><b>날짜 파라미터에 {@code CAST(... AS date)} 를 붙인 이유.</b> 조건을 비우면 null 이
 * 그대로 바인딩되는데, PostgreSQL 은 타입을 알 수 없는 null 파라미터를 만나면
 * {@code could not determine data type of parameter} 로 거절한다. 캐스트가 그 타입을 알려 준다.
 *
 * <p><b>도메인 타입이 {@link AccountJpaEntity} 인 것은 자리 표시자다.</b> Spring Data 의
 * {@code Repository<T, ID>} 가 관리되는 엔티티를 요구하는데, 이 저장소는 네이티브 쿼리만
 * 선언하므로 그 타입을 실제로 쓰지 않는다. {@code project} 용 엔티티를 새로 만들면 안 된다 —
 * 테스트는 {@code ddl-auto=create-drop} 이라 Hibernate 가 그 엔티티로 {@code project} 테이블을
 * 먼저 만들어 버리고, 그러면 {@code shared-tables.sql} 의
 * {@code CREATE TABLE IF NOT EXISTS project} 가 통째로 건너뛰어져 회원 관리 쪽 쿼리가 깨진다.
 */
public interface ProjectAdminQueryRepository extends Repository<AccountJpaEntity, Long> {

    // ==================================================================
    // 목록
    // ==================================================================

    /** 프로젝트 목록 한 줄. 피그마의 표 컬럼과 1:1 로 대응한다. */
    interface ListRow {
        Long getProjectId();

        String getTitle();

        /** {@code client_profile.account_id}. 회원 관리 화면으로 넘어가는 링크에 쓴다. */
        Long getClientAccountId();

        /**
         * 클라이언트 <b>회사명</b>({@code client_profile.company_name}).
         *
         * <p>같은 계정의 {@code account.name} 은 담당자명이라 다른 값이다. 화면의 "클라이언트" 는
         * 발주한 회사를 가리키므로 회사명이 맞다.
         */
        String getClientName();

        String getStatus();

        String getPaymentStatus();

        /** 대표 포지션(position_no 가 가장 작은 것)의 직군. 포지션이 없으면 null */
        String getJobCategory();

        /** 대표 포지션의 직무 */
        String getJobRole();

        /** 포지션 개수. 2 이상이면 화면에서 "외 N건" 으로 덧붙인다. */
        long getPositionCount();

        /**
         * 계약 <b>월 단가</b>({@code contract.salary_amount}). 계약 전이면 null.
         *
         * <p>모집 인원이 여럿이면 계약도 여럿이라 <b>가장 먼저 체결된 계약</b>의 값을 대표로 쓴다.
         */
        Long getContractSalaryAmount();

        /**
         * 등록 시 적은 예산({@code project.budget_amount}). <b>총액</b>이며 월 단가가 아니다.
         *
         * <p>계약 전 프로젝트에는 계약금액이 없으므로 화면은 이 값으로 대체한다.
         * 단위가 다르니 "월" 을 붙이면 안 된다.
         */
        Long getBudgetAmount();

        Integer getPeriodValue();

        String getPeriodUnit();

        Integer getTotalHeadcount();

        Integer getConfirmedHeadcount();

        LocalDateTime getCreatedAt();
    }

    /**
     * 프로젝트 목록.
     *
     * <p>조건은 null 이면 건너뛴다.
     * <ul>
     *   <li>{@code status} — 피그마 탭 그대로다. null 이 "전체".</li>
     *   <li>{@code keyword} — 프로젝트명과 클라이언트 회사명을 함께 본다(대소문자 무시).</li>
     *   <li>{@code fromDate} / {@code toDateExclusive} — 등록일 기준. 위쪽 경계는 <b>포함하지 않는</b>
     *       날짜다. 서비스가 "종료일 + 1일" 을 계산해 넘긴다. {@code <= 종료일} 로 쓰면
     *       그날 23:59 에 등록된 프로젝트를 놓치고, 날짜 덧셈을 SQL 에 두면 DB 마다 문법이 갈린다.</li>
     * </ul>
     *
     * <p>대표 포지션과 대표 계약은 상관 서브쿼리로 하나씩만 집는다. 조인으로 붙이면 포지션이
     * 3개인 프로젝트가 목록에 3줄로 나오고, {@code DISTINCT} 로 지우면 페이징 건수가 어긋난다.
     */
    @Query(value = """
            SELECT p.id                 AS projectId,
                   p.title              AS title,
                   cp.account_id        AS clientAccountId,
                   cp.company_name      AS clientName,
                   p.status             AS status,
                   p.payment_status     AS paymentStatus,
                   (SELECT pp.job_category FROM project_position pp
                     WHERE pp.project_id = p.id
                     ORDER BY pp.position_no, pp.id
                     LIMIT 1)           AS jobCategory,
                   (SELECT pp.job_role FROM project_position pp
                     WHERE pp.project_id = p.id
                     ORDER BY pp.position_no, pp.id
                     LIMIT 1)           AS jobRole,
                   (SELECT COUNT(*) FROM project_position pp
                     WHERE pp.project_id = p.id)
                                        AS positionCount,
                   (SELECT c.salary_amount FROM contract c
                     WHERE c.project_id = p.id
                       AND c.status <> 'REJECTED'
                     ORDER BY c.created_at, c.id
                     LIMIT 1)           AS contractSalaryAmount,
                   p.budget_amount      AS budgetAmount,
                   p.period_value       AS periodValue,
                   p.period_unit        AS periodUnit,
                   p.total_headcount    AS totalHeadcount,
                   p.confirmed_headcount AS confirmedHeadcount,
                   p.created_at         AS createdAt
              FROM project p
              LEFT JOIN client_profile cp ON cp.id = p.client_id
             WHERE p.deleted_at IS NULL
               AND (CAST(:status AS varchar) IS NULL OR p.status = CAST(:status AS varchar))
               AND (CAST(:keyword AS varchar) IS NULL
                    OR p.title         ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR cp.company_name ILIKE '%' || CAST(:keyword AS varchar) || '%')
               AND (CAST(:fromDate AS date) IS NULL OR p.created_at >= CAST(:fromDate AS date))
               AND (CAST(:toDateExclusive AS date) IS NULL OR p.created_at < CAST(:toDateExclusive AS date))
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ListRow> findPage(@Param("status") String status,
                           @Param("keyword") String keyword,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDateExclusive") LocalDate toDateExclusive,
                           @Param("size") int size,
                           @Param("offset") long offset);

    /** 목록 전체 건수. 페이지 계산용이라 검색 조건에 필요한 조인만 남긴다. */
    @Query(value = """
            SELECT COUNT(*)
              FROM project p
              LEFT JOIN client_profile cp ON cp.id = p.client_id
             WHERE p.deleted_at IS NULL
               AND (CAST(:status AS varchar) IS NULL OR p.status = CAST(:status AS varchar))
               AND (CAST(:keyword AS varchar) IS NULL
                    OR p.title         ILIKE '%' || CAST(:keyword AS varchar) || '%'
                    OR cp.company_name ILIKE '%' || CAST(:keyword AS varchar) || '%')
               AND (CAST(:fromDate AS date) IS NULL OR p.created_at >= CAST(:fromDate AS date))
               AND (CAST(:toDateExclusive AS date) IS NULL OR p.created_at < CAST(:toDateExclusive AS date))
            """, nativeQuery = true)
    long countPage(@Param("status") String status,
                   @Param("keyword") String keyword,
                   @Param("fromDate") LocalDate fromDate,
                   @Param("toDateExclusive") LocalDate toDateExclusive);

    // ==================================================================
    // 상단 탭 카운트
    // ==================================================================

    interface StatusCountRow {
        long getTotal();

        long getRegistered();

        long getRecruiting();

        long getNegotiating();

        long getContractPending();

        long getInProgress();

        long getCompletionPending();

        long getClosed();

        long getCanceled();
    }

    /**
     * 피그마 상단 탭의 괄호 숫자 9개.
     *
     * <p>한 번의 스캔으로 아홉 값을 모두 낸다. 탭마다 COUNT 를 날리면 화면 하나에 9번 왕복한다.
     *
     * <p><b>검색 조건과 무관하게 전체를 센다.</b> 탭은 "지금 무엇이 몇 건인지" 를 보여 주는
     * 고정 지표라, 키워드를 입력할 때마다 숫자가 흔들리면 기준으로 쓸 수 없다.
     *
     * <p>{@code CANCELED} 를 뺀 나머지 여덟의 합이 {@code total} 과 같아야 정상이다.
     * 어긋난다면 백엔드에 이 enum 밖의 상태가 새로 생겼다는 뜻이다.
     */
    @Query(value = """
            SELECT COUNT(*)                                                       AS total,
                   COUNT(*) FILTER (WHERE p.status = 'REGISTERED')                AS registered,
                   COUNT(*) FILTER (WHERE p.status = 'RECRUITING')                AS recruiting,
                   COUNT(*) FILTER (WHERE p.status = 'NEGOTIATING')               AS negotiating,
                   COUNT(*) FILTER (WHERE p.status = 'CONTRACT_PENDING')          AS contractPending,
                   COUNT(*) FILTER (WHERE p.status = 'IN_PROGRESS')               AS inProgress,
                   COUNT(*) FILTER (WHERE p.status = 'COMPLETION_PENDING')        AS completionPending,
                   COUNT(*) FILTER (WHERE p.status = 'CLOSED')                    AS closed,
                   COUNT(*) FILTER (WHERE p.status = 'CANCELED')                  AS canceled
              FROM project p
             WHERE p.deleted_at IS NULL
            """, nativeQuery = true)
    StatusCountRow findStatusCounts();

    // ==================================================================
    // 상세 — 기본 정보
    // ==================================================================

    /** 상세 화면 "프로젝트 기본 정보" 카드. */
    interface DetailRow {
        Long getProjectId();

        String getTitle();

        Long getClientAccountId();

        String getClientName();

        /** 클라이언트 담당자명({@code account.name}). 회사명과 다른 값이다. */
        String getClientManagerName();

        String getClientEmail();

        String getClientPhone();

        String getStatus();

        String getPaymentStatus();

        Long getBudgetAmount();

        Integer getPeriodValue();

        String getPeriodUnit();

        String getWorkStyle();

        String getWorkForm();

        String getWorkLocation();

        LocalDate getStartDesiredDate();

        Boolean getStartNegotiable();

        Integer getTotalHeadcount();

        Integer getConfirmedHeadcount();

        String getCurrentSituation();

        String getMainTask();

        String getDetailScope();

        String getExtraNote();

        LocalDateTime getRecruitStartedAt();

        LocalDateTime getRecruitDeadline();

        Integer getExtensionCount();

        LocalDateTime getNoticeAgreedAt();

        LocalDateTime getCanceledAt();

        LocalDateTime getClosedAt();

        LocalDateTime getCreatedAt();
    }

    /**
     * 상세 기본 정보.
     *
     * <p>클라이언트는 {@code project.client_id → client_profile.id → account.id} 로 두 번 거친다.
     * {@code project.client_id} 는 {@code account.id} 가 아니다 — 백엔드
     * {@code ProjectJpaEntity} 주석에도 같은 경고가 있다.
     *
     * <p>{@code deleted_at IS NULL} 을 목록과 <b>똑같이</b> 건다. 보존기간이 끝나 지운
     * 프로젝트는 상세도 없어야 하고, 결과가 비면 서비스가 404 를 낸다. 등록 취소
     * ({@code status = 'CANCELED'})는 지워진 것이 아니므로 여기서 걸리지 않는다.
     */
    @Query(value = """
            SELECT p.id                  AS projectId,
                   p.title               AS title,
                   cp.account_id         AS clientAccountId,
                   cp.company_name       AS clientName,
                   ca.name               AS clientManagerName,
                   ca.email              AS clientEmail,
                   ca.phone              AS clientPhone,
                   p.status              AS status,
                   p.payment_status      AS paymentStatus,
                   p.budget_amount       AS budgetAmount,
                   p.period_value        AS periodValue,
                   p.period_unit         AS periodUnit,
                   p.work_style          AS workStyle,
                   p.work_form           AS workForm,
                   p.work_location       AS workLocation,
                   p.start_desired_date  AS startDesiredDate,
                   p.start_negotiable    AS startNegotiable,
                   p.total_headcount     AS totalHeadcount,
                   p.confirmed_headcount AS confirmedHeadcount,
                   p.current_situation   AS currentSituation,
                   p.main_task           AS mainTask,
                   p.detail_scope        AS detailScope,
                   p.extra_note          AS extraNote,
                   p.recruit_started_at  AS recruitStartedAt,
                   p.recruit_deadline    AS recruitDeadline,
                   p.extension_count     AS extensionCount,
                   p.notice_agreed_at    AS noticeAgreedAt,
                   p.canceled_at         AS canceledAt,
                   p.closed_at           AS closedAt,
                   p.created_at          AS createdAt
              FROM project p
              LEFT JOIN client_profile cp ON cp.id = p.client_id
              LEFT JOIN account ca        ON ca.id = cp.account_id
             WHERE p.id = :projectId
               AND p.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<DetailRow> findDetail(@Param("projectId") Long projectId);

    // ==================================================================
    // 상세 — 모집 포지션
    // ==================================================================

    /**
     * 상세 화면의 직군·직무.
     *
     * <p>피그마는 직군·직무를 한 줄로 그리지만 <b>프로젝트는 포지션을 여러 개 가질 수 있다.</b>
     * 목록은 대표 하나만 쓰고, 상세는 전부 내려 준다. 화면이 첫 줄만 그리더라도
     * 두 번째 포지션이 있다는 사실 자체는 관리자가 알 수 있어야 한다.
     */
    interface PositionRow {
        Long getPositionId();

        Integer getPositionNo();

        String getJobCategory();

        String getJobRole();

        Integer getMinCareerYears();

        Integer getHeadcount();

        Integer getConfirmedCount();

        String getStatus();

        String getPreferredNote();

        /** 요구 기술 스택. {@code position_skill.skill_code} 를 콤마로 이어 붙인 값 */
        String getSkillCodes();
    }

    /**
     * 포지션 목록.
     *
     * <p>기술 스택은 포지션당 여러 건이라 조인하면 포지션이 스택 수만큼 늘어난다.
     * {@code string_agg} 로 한 칸에 모아 서비스에서 나눈다. 스택이 없으면 null 이다.
     */
    @Query(value = """
            SELECT pp.id               AS positionId,
                   pp.position_no      AS positionNo,
                   pp.job_category     AS jobCategory,
                   pp.job_role         AS jobRole,
                   pp.min_career_years AS minCareerYears,
                   pp.headcount        AS headcount,
                   pp.confirmed_count  AS confirmedCount,
                   pp.status           AS status,
                   pp.preferred_note   AS preferredNote,
                   (SELECT string_agg(ps.skill_code, ',' ORDER BY ps.skill_code)
                      FROM position_skill ps
                     WHERE ps.position_id = pp.id) AS skillCodes
              FROM project_position pp
             WHERE pp.project_id = :projectId
             ORDER BY pp.position_no, pp.id
            """, nativeQuery = true)
    List<PositionRow> findPositions(@Param("projectId") Long projectId);

    // ==================================================================
    // 상세 — 계약 정보 / 매칭 프리랜서
    // ==================================================================

    /**
     * 상세 화면의 "계약 정보" 카드와 "매칭 프리랜서".
     *
     * <p>둘을 한 쿼리로 낸다. 매칭된 프리랜서는 <b>계약을 맺은 사람</b>이라 출처가 같기 때문이다.
     * 협상만 하다 만 후보는 매칭 프리랜서가 아니다.
     */
    interface ContractRow {
        Long getContractId();

        String getContractNo();

        Long getPositionId();

        Long getFreelancerAccountId();

        /** 프리랜서 이름({@code account.name}). 개인이라 프로필에 이름이 없어 계정에서 가져온다. */
        String getFreelancerName();

        String getFreelancerEmail();

        /** 월 단가. 협상에서 합의된 금액이 그대로 넘어온다. */
        Long getSalaryAmount();

        /** 총 계약금액 = 월 단가 × 계약 개월 수 */
        Long getTotalAmount();

        LocalDate getStartDate();

        LocalDate getEndDate();

        String getStatus();

        String getWorkStyle();

        String getWorkForm();

        LocalDateTime getSignedAt();

        LocalDateTime getCompletedAt();

        LocalDateTime getTerminatedAt();

        String getTerminatedBy();

        LocalDateTime getCreatedAt();
    }

    /**
     * 계약 목록. <b>서명 거부(REJECTED)된 계약도 내려 준다.</b>
     *
     * <p>목록의 대표 계약금액은 거부된 건을 빼지만, 상세는 이력이라 남긴다. 관리자가
     * "왜 계약이 안 됐는지" 를 보는 화면에서 거부 건이 사라지면 확인할 방법이 없다.
     * 화면이 첫 건만 그리도록 체결 시각 · 생성 시각 순으로 정렬한다.
     */
    @Query(value = """
            SELECT c.id            AS contractId,
                   c.contract_no   AS contractNo,
                   c.position_id   AS positionId,
                   fp.account_id   AS freelancerAccountId,
                   fa.name         AS freelancerName,
                   fa.email        AS freelancerEmail,
                   c.salary_amount AS salaryAmount,
                   c.total_amount  AS totalAmount,
                   c.start_date    AS startDate,
                   c.end_date      AS endDate,
                   c.status        AS status,
                   c.work_style    AS workStyle,
                   c.work_form     AS workForm,
                   c.signed_at     AS signedAt,
                   c.completed_at  AS completedAt,
                   c.terminated_at AS terminatedAt,
                   c.terminated_by AS terminatedBy,
                   c.created_at    AS createdAt
              FROM contract c
              LEFT JOIN freelancer_profile fp ON fp.id = c.freelancer_id
              LEFT JOIN account fa            ON fa.id = fp.account_id
             WHERE c.project_id = :projectId
             ORDER BY c.created_at, c.id
            """, nativeQuery = true)
    List<ContractRow> findContracts(@Param("projectId") Long projectId);
}
