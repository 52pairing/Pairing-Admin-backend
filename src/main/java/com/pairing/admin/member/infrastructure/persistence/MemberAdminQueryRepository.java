package com.pairing.admin.member.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 회원 관리 화면의 조회 전용 저장소.
 *
 * <p>회원 한 줄에 필요한 값이 {@code account} 한 테이블에 다 있지 않다. 기업명은
 * {@code client_profile}, 가입 공급자는 {@code social_account}, 진행 프로젝트 건수는
 * {@code project}/{@code contract} 에 흩어져 있다. 그래서 <b>엔티티를 늘리지 않고
 * 네이티브 쿼리 + 인터페이스 프로젝션</b>으로 필요한 컬럼만 읽는다.
 * ({@code NegotiationAdminRepository}, {@code shared-tables.sql} 과 같은 방침이다 —
 * 백엔드 소유 테이블을 이 서버에 또 매핑하면 컬럼이 바뀔 때 양쪽이 함께 깨진다.)
 *
 * <p>쓰기 메서드를 열지 않으려고 {@code JpaRepository} 가 아니라 {@link Repository} 를 상속한다.
 * 회원 정보를 고치는 경로는 {@link AccountJpaRepository} 의 엔티티 한 곳뿐이어야 한다.
 *
 * <p><b>탈퇴 회원도 조회 대상이다.</b> 백엔드의 탈퇴({@code Account.withdraw})는
 * {@code status=WITHDRAWN} 과 함께 {@code deleted_at} 을 채운다. 여기서 {@code deleted_at IS NULL}
 * 로 걸러 버리면 화면의 "탈퇴" 필터와 요약 카드가 영원히 0 이 된다.
 *
 * <p>정렬은 <b>최근 가입순으로 고정</b>이다. 네이티브 쿼리에 정렬을 문자열로 이어 붙이면
 * SQL 주입 경로가 된다.
 */
public interface MemberAdminQueryRepository extends Repository<AccountJpaEntity, Long> {

    // ==================================================================
    // 목록
    // ==================================================================

    /** 회원 목록 한 줄. 피그마의 표 컬럼과 1:1 로 대응한다. */
    interface ListRow {
        Long getAccountId();

        String getName();

        /** 클라이언트만 값이 있다. 표에서 이름 위에 기업명으로 보여 준다. */
        String getCompanyName();

        String getEmail();

        String getPhone();

        String getRole();

        String getStatus();

        Boolean getSuspended();

        String getSignupType();

        /** {@code social_account.provider}. 이메일 가입이거나 연동이 없으면 null */
        String getProvider();

        LocalDateTime getCreatedAt();

        LocalDateTime getLastLoginAt();

        /** 종료·취소되지 않은 프로젝트 건수 */
        Long getActiveProjectCount();
    }

