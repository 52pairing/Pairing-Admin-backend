package com.pairing.admin.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사이트 리뷰 관리 API 검증.
 *
 * <p>후기 행은 {@link JdbcTemplate} 으로 직접 넣는다. 이 서버에는 후기 작성 API 가 없고,
 * 엔티티도 {@code created_at} 을 쓰기 대상에서 빼 두었다.
 *
 * <p>작성자명·프로젝트명은 site_review 에 없는 값이다. 그 검색이 실제로 동작하는지 보려고
 * {@code account}/{@code client_profile}/{@code project} 에 짝이 되는 행을 함께 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/shared-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("사이트 리뷰 관리")
class SiteReviewAdminFlowTest {

    private static final String USERNAME = "review-admin";
    private static final String PASSWORD = "Admin!2345";

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

    private MockHttpSession session;
    private Long clientReviewId;
    private Long freelancerReviewId;
    private Long privateReviewId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM site_review");
        jdbcTemplate.update("DELETE FROM client_profile");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM account");
        adminUserJpaRepository.deleteAll();

        adminUserJpaRepository.save(AdminUserJpaEntity.create(USERNAME, passwordEncoder.encode(PASSWORD)));
        session = login();

        // 클라이언트는 회사명이 표시명이다
        insertAccount(401L, "client@samsung.kr", "CLIENT", "김담당");
        jdbcTemplate.update(
                "INSERT INTO client_profile (account_id, company_name, deleted_at) VALUES (?, ?, NULL)",
                401L, "삼성전자");
        insertAccount(402L, "kim@free.kr", "FREELANCER", "김프리");

        jdbcTemplate.update("INSERT INTO project (id, title) VALUES (?, ?)", 501L, "쇼핑몰 관리자 페이지");
        jdbcTemplate.update("INSERT INTO project (id, title) VALUES (?, ?)", 502L, "데이터 파이프라인 구축");

        clientReviewId = insertReview(501L, 401L, "CLIENT", 5,
                "매칭 속도가 빠르고 AI 협상 기능이 유용했습니다.", "PUBLIC", true, LocalDateTime.now());

        freelancerReviewId = insertReview(502L, 402L, "FREELANCER", 4,
                "플랫폼 사용이 직관적이에요.", "PUBLIC", false, LocalDateTime.now());

