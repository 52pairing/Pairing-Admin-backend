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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 정지·해제 검증. (관리자 &gt; 회원 관리)
 *
 * <p>정지는 두 곳을 함께 건드려야 완결된다.
 * <ul>
 *   <li>공유 Redis 의 {@code SUSPEND:{id}} 생성 → 백엔드가 새 로그인을 막는다. <b>이쪽이 원본</b>이다.</li>
 *   <li>공유 Redis 의 {@code RT:{id}} / {@code SESSION:{id}} 삭제 → 이미 로그인된 세션이 즉시 끊긴다</li>
 *   <li>공유 DB 의 {@code account.suspended_at} → 목록 필터·요약 카드가 SQL 로 셀 수 있게 한다</li>
 * </ul>
 * 두 번째를 빠뜨리면 정지해도 액세스 토큰 수명(기본 30분)만큼 계속 서비스를 쓸 수 있다.
 *
 * <p>Redis 는 목으로 대체한다. 검증 대상은 "정확한 키를 건드리라고 요청하는가" 이고, 실제 삭제는
 * Redis 자체의 동작이다. 키 이름이 백엔드와 어긋나면 조용히 실패하는 종류의 버그라 여기서 못 박아 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/shared-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("회원 정지·해제")
class MemberSuspensionFlowTest {

    private static final String USERNAME = "member-admin";
    private static final String PASSWORD = "Admin!2345";
    private static final Long TARGET_ID = 501L;

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

    /** 테스트에는 실제 Redis 가 없다. 어떤 키를 건드리는지만 확인한다. */
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOperations;
    private MockHttpSession session;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        // 목이라 opsForValue() 가 기본적으로 null 을 돌려준다. 정지가 값을 쓰므로 미리 물려 둔다.
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        jdbcTemplate.update("DELETE FROM account");
        adminUserJpaRepository.deleteAll();

        adminUserJpaRepository.save(AdminUserJpaEntity.create(USERNAME, passwordEncoder.encode(PASSWORD)));
        session = login();

