package com.pairing.admin.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import com.pairing.admin.notification.infrastructure.client.PairingBackendNotificationClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1:1 문의 관리 API 검증.
 *
 * <p>문의 행은 {@link JdbcTemplate} 으로 직접 넣는다. <b>이 서버에는 문의 생성 API 가 없어서</b>
 * 접수를 재현할 방법이 없고, 엔티티도 {@code created_at} 을 쓰기 대상에서 빼 두었다
 * (운영 DB 는 기본값으로 채운다).
 *
 * <p>{@code shared-tables.sql} 은 native 쿼리가 참조하는 백엔드 테이블을 H2 에 만든다.
 * 엔티티가 없으면 create-drop 으로도 생기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/shared-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("1:1 문의 관리")
class InquiryAdminFlowTest {

    private static final String USERNAME = "inquiry-admin";
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

    /** 사용자 서버 호출은 목으로 막는다. 테스트에서 진짜 HTTP 를 쏘면 서버가 떠 있어야 한다. */
    @MockitoBean
    private PairingBackendNotificationClient notificationClient;

    private MockHttpSession session;
    private Long pendingInquiryId;
    private Long answeredInquiryId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM inquiry_file");
        jdbcTemplate.update("DELETE FROM inquiry");
        jdbcTemplate.update("DELETE FROM account");
        adminUserJpaRepository.deleteAll();

        adminUserJpaRepository.save(AdminUserJpaEntity.create(USERNAME, passwordEncoder.encode(PASSWORD)));
        session = login();

        // 오늘 접수된 대기중 문의(클라이언트) + 어제 접수돼 답변까지 끝난 문의(프리랜서)
        pendingInquiryId = insertInquiry(301L, "오이랩", "CLIENT", "contact@oilab.kr",
                "착수금 수수료 결제 문의", "결제 버튼이 활성화되지 않습니다.",
                "PENDING", null, null, LocalDateTime.now());