        // 지난달에 작성된 비공개 후기. 이번 달 카운트에서 빠져야 한다.
        privateReviewId = insertReview(501L, 402L, "FREELANCER", 3,
                "개선이 필요한 부분이 있습니다.", "PRIVATE", false,
                LocalDateTime.now().minusMonths(2));
    }

    @Test
    @DisplayName("요약은 평균·전체·이번 달·홍보·공개를 세고, 별점 분포는 0건도 채운다")
    void summaryCountsAndFillsDistribution() throws Exception {
        mockMvc.perform(get("/api/v1/admin/site-reviews/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.thisMonthCount").value(2))
                .andExpect(jsonPath("$.data.promotedCount").value(1))
                .andExpect(jsonPath("$.data.notPromotedCount").value(2))
                .andExpect(jsonPath("$.data.publicCount").value(2))
                .andExpect(jsonPath("$.data.scoreDistribution.5").value(1))
                .andExpect(jsonPath("$.data.scoreDistribution.4").value(1))
                .andExpect(jsonPath("$.data.scoreDistribution.3").value(1))
                // 0건인 별점도 그래프에 빈 막대로 나와야 한다
                .andExpect(jsonPath("$.data.scoreDistribution.2").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution.1").value(0));
    }

    @Test
    @DisplayName("목록의 작성자명은 클라이언트면 회사명, 프리랜서면 이름이다")
    void writerNameUsesCompanyNameForClient() throws Exception {
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session)
                        .param("writerRole", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].writerName").value("삼성전자"))
                .andExpect(jsonPath("$.data.content[0].projectTitle").value("쇼핑몰 관리자 페이지"));

        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session)
                        .param("score", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].writerName").value("김프리"));
    }

    @Test
    @DisplayName("검색어 하나로 회원명·내용·프로젝트명을 모두 찾는다")
    void keywordSearchesWriterContentAndProject() throws Exception {
        // 회원명(회사명) — site_review 에 없는 값이다
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session).param("keyword", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].siteReviewId").value(clientReviewId));

        // 후기 내용
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session).param("keyword", "직관적"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].siteReviewId").value(freelancerReviewId));

        // 프로젝트명 — 이 프로젝트에는 후기가 두 건 달려 있다
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session).param("keyword", "쇼핑몰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        // 아무것도 안 걸리는 검색어
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session).param("keyword", "없는단어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("회원명 검색은 프리랜서 이름으로도 걸린다")
    void keywordSearchesFreelancerName() throws Exception {
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session).param("keyword", "김프리"))
                .andExpect(status().isOk())
                // 김프리가 쓴 후기 두 건
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("공개 여부·홍보 여부로 필터링된다")
    void filtersByVisibilityAndPromoted() throws Exception {
        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session)
                        .param("visibility", "PRIVATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].siteReviewId").value(privateReviewId));

        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session)
                        .param("promoted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].siteReviewId").value(clientReviewId));
    }

    @Test
    @DisplayName("비공개 후기를 공개 + 홍보로 바꿀 수 있다")
    void updateVisibilityToPublicAndPromoted() throws Exception {
        mockMvc.perform(put("/api/v1/admin/site-reviews/" + privateReviewId + "/visibility")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PUBLIC", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.promoted").value(true))
                // 응답에도 작성자명·프로젝트명이 채워져 있어야 화면이 그대로 갱신된다
                .andExpect(jsonPath("$.data.writerName").value("김프리"))
                .andExpect(jsonPath("$.data.projectTitle").value("쇼핑몰 관리자 페이지"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT visibility, promoted FROM site_review WHERE id = ?", privateReviewId);
        assertThat(row.get("visibility")).isEqualTo("PUBLIC");
        assertThat(row.get("promoted")).isEqualTo(true);
    }

    @Test
    @DisplayName("비공개 + 홍보 활용 조합은 400 으로 막는다")
    void rejectsPromotingPrivateReview() throws Exception {
        mockMvc.perform(put("/api/v1/admin/site-reviews/" + clientReviewId + "/visibility")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PRIVATE", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_REVIEW_002"));

        // 막혔으므로 원래 값이 그대로여야 한다
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM site_review WHERE id = ?", String.class, clientReviewId))
                .isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("없는 리뷰를 바꾸려 하면 404 다")
    void missingReviewReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/admin/site-reviews/999999/visibility")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PUBLIC", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_REVIEW_001"));
    }

    @Test
    @DisplayName("계정이 지워진 후기는 작성자 구분으로 대체 표시된다")
    void deletedAccountFallsBackToRoleLabel() throws Exception {
        jdbcTemplate.update("DELETE FROM client_profile WHERE account_id = ?", 401L);
        jdbcTemplate.update("DELETE FROM account WHERE id = ?", 401L);

        mockMvc.perform(get("/api/v1/admin/site-reviews").session(session)
                        .param("writerRole", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].writerName").value("클라이언트"));
    }

    @Test
    @DisplayName("로그인하지 않으면 리뷰 목록을 볼 수 없다")
    void requiresAdminSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/site-reviews"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", USERNAME, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String body(String visibility, boolean promoted) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", visibility);
        body.put("promoted", promoted);
        return objectMapper.writeValueAsString(body);
    }

    private void insertAccount(Long id, String email, String role, String name) {
        jdbcTemplate.update("""
                        INSERT INTO account (id, email, role, name, phone, signup_type, status,
                                             email_verified, login_fail_count, is_temp_password,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, '01000000000', 'EMAIL', 'ACTIVE', TRUE, 0, FALSE, ?, ?)
                        """,
                id, email, role, name, LocalDateTime.now(), LocalDateTime.now());
    }

    private Long insertReview(Long projectId, Long writerAccountId, String writerRole, int score,
                              String content, String visibility, boolean promoted, LocalDateTime createdAt) {

        jdbcTemplate.update("""
                        INSERT INTO site_review (contract_id, project_id, writer_account_id, writer_role,
                                                 score, content, visibility, promoted, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                projectId, projectId, writerAccountId, writerRole, score, content, visibility, promoted, createdAt);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM site_review WHERE content = ?", Long.class, content);
    }
}