        insertAccount(TARGET_ID, "FREELANCER", "김프리", "ACTIVE");
    }

    @Test
    @DisplayName("정지하면 Redis 마커가 생기고 세션 키가 지워진다")
    void suspendCreatesMarkerAndClearsBackendSession() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반 신고 누적"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.statusLabel").value("정지"))
                .andExpect(jsonPath("$.data.suspended").value(true))
                .andExpect(jsonPath("$.data.suspendReason").value("약관 위반 신고 누적"));

        // 백엔드의 RedisKeys 와 같은 이름이어야 한다. 어긋나면 정지해도 로그인이 막히지 않는다.
        verify(valueOperations).set(eq("SUSPEND:" + TARGET_ID), anyString());
        verify(redisTemplate).delete(List.of("RT:" + TARGET_ID, "SESSION:" + TARGET_ID));
    }

    @Test
    @DisplayName("정지해도 account.status 는 그대로다 (백엔드 enum 에 SUSPENDED 가 없다)")
    void suspensionDoesNotTouchStatusColumn() throws Exception {
        suspend();

        // status 에 'SUSPENDED' 를 써 넣으면 백엔드가 이 계정을 읽는 순간 IllegalArgumentException 이 난다.
        assertThat(storedStatus(TARGET_ID)).isEqualTo("ACTIVE");
        assertThat(storedSuspendedAt(TARGET_ID)).isNotNull();
    }

    @Test
    @DisplayName("정지 해제하면 마커가 지워지고 사유·실패 횟수가 초기화된다")
    void releaseSuspensionRestoresAccount() throws Exception {
        suspend();

        mockMvc.perform(delete("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.suspended").value(false))
                .andExpect(jsonPath("$.data.suspendReason").doesNotExist())
                .andExpect(jsonPath("$.data.loginFailCount").value(0));

        assertThat(storedSuspendedAt(TARGET_ID)).isNull();
        verify(redisTemplate).delete("SUSPEND:" + TARGET_ID);
    }

    @Test
    @DisplayName("정지와 해제를 반복할 수 있다")
    void suspensionCanBeToggledRepeatedly() throws Exception {
        for (int round = 0; round < 3; round++) {
            suspend();
            assertThat(storedSuspendedAt(TARGET_ID)).isNotNull();

            mockMvc.perform(delete("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session))
                    .andExpect(status().isOk());
            assertThat(storedSuspendedAt(TARGET_ID)).isNull();
        }
    }

    @Test
    @DisplayName("이미 정지된 회원을 다시 정지하면 409로 막고 세션 파기도 하지 않는다")
    void suspendingTwiceIsRejected() throws Exception {
        suspend();
        org.mockito.Mockito.clearInvocations(redisTemplate, valueOperations);

        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"중복 정지"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_MEMBER_003"));

        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    @DisplayName("정지 상태가 아닌 회원의 해제는 409로 막는다")
    void releasingNonSuspendedIsRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_MEMBER_003"));
    }

    @Test
    @DisplayName("비밀번호 5회 실패로 잠긴(LOCKED) 회원도 정지할 수 있다")
    void lockedMemberCanStillBeSuspended() throws Exception {
        jdbcTemplate.update("UPDATE account SET status = 'LOCKED' WHERE id = ?", TARGET_ID);

        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"잠긴 계정도 정지 가능"}"""))
                .andExpect(status().isOk())
                // 화면에는 정지가 잠금보다 우선해 보여야 한다. 관리자가 정지 여부를 알 수 없으면 안 된다.
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        // LOCKED 는 본인이 이메일 인증으로 풀 수 있다. status 를 덮어쓰지 않았으므로 그 이력이 남는다.
        assertThat(storedStatus(TARGET_ID)).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("탈퇴한 회원은 정지할 수 없다")
    void withdrawnMemberCannotBeSuspended() throws Exception {
        jdbcTemplate.update("UPDATE account SET status = 'WITHDRAWN' WHERE id = ?", TARGET_ID);

        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"x"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_MEMBER_004"));
    }

    @Test
    @DisplayName("관리자 역할 계정은 정지할 수 없다 (서로 잠가 아무도 못 들어오는 상태 방지)")
    void adminRoleAccountCannotBeSuspended() throws Exception {
        insertAccount(502L, "ADMIN", "관리자", "ACTIVE");

        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", 502L).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"x"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_MEMBER_002"));
    }

    @Test
    @DisplayName("정지 사유는 필수다")
    void reasonIsRequired() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정지 회원은 목록의 상태 필터로 걸러진다")
    void suspendedMemberIsFilterableInList() throws Exception {
        insertAccount(502L, "FREELANCER", "박정상", "ACTIVE");
        suspend();

        mockMvc.perform(get("/api/v1/admin/members").session(session).param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(TARGET_ID));

        // 정지 회원의 status 는 ACTIVE 그대로다. "정상" 필터에 섞이면 안 된다.
        mockMvc.perform(get("/api/v1/admin/members").session(session).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(502L));
    }

    // ==========================================

    private void suspend() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/members/{id}/suspension", TARGET_ID).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반 신고 누적"}"""))
                .andExpect(status().isOk());
    }

    private String storedStatus(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM account WHERE id = ?", String.class, accountId);
    }

    private Object storedSuspendedAt(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT suspended_at FROM account WHERE id = ?", Object.class, accountId);
    }

    private void insertAccount(Long id, String role, String name, String status) {
        jdbcTemplate.update("""
                        INSERT INTO account (id, email, role, name, phone, signup_type, status,
                                             email_verified, login_fail_count, is_temp_password,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, '01000000000', 'EMAIL', ?, TRUE, 0, FALSE,
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                id, "member-" + id + "@pairing.com", role, name, status);
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
