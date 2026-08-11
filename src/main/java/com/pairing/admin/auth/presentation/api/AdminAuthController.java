package com.pairing.admin.auth.presentation.api;

import com.pairing.admin.auth.application.AdminAuthService;
import com.pairing.admin.auth.presentation.api.request.AdminLoginRequest;
import com.pairing.admin.auth.presentation.api.request.AdminPasswordChangeRequest;
import com.pairing.admin.auth.presentation.api.response.AdminMeResponse;
import com.pairing.admin.auth.presentation.api.response.CsrfTokenResponse;
import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.security.AdminPrincipal;
import com.pairing.admin.global.security.CurrentAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인증. (세션 로그인)
 *
 * <p>로그인에 성공하면 {@code ADMIN_SESSION} 쿠키가 내려간다. 이후 요청은 이 쿠키로 인증된다.
 * 프론트는 모든 요청에 {@code credentials: 'include'} (axios 는 {@code withCredentials: true}) 를 켜야 한다.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "01. Auth", description = "관리자 인증 API (세션 로그인)")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @GetMapping("/csrf")
    @Operation(summary = "CSRF 토큰 조회",
            description = """
                    로그인 화면 진입 시 한 번 호출한다. 응답으로 XSRF-TOKEN 쿠키가 함께 내려간다.
                    axios 를 쓴다면 쿠키를 자동으로 읽어 헤더에 실어주므로 응답 본문은 쓰지 않아도 된다.
                    """)
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> findCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        // CSRF 를 꺼 둔 환경(app.security.csrf-enabled=false)에서는 토큰이 없다.
        CsrfTokenResponse body = (csrfToken == null)
                ? CsrfTokenResponse.disabled()
                : new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken(), true);

        return ResponseEntity.ok(ApiResponse.success("CSRF_TOKEN_ISSUED", "조회에 성공했습니다.", body));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인",
            description = """
                    admin_user 테이블의 계정으로 로그인합니다. 회원(account) 계정으로는 접근할 수 없습니다.
                    성공 시 세션 쿠키(ADMIN_SESSION)가 발급됩니다.
                    """)
    public ResponseEntity<ApiResponse<AdminMeResponse>> login(@Valid @RequestBody AdminLoginRequest request,
                                                              HttpServletRequest httpRequest,
                                                              HttpServletResponse httpResponse) {

        AdminMeResponse admin = adminAuthService.login(
                request.username(), request.password(), httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", "로그인에 성공했습니다.", admin));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "세션을 무효화하고 세션 쿠키를 만료시킵니다.")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {

        adminAuthService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("LOGOUT_SUCCESS", "로그아웃되었습니다."));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회",
            description = "세션이 살아 있는지 확인하는 용도로도 쓴다. 만료됐다면 401 이 돌아온다.")
    public ResponseEntity<ApiResponse<AdminMeResponse>> findMe(@CurrentAdmin AdminPrincipal admin) {
        AdminMeResponse me = adminAuthService.findMe(admin.getAdminId());
        return ResponseEntity.ok(ApiResponse.success("ADMIN_FOUND", "조회에 성공했습니다.", me));
    }

    @PatchMapping("/password")
    @Operation(summary = "비밀번호 변경",
            description = "부트스트랩으로 만든 초기 비밀번호는 첫 로그인 후 여기서 바꾼다.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@CurrentAdmin AdminPrincipal admin,
                                                            @Valid @RequestBody AdminPasswordChangeRequest request) {

        adminAuthService.changePassword(admin.getAdminId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("PASSWORD_CHANGED", "비밀번호가 변경되었습니다."));
    }
}