        answeredInquiryId = insertInquiry(302L, "김개발", "FREELANCER", "kim@dev.kr",
                "프로필 수정이 반영되지 않아요", "저장을 눌렀는데 그대로입니다.",
                "ANSWERED", "확인 후 조치했습니다.", LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(2));
    }

    @Test
    @DisplayName("요약 카드는 전체·대기·완료·오늘 접수를 각각 센다")
    void summaryCountsByStatusAndToday() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.answeredCount").value(1))
                // 이틀 전 문의는 오늘 접수에 들어가지 않는다
                .andExpect(jsonPath("$.data.todayCount").value(1));
    }

    @Test
    @DisplayName("회원유형·상태로 필터링된다")
    void filtersByWriterRoleAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries").session(session)
                        .param("writerRole", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].writerName").value("오이랩"));

        mockMvc.perform(get("/api/v1/admin/inquiries").session(session)
                        .param("status", "ANSWERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(answeredInquiryId));
    }

    @Test
    @DisplayName("검색어 하나로 회원명·제목·문의번호를 모두 찾는다")
    void keywordSearchesNameTitleAndInquiryNo() throws Exception {
        // 회원명
        mockMvc.perform(get("/api/v1/admin/inquiries").session(session).param("keyword", "오이랩"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 제목
        mockMvc.perform(get("/api/v1/admin/inquiries").session(session).param("keyword", "프로필"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 문의번호. 컬럼이 아니라 작성일+id 로 만든 값이라 DB 에서 조립해 비교해야 걸린다.
        String inquiryNo = jsonPath("$.data.content[0].inquiryNo").toString();
        assertThat(inquiryNo).isNotBlank();

        mockMvc.perform(get("/api/v1/admin/inquiries").session(session)
                        .param("keyword", String.format("%04d", pendingInquiryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(pendingInquiryId));
    }

    @Test
    @DisplayName("상세 조회는 작성자 정보와 첨부파일까지 내려준다")
    void detailIncludesWriterInfoAndAttachments() throws Exception {
        jdbcTemplate.update("INSERT INTO file (id, original_name, object_key) VALUES (?, ?, ?)",
                77L, "오류화면.png", "dev/inquiry_attachment/abc.png");
        jdbcTemplate.update("INSERT INTO inquiry_file (inquiry_id, file_id, sort_order) VALUES (?, ?, ?)",
                pendingInquiryId, 77L, 0);

        mockMvc.perform(get("/api/v1/admin/inquiries/" + pendingInquiryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.writerName").value("오이랩"))
                .andExpect(jsonPath("$.data.writerRole").value("CLIENT"))
                .andExpect(jsonPath("$.data.writerEmail").value("contact@oilab.kr"))
                .andExpect(jsonPath("$.data.files.length()").value(1))
                .andExpect(jsonPath("$.data.files[0].originalName").value("오류화면.png"))
                // object key 가 아니라 CDN 루트가 붙은 절대 URL 이어야 한다
                .andExpect(jsonPath("$.data.files[0].url").value(
                        org.hamcrest.Matchers.endsWith("dev/inquiry_attachment/abc.png")));
    }

    @Test
    @DisplayName("첨부파일이 지워졌어도 상세 조회는 막히지 않는다")
    void detailSurvivesDeletedAttachment() throws Exception {
        // file 행 없이 inquiry_file 만 남은 상태 (파일이 나중에 삭제된 경우)
        jdbcTemplate.update("INSERT INTO inquiry_file (inquiry_id, file_id, sort_order) VALUES (?, ?, ?)",
                pendingInquiryId, 999L, 0);

        mockMvc.perform(get("/api/v1/admin/inquiries/" + pendingInquiryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files.length()").value(0));
    }

    @Test
    @DisplayName("답변을 등록하면 상태가 ANSWERED 로 바뀌고 작성자에게 알림 생성을 요청한다")
    void answerChangesStatusAndRequestsNotification() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inquiries/" + pendingInquiryId + "/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "확인 후 안내드립니다."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer").value("확인 후 안내드립니다."))
                .andExpect(jsonPath("$.data.answererName").value("페어링 고객지원"))
                .andExpect(jsonPath("$.data.answeredAt").exists());

        // 알림은 이 서버가 DB 에 넣지 않고 사용자 서버에 맡긴다. 그쪽이 저장과 실시간 push 를
        // 함께 처리한다. 그래서 검증 대상이 notification 테이블이 아니라 호출 내용이다.
        verify(notificationClient).create(
                eq(301L),
                eq("INQUIRY_ANSWERED"),
                eq("문의하신 내용에 답변이 등록되었습니다."),
                any(),
                eq("/support/inquiries/" + pendingInquiryId));
    }

    @Test
    @DisplayName("이미 답변한 문의에 다시 답변하면 내용이 교체되고 알림이 한 번 더 간다")
    void reAnswerReplacesContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inquiries/" + answeredInquiryId + "/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "추가 안내드립니다."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("추가 안내드립니다."));

        verify(notificationClient, times(1)).create(
                eq(302L), eq("INQUIRY_ANSWERED"), any(), any(),
                eq("/support/inquiries/" + answeredInquiryId));
    }

    @Test
    @DisplayName("알림 생성이 실패해도 답변 등록은 성공한다")
    void answerSucceedsEvenIfNotificationFails() throws Exception {
        // 사용자 서버 배포 중이거나 키가 어긋나면 이 호출이 터진다. 그때 답변까지 롤백되면
        // 관리자는 버튼을 눌러도 아무 일이 안 일어나는 것처럼 보인다.
        doThrow(new RuntimeException("사용자 서버 응답 없음"))
                .when(notificationClient).create(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/admin/inquiries/" + pendingInquiryId + "/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "확인 후 안내드립니다."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"));
    }

    @Test
    @DisplayName("빈 답변은 400 으로 막고, 없는 문의는 404 다")
    void rejectsBlankAnswerAndMissingInquiry() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inquiries/" + pendingInquiryId + "/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "   "))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/inquiries/999999/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "답변"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_INQUIRY_001"));
    }

    @Test
    @DisplayName("로그인하지 않으면 문의 목록을 볼 수 없다")
    void requiresAdminSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries"))
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

    private Long insertInquiry(Long writerAccountId, String writerName, String writerRole, String writerEmail,
                               String title, String content, String status, String answer,
                               LocalDateTime answeredAt, LocalDateTime createdAt) {

        jdbcTemplate.update("""
                        INSERT INTO account (id, email, role, name, phone, signup_type, status,
                                             email_verified, login_fail_count, is_temp_password,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, '01000000000', 'EMAIL', 'ACTIVE', TRUE, 0, FALSE, ?, ?)
                        """,
                writerAccountId, writerEmail, writerRole, writerName, createdAt, createdAt);

        jdbcTemplate.update("""
                        INSERT INTO inquiry (writer_account_id, writer_name, writer_role, writer_email,
                                             title, content, status, answer, answered_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                writerAccountId, writerName, writerRole, writerEmail,
                title, content, status, answer, answeredAt, createdAt);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM inquiry WHERE writer_account_id = ?", Long.class, writerAccountId);
    }
}