    /**
     * 회원 목록.
     *
     * <p>조건은 null 이면 건너뛴다.
     * <ul>
     *   <li>{@code status} — {@code SUSPENDED} 는 {@code suspended_at} 으로, 나머지는
     *       {@code account.status} 로 판정한다. 정지 회원의 status 는 대개 ACTIVE 그대로라,
     *       "정상" 을 고르면 정지된 회원이 섞이지 않도록 함께 걸러야 한다.</li>
     *   <li>{@code signupMethod} — {@code EMAIL}, 공급자 이름({@code KAKAO}/{@code GOOGLE}),
     *       또는 공급자를 가리지 않는 {@code SOCIAL}.</li>
     * </ul>
     *
     * <p>진행 프로젝트 건수는 역할마다 경로가 다르다. 클라이언트는 자기가 등록한 프로젝트를 세고,
     * 프리랜서는 계약으로 참여한 프로젝트를 센다. {@code project.client_id} 가
     * {@code account.id} 가 아니라 <b>{@code client_profile.id}</b> 를 가리키므로 프로필을 한 번 거친다.
     */
    @Query(value = """
            SELECT a.id                AS accountId,
                   a.name              AS name,
                   cp.company_name     AS companyName,
                   a.email             AS email,
                   a.phone             AS phone,
                   a.role              AS role,
                   a.status            AS status,
                   (a.suspended_at IS NOT NULL) AS suspended,
                   a.signup_type       AS signupType,
                   (SELECT sa.provider FROM social_account sa
                     WHERE sa.account_id = a.id
                     ORDER BY sa.connected_at, sa.id
                     LIMIT 1)          AS provider,
                   a.created_at        AS createdAt,
                   a.last_login_at     AS lastLoginAt,
                   CASE WHEN a.role = 'CLIENT' THEN
                            (SELECT COUNT(*) FROM project p
                               JOIN client_profile pcp ON pcp.id = p.client_id
                              WHERE pcp.account_id = a.id
                                AND p.deleted_at IS NULL
                                AND p.status NOT IN ('CLOSED', 'CANCELED'))
                        ELSE
                            (SELECT COUNT(DISTINCT c.project_id) FROM contract c
                               JOIN freelancer_profile pfp ON pfp.id = c.freelancer_id
                               JOIN project p ON p.id = c.project_id
                              WHERE pfp.account_id = a.id
                                AND p.deleted_at IS NULL
                                AND p.status NOT IN ('CLOSED', 'CANCELED'))
                        END            AS activeProjectCount
              FROM account a
              LEFT JOIN client_profile cp ON cp.account_id = a.id AND cp.deleted_at IS NULL
             WHERE a.role <> 'ADMIN'
               AND (:role IS NULL OR a.role = :role)
               AND (:status IS NULL
                    OR (:status = 'SUSPENDED' AND a.suspended_at IS NOT NULL)
                    OR (:status <> 'SUSPENDED' AND a.suspended_at IS NULL AND a.status = :status))
               AND (:signupMethod IS NULL
                    OR (:signupMethod = 'EMAIL' AND a.signup_type = 'EMAIL')
                    OR (:signupMethod = 'SOCIAL' AND a.signup_type = 'SOCIAL')
                    OR (:signupMethod NOT IN ('EMAIL', 'SOCIAL') AND a.signup_type = 'SOCIAL'
                        AND EXISTS (SELECT 1 FROM social_account sa2
                                     WHERE sa2.account_id = a.id AND sa2.provider = :signupMethod)))
               AND (:keyword IS NULL
                    OR a.name          ILIKE '%' || :keyword || '%'
                    OR a.email         ILIKE '%' || :keyword || '%'
                    OR a.phone         LIKE  '%' || :keyword || '%'
                    OR cp.company_name ILIKE '%' || :keyword || '%')
             ORDER BY a.created_at DESC, a.id DESC
             LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ListRow> findPage(@Param("role") String role,
                           @Param("status") String status,
                           @Param("signupMethod") String signupMethod,
                           @Param("keyword") String keyword,
                           @Param("size") int size,
                           @Param("offset") long offset);

    /** 목록 전체 건수. 페이지 계산용이라 검색 조건에 필요한 조인만 남긴다. */
    @Query(value = """
            SELECT COUNT(*)
              FROM account a
              LEFT JOIN client_profile cp ON cp.account_id = a.id AND cp.deleted_at IS NULL
             WHERE a.role <> 'ADMIN'
               AND (:role IS NULL OR a.role = :role)
               AND (:status IS NULL
                    OR (:status = 'SUSPENDED' AND a.suspended_at IS NOT NULL)
                    OR (:status <> 'SUSPENDED' AND a.suspended_at IS NULL AND a.status = :status))
               AND (:signupMethod IS NULL
                    OR (:signupMethod = 'EMAIL' AND a.signup_type = 'EMAIL')
                    OR (:signupMethod = 'SOCIAL' AND a.signup_type = 'SOCIAL')
                    OR (:signupMethod NOT IN ('EMAIL', 'SOCIAL') AND a.signup_type = 'SOCIAL'
                        AND EXISTS (SELECT 1 FROM social_account sa2
                                     WHERE sa2.account_id = a.id AND sa2.provider = :signupMethod)))
               AND (:keyword IS NULL
                    OR a.name          ILIKE '%' || :keyword || '%'
                    OR a.email         ILIKE '%' || :keyword || '%'
                    OR a.phone         LIKE  '%' || :keyword || '%'
                    OR cp.company_name ILIKE '%' || :keyword || '%')
            """, nativeQuery = true)
    long countPage(@Param("role") String role,
                   @Param("status") String status,
                   @Param("signupMethod") String signupMethod,
                   @Param("keyword") String keyword);

    // ==================================================================
    // 요약 카드
    // ==================================================================

    interface SummaryRow {
        long getTotal();

        long getActive();

        long getSuspended();

        long getWithdrawn();

        long getClients();

        long getFreelancers();
    }

    /**
     * 목록 화면 상단 카드 6개.
     *
     * <p>한 번의 스캔으로 여섯 값을 모두 낸다. 건수마다 쿼리를 날리면 화면 하나에 6번 왕복한다.
     *
     * <p>"정상" 은 정지되지 않은 ACTIVE 만 센다. 정지 회원은 status 가 ACTIVE 그대로라
     * 빼 주지 않으면 정상과 정지 양쪽에 중복으로 잡힌다. 그래서 여섯 값의 합은 전체와 맞지 않는다
     * — 가입 대기(PENDING)·잠금(LOCKED)이 어느 카드에도 안 들어가고, 역할 카드는 상태와 무관하게 세기 때문이다.
     */
    @Query(value = """
            SELECT COUNT(*)                                                          AS total,
                   COUNT(*) FILTER (WHERE a.suspended_at IS NULL
                                      AND a.status = 'ACTIVE')                       AS active,
                   COUNT(*) FILTER (WHERE a.suspended_at IS NOT NULL)                AS suspended,
                   COUNT(*) FILTER (WHERE a.status = 'WITHDRAWN')                    AS withdrawn,
                   COUNT(*) FILTER (WHERE a.role = 'CLIENT')                         AS clients,
                   COUNT(*) FILTER (WHERE a.role = 'FREELANCER')                     AS freelancers
              FROM account a
             WHERE a.role <> 'ADMIN'
            """, nativeQuery = true)
    SummaryRow findSummary();

    // ==================================================================
    // 상세 — 프로필
    // ==================================================================

    /**
     * 상세 화면의 프로필 정보. 클라이언트와 프리랜서가 서로 다른 테이블에 있어 한 번에 읽는다.
     *
     * <p>역할에 해당하지 않는 쪽은 전부 null 이다. 클라이언트에게 등급·생년월일이 없고,
     * 프리랜서에게 사업자번호가 없는 것이 정상이다.
     */
    interface ProfileRow {
        String getCompanyName();

        String getBusinessNo();

        String getBusinessField();

        String getEmployeeCount();

        String getClientAddress();

        String getClientGrade();

        LocalDate getBirthDate();

        String getFreelancerAddress();

        String getFreelancerGrade();

        Boolean getAiMatchingAgreed();

        Boolean getMatchingPaused();

        String getProvider();

        // ---- freelancer_condition. 프리랜서가 근무 조건을 아직 등록하지 않았으면 전부 null ----

        String getJobCategory();

        String getJobRole();

        String getAffiliation();

        String getWorkStyle();

        String getWorkForm();

        String getPayUnit();

        Long getPayAmount();

        Long getMinAcceptAmount();

        LocalDate getAvailableFrom();

        Boolean getStartNegotiable();

        Integer getPeriodValue();

        String getPeriodUnit();

        Integer getCareerYears();

        Boolean getHasFreelanceExperience();
    }

    /**
     * {@code freelancer_condition} 은 {@code account_id} 로 붙인다.
     *
     * <p>백엔드의 {@code db/init/02-create-schema.sql} 에는 이 테이블의 FK 가
     * {@code freelancer_id → freelancer_profile(id)} 로 적혀 있지만, 실제로 값을 쓰는
     * {@code FreelancerConditionJpaEntity} 는 {@code account_id} 를 매핑한다.
     * ({@code has_freelance_exp} / {@code has_freelance_experience} 도 마찬가지로 어긋나 있다)
     * 돌아가는 애플리케이션이 쓰는 쪽이 엔티티이므로 여기서도 엔티티 기준을 따른다.
     */
    @Query(value = """
            SELECT cp.company_name    AS companyName,
                   cp.business_no     AS businessNo,
                   cp.business_field  AS businessField,
                   cp.employee_count  AS employeeCount,
                   cp.address         AS clientAddress,
                   cp.grade           AS clientGrade,
                   fp.birth_date      AS birthDate,
                   fp.address         AS freelancerAddress,
                   fp.grade           AS freelancerGrade,
                   fp.ai_matching_agreed AS aiMatchingAgreed,
                   fp.matching_paused    AS matchingPaused,
                   (SELECT sa.provider FROM social_account sa
                     WHERE sa.account_id = a.id
                     ORDER BY sa.connected_at, sa.id
                     LIMIT 1)         AS provider,
                   fc.job_category    AS jobCategory,
                   fc.job_role        AS jobRole,
                   fc.affiliation     AS affiliation,
                   fc.work_style      AS workStyle,
                   fc.work_form       AS workForm,
                   fc.pay_unit        AS payUnit,
                   fc.pay_amount      AS payAmount,
                   fc.min_accept_amount AS minAcceptAmount,
                   fc.available_from  AS availableFrom,
                   fc.start_negotiable AS startNegotiable,
                   fc.period_value    AS periodValue,
                   fc.period_unit     AS periodUnit,
                   fc.career_years    AS careerYears,
                   fc.has_freelance_experience AS hasFreelanceExperience
              FROM account a
              LEFT JOIN client_profile cp        ON cp.account_id = a.id AND cp.deleted_at IS NULL
              LEFT JOIN freelancer_profile fp    ON fp.account_id = a.id AND fp.deleted_at IS NULL
              LEFT JOIN freelancer_condition fc  ON fc.account_id = a.id
             WHERE a.id = :accountId
            """, nativeQuery = true)
    Optional<ProfileRow> findProfile(@Param("accountId") Long accountId);

    interface SkillRow {
        String getSkillCode();

        String getSkillLevel();
    }

    /**
     * 프리랜서 기술 스택. 조건 한 건에 여러 개 붙으므로 따로 읽는다.
     *
     * <p>정렬 기준이 따로 없어 코드 순으로 낸다. 화면에 칩으로 늘어놓을 때 순서가 요청마다
     * 달라지면 눈에 거슬리므로, 순서 자체는 고정해 둔다.
     */
    @Query(value = """
            SELECT cs.skill_code  AS skillCode,
                   cs.skill_level AS skillLevel
              FROM condition_skill cs
              JOIN freelancer_condition fc ON fc.id = cs.condition_id
             WHERE fc.account_id = :accountId
             ORDER BY cs.skill_code
            """, nativeQuery = true)
    List<SkillRow> findSkills(@Param("accountId") Long accountId);

    // ==================================================================
    // 상세 — 활동 현황
    // ==================================================================

    interface ActivityRow {
        long getInProgressProjects();

        long getCompletedProjects();

        long getCanceledProjects();
    }

    /**
     * 프로젝트 건수 3종. 역할에 따라 세는 경로가 다르다.
     *
     * <p>기준을 프로젝트 상태 하나로 통일한다.
     * <ul>
     *   <li>진행중 — 종료도 취소도 아닌 것 전부(등록 완료·모집중·협상중·계약 대기·진행중·완료 대기)</li>
     *   <li>완료 — {@code CLOSED}</li>
     *   <li>취소 — {@code CANCELED}</li>
     * </ul>
     * 프리랜서는 계약을 맺은 프로젝트만 센다. 협상만 하다 만 프로젝트는 참여 이력이 아니다.
     * 한 프로젝트에 계약이 여러 건일 수 있어 {@code DISTINCT} 로 센다.
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (WHERE p.status NOT IN ('CLOSED', 'CANCELED')) AS inProgressProjects,
                   COUNT(*) FILTER (WHERE p.status = 'CLOSED')                    AS completedProjects,
                   COUNT(*) FILTER (WHERE p.status = 'CANCELED')                  AS canceledProjects
              FROM project p
             WHERE p.deleted_at IS NULL
               AND p.id IN (
                   SELECT p2.id FROM project p2
                     JOIN client_profile cp ON cp.id = p2.client_id
                    WHERE cp.account_id = :accountId
                   UNION
                   SELECT c.project_id FROM contract c
                     JOIN freelancer_profile fp ON fp.id = c.freelancer_id
                    WHERE fp.account_id = :accountId)
            """, nativeQuery = true)
    ActivityRow findProjectActivity(@Param("accountId") Long accountId);

