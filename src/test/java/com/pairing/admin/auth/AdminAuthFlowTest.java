package com.pairing.admin.auth;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 로그인 흐름 검증.
 *
 * <p>확인하려는 것은 두 가지다.
 * <ol>
 *   <li><b>로그인 후 세션이 실제로 유지되는가.</b> 스프링 시큐리티 6부터는 인증 정보가
 *       자동으로 세션에 저장되지 않아서, {@code securityContextRepository.saveContext(...)} 를
 *       빠뜨리면 "로그인은 200인데 다음 요청은 401" 이 된다. 눈으로는 잘 안 보이는 실수라 못 박아 둔다.</li>
 *   <li><b>인증 대상이 admin_user 테이블뿐인가.</b> 회원(account) 계정으로는 들어올 수 없어야 한다.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("관리자 세션 로그인")
class AdminAuthFlowTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Admin!2345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserJpaRepository adminUserJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminUserJpaRepository.deleteAll();
        adminUserJpaRepository.save(
                AdminUserJpaEntity.create(USERNAME, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    @DisplayName("로그인에 성공하면 세션이 만들어지고, 그 세션으로 보호된 API를 호출할 수 있다")
    void loginThenAccessProtectedApi() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).as("로그인 성공 시 세션이 생성되어야 한다").isNotNull();

        // 세션을 들고 가면 통과
        mockMvc.perform(get("/api/v1/admin/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(USERNAME));

        // 세션 없이 가면 401
        mockMvc.perform(get("/api/v1/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_GLOBAL_006"));
    }

    @Test
    @DisplayName("로그아웃하면 같은 세션으로는 더 이상 호출할 수 없다")
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/admin/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401이고, 실패 횟수가 누적된다")
    void wrongPasswordIncreasesFailCount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USERNAME, "WrongPassword1!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_001"));

        // 실패 기록은 별도 트랜잭션에서 커밋되므로, 예외가 나도 롤백되지 않아야 한다
        assertThat(adminUserJpaRepository.findByUsername(USERNAME).orElseThrow().getLoginFailCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("연속 실패가 상한에 닿으면 계정이 잠기고, 이후에는 올바른 비밀번호로도 로그인할 수 없다")
    void lockAfterMaxFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(USERNAME, "WrongPassword1!")))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(adminUserJpaRepository.findByUsername(USERNAME).orElseThrow().isLocked()).isTrue();

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_002"));
    }

    @Test
    @DisplayName("존재하지 않는 아이디는 계정이 없는 것과 비밀번호가 틀린 것을 구분하지 않는다")
    void unknownUsernameLooksTheSameAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("no-such-admin", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_001"));
    }

    @Test
    @DisplayName("보호된 관리 API도 세션이 있어야 호출할 수 있다")
    void memberApiRequiresSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/members").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ------------------------------------------------------------------

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String body(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }
}
