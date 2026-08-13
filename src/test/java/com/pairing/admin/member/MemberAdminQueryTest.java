package com.pairing.admin.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 조회 검증. (관리자 &gt; 회원 관리)
 *
 * <p>회원 한 줄에 필요한 값이 {@code account} 한 테이블에 다 없다는 것이 이 화면의 핵심 난점이다.
 * 기업명은 {@code client_profile}, 가입 공급자는 {@code social_account}, 프로젝트 건수는
 * {@code project}/{@code contract} 에 있다. 조인 경로가 하나라도 틀리면 값이 조용히 null 이나 0 이
 * 되므로, 여기서는 <b>각 값이 실제로 채워지는지</b>를 확인한다.
 *
 * <p>특히 {@code project.client_id} 는 {@code account.id} 가 아니라 {@code client_profile.id} 다.
 * 이걸 헷갈리면 건수가 늘 0 으로 나온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/shared-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("회원 조회")
class MemberAdminQueryTest {

    private static final String USERNAME = "query-admin";
    private static final String PASSWORD = "Admin!2345";

    private static final Long CLIENT_ID = 601L;
    private static final Long FREELANCER_ID = 602L;
    private static final Long SOCIAL_ID = 603L;
    private static final Long WITHDRAWN_ID = 604L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserJpaRepository adminUserJpaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM account");
        for (String table : new String[]{"client_profile", "freelancer_profile", "social_account",
                "project", "contract", "settlement", "review", "condition_skill", "freelancer_condition"}) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        adminUserJpaRepository.deleteAll();

        adminUserJpaRepository.save(AdminUserJpaEntity.create(USERNAME, passwordEncoder.encode(PASSWORD)));
        session = login();

        // 클라이언트: 기업 프로필 + 프로젝트 4건(진행중 2 · 완료 1 · 취소 1)
        insertAccount(CLIENT_ID, "CLIENT", "김담당", "ACTIVE", "EMAIL");
        jdbcTemplate.update("""
                INSERT INTO client_profile (id, account_id, company_name, business_no, business_field,
                                            employee_count, address, grade, deleted_at)
                VALUES (1, ?, '삼성전자', '1248100998', 'IT·정보통신', '100~299명', '서울시 강남구', 'GOLD', NULL)
                """, CLIENT_ID);
        insertProject(11L, 1L, "IN_PROGRESS");
        insertProject(12L, 1L, "RECRUITING");
        insertProject(13L, 1L, "CLOSED");
        insertProject(14L, 1L, "CANCELED");

        // 프리랜서: 계약으로 프로젝트 1건 참여 + 받은 리뷰 2건 + 결제 완료 정산 2건
        insertAccount(FREELANCER_ID, "FREELANCER", "김프리", "ACTIVE", "EMAIL");
        jdbcTemplate.update("""
                INSERT INTO freelancer_profile (id, account_id, birth_date, address, grade,
                                                ai_matching_agreed, matching_paused, deleted_at)
                VALUES (2, ?, DATE '1995-03-02', '서울시 마포구', 'SENIOR', TRUE, FALSE, NULL)
                """, FREELANCER_ID);
        // 근무 조건은 account_id 로 붙는다. freelancer_profile.id 가 아니다.
        jdbcTemplate.update("""
                INSERT INTO freelancer_condition (id, account_id, job_category, job_role, affiliation,
                                                  work_style, work_form, pay_unit, pay_amount,
                                                  min_accept_amount, available_from, start_negotiable,
                                                  period_value, period_unit, career_years,
                                                  has_freelance_experience)
                VALUES (3, ?, 'DEVELOPMENT', 'BACKEND', '무소속',
                        'REMOTE', 'FULL_TIME', 'MONTHLY', 5000000,
                        4000000, DATE '2026-09-01', TRUE,
                        6, 'MONTH', 5, TRUE)
                """, FREELANCER_ID);
        jdbcTemplate.update(
                "INSERT INTO condition_skill (id, condition_id, skill_code, skill_level) VALUES (61, 3, 'SPRING_BOOT', 'ADVANCED')");
        jdbcTemplate.update(
                "INSERT INTO condition_skill (id, condition_id, skill_code, skill_level) VALUES (62, 3, 'DOTNET', 'BEGINNER')");
        jdbcTemplate.update("INSERT INTO contract (id, project_id, freelancer_id) VALUES (21, 11, 2)");
        jdbcTemplate.update("INSERT INTO review (id, reviewee_account_id, score) VALUES (31, ?, 5)", FREELANCER_ID);
        jdbcTemplate.update("INSERT INTO review (id, reviewee_account_id, score) VALUES (32, ?, 4)", FREELANCER_ID);
        insertSettlement(41L, FREELANCER_ID, 1_000_000L, "PAID");
        insertSettlement(42L, FREELANCER_ID, 500_000L, "PAID");
        // 미납은 실제로 오간 돈이 아니다. 누적 거래금액에 섞이면 안 된다.
        insertSettlement(43L, FREELANCER_ID, 900_000L, "OVERDUE");