    /**
     * 누적 거래금액.
     *
     * <p>{@code settlement.base_amount} 의 합이다. 이 컬럼이 수수료를 매기기 전의 거래액이고,
     * {@code fee_amount} 는 플랫폼이 가져가는 수수료라 "거래금액" 이 아니다.
     *
     * <p>결제 완료({@code PAID})만 센다. 대기·미납·실패를 포함하면 실제로 오가지 않은 돈이 섞인다.
     *
     * <p>{@code payer_account_id} 는 {@code account.id} 를 직접 가리켜서 프로필을 거치지 않는다.
     * 대상이 없으면 SUM 이 null 이므로 받는 쪽에서 0 으로 바꾼다.
     */
    @Query(value = """
            SELECT SUM(s.base_amount)
              FROM settlement s
             WHERE s.payer_account_id = :accountId
               AND s.status = 'PAID'
            """, nativeQuery = true)
    BigDecimal sumPaidAmount(@Param("accountId") Long accountId);

    interface ReviewStatsRow {
        long getReviewCount();

        Double getAverageScore();
    }

    /**
     * 받은 리뷰 수와 평균 별점.
     *
     * <p>이 회원이 <b>받은</b> 리뷰({@code reviewee_account_id})만 센다. 쓴 리뷰는 그 사람에 대한
     * 평가가 아니다.
     *
     * <p>컬럼 이름이 백엔드 {@code ReviewJpaEntity} 기준이다({@code reviewee_account_id},
     * {@code score}). 백엔드의 {@code db/init/02-create-schema.sql} 에는 아직 옛 이름
     * ({@code reviewee_id}, {@code rating})이 남아 있는데, 실제 스키마는 엔티티 쪽이 맞다.
     */
    @Query(value = """
            SELECT COUNT(*)     AS reviewCount,
                   AVG(r.score) AS averageScore
              FROM review r
             WHERE r.reviewee_account_id = :accountId
            """, nativeQuery = true)
    ReviewStatsRow findReviewStats(@Param("accountId") Long accountId);
}