        // 소셜 가입 프리랜서 (카카오)
        insertAccount(SOCIAL_ID, "FREELANCER", "박소셜", "ACTIVE", "SOCIAL");
        jdbcTemplate.update("""
                INSERT INTO social_account (id, account_id, provider, connected_at)
                VALUES (51, ?, 'KAKAO', CURRENT_TIMESTAMP)
                """, SOCIAL_ID);

        // 탈퇴 회원. 백엔드 탈퇴는 status 와 함께 deleted_at 도 채운다.
        insertAccount(WITHDRAWN_ID, "FREELANCER", "이탈퇴", "WITHDRAWN", "EMAIL");
        jdbcTemplate.update("UPDATE account SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", WITHDRAWN_ID);
    }

    @Test
    @DisplayName("요약 카드는 정상·정지·탈퇴·역할 건수를 함께 낸다")
    void summaryCountsEachBucket() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.active").value(3))
                .andExpect(jsonPath("$.data.suspended").value(0))
                .andExpect(jsonPath("$.data.withdrawn").value(1))
                .andExpect(jsonPath("$.data.clients").value(1))
                .andExpect(jsonPath("$.data.freelancers").value(3));
    }

    @Test
    @DisplayName("탈퇴 회원도 목록에 나온다 (deleted_at 이 채워져 있어도)")
    void withdrawnMemberStaysInList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("status", "WITHDRAWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(WITHDRAWN_ID))
                .andExpect(jsonPath("$.data.content[0].statusLabel").value("탈퇴"));
    }

    @Test
    @DisplayName("목록에 기업명과 진행 프로젝트 건수가 채워진다")
    void listCarriesCompanyNameAndProjectCount() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].companyName").value("삼성전자"))
                .andExpect(jsonPath("$.data.content[0].name").value("김담당"))
                // 종료(CLOSED)·취소(CANCELED)를 뺀 2건
                .andExpect(jsonPath("$.data.content[0].activeProjectCount").value(2));
    }

    @Test
    @DisplayName("프리랜서의 진행 프로젝트는 계약을 거쳐 센다")
    void freelancerProjectCountGoesThroughContract() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("keyword", "김프리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].activeProjectCount").value(1));
    }

    @Test
    @DisplayName("가입방식은 공급자까지 구분되고 필터로도 걸린다")
    void signupMethodResolvesProvider() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("signupMethod", "KAKAO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(SOCIAL_ID))
                .andExpect(jsonPath("$.data.content[0].signupMethod").value("KAKAO"))
                .andExpect(jsonPath("$.data.content[0].signupMethodLabel").value("카카오"));

        mockMvc.perform(get("/api/v1/admin/members").session(session).param("signupMethod", "GOOGLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/members").session(session).param("signupMethod", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @Test
    @DisplayName("기업명으로도 검색된다")
    void keywordMatchesCompanyName() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("keyword", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(CLIENT_ID));
    }

    @Test
    @DisplayName("클라이언트 상세에 사업자 정보와 프로젝트 집계가 담긴다")
    void clientDetailCarriesBusinessProfile() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}", CLIENT_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.companyName").value("삼성전자"))
                .andExpect(jsonPath("$.data.profile.businessNo").value("1248100998"))
                .andExpect(jsonPath("$.data.profile.businessField").value("IT·정보통신"))
                .andExpect(jsonPath("$.data.profile.employeeCount").value("100~299명"))
                .andExpect(jsonPath("$.data.activity.inProgressProjects").value(2))
                .andExpect(jsonPath("$.data.activity.completedProjects").value(1))
                .andExpect(jsonPath("$.data.activity.canceledProjects").value(1));
    }

    @Test
    @DisplayName("프리랜서 상세에 근무 조건과 기술 스택이 담긴다")
    void freelancerDetailCarriesCondition() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}", FREELANCER_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.condition.jobRole").value("BACKEND"))
                .andExpect(jsonPath("$.data.profile.condition.jobRoleLabel").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.profile.condition.jobCategoryLabel").value("개발"))
                .andExpect(jsonPath("$.data.profile.condition.careerYears").value(5))
                .andExpect(jsonPath("$.data.profile.condition.hasFreelanceExperience").value(true))
                .andExpect(jsonPath("$.data.profile.condition.payAmount").value(5000000))
                .andExpect(jsonPath("$.data.profile.condition.payUnitLabel").value("월급"))
                .andExpect(jsonPath("$.data.profile.condition.workStyleLabel").value("재택"))
                .andExpect(jsonPath("$.data.profile.condition.affiliation").value("무소속"))
                .andExpect(jsonPath("$.data.profile.aiMatchingAgreed").value(true))
                // 코드 순 정렬이라 DOTNET 이 SPRING_BOOT 보다 앞이다.
                .andExpect(jsonPath("$.data.profile.skills.length()").value(2))
                .andExpect(jsonPath("$.data.profile.skills[0].code").value("DOTNET"))
                // 규칙으로 유도할 수 없는 라벨이라 표에서 가져온다.
                .andExpect(jsonPath("$.data.profile.skills[0].label").value("C#/.NET"))
                .andExpect(jsonPath("$.data.profile.skills[1].label").value("Spring Boot"))
                .andExpect(jsonPath("$.data.profile.skills[1].levelLabel").value("고급"));
    }

    @Test
    @DisplayName("등급 라벨은 역할에 맞는 체계로 붙는다")
    void gradeLabelFollowsRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}", FREELANCER_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.gradeLabel").value("시니어"));

        // 같은 'grade' 필드지만 클라이언트는 실버/골드/다이아 체계다.
        mockMvc.perform(get("/api/v1/admin/members/{id}", CLIENT_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.gradeLabel").value("골드"));
    }

    @Test
    @DisplayName("근무 조건을 등록하지 않은 프리랜서는 condition 이 null 이고 skills 는 빈 배열이다")
    void freelancerWithoutConditionIsSafe() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO freelancer_profile (id, account_id, birth_date, grade, deleted_at)
                VALUES (9, ?, DATE '1990-01-01', 'JUNIOR', NULL)
                """, SOCIAL_ID);

        mockMvc.perform(get("/api/v1/admin/members/{id}", SOCIAL_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.data.profile.condition").doesNotExist())
                .andExpect(jsonPath("$.data.profile.skills.length()").value(0));
    }

    @Test
    @DisplayName("프리랜서 상세에 거래금액과 리뷰 통계가 담긴다")
    void freelancerDetailCarriesActivity() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}", FREELANCER_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.birthDate").value("1995-03-02"))
                .andExpect(jsonPath("$.data.profile.grade").value("SENIOR"))
                // 결제 완료 2건만 더한다. 미납 90만원은 빠진다.
                .andExpect(jsonPath("$.data.activity.totalTradeAmount").value(1500000))
                .andExpect(jsonPath("$.data.activity.reviewCount").value(2))
                .andExpect(jsonPath("$.data.activity.averageScore").value(4.5))
                .andExpect(jsonPath("$.data.activity.inProgressProjects").value(1));
    }

    @Test
    @DisplayName("활동 이력이 없으면 거래금액은 0, 평균 별점은 null 이다")
    void emptyActivityFallsBackSafely() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}", SOCIAL_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activity.totalTradeAmount").value(0))
                .andExpect(jsonPath("$.data.activity.reviewCount").value(0))
                // 0.0 으로 바꾸면 "별점 0점" 과 구분되지 않는다.
                .andExpect(jsonPath("$.data.activity.averageScore").doesNotExist());
    }

    // ==========================================

    private void insertProject(Long id, Long clientProfileId, String status) {
        jdbcTemplate.update("""
                INSERT INTO project (id, client_id, title, status, deleted_at)
                VALUES (?, ?, ?, ?, NULL)
                """, id, clientProfileId, "프로젝트 " + id, status);
    }

    private void insertSettlement(Long id, Long payerAccountId, long amount, String status) {
        jdbcTemplate.update("""
                INSERT INTO settlement (id, payer_account_id, base_amount, status)
                VALUES (?, ?, ?, ?)
                """, id, payerAccountId, amount, status);
    }

    private void insertAccount(Long id, String role, String name, String status, String signupType) {
        jdbcTemplate.update("""
                        INSERT INTO account (id, email, role, name, phone, signup_type, status,
                                             email_verified, login_fail_count, is_temp_password,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, 0, FALSE,
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                id, "member-" + id + "@pairing.com", role, name, "0100000" + id, signupType, status);
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", USERNAME, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
