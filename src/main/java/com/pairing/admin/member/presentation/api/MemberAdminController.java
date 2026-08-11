package com.pairing.admin.member.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.security.AdminPrincipal;
import com.pairing.admin.global.security.CurrentAdmin;
import com.pairing.admin.member.application.MemberAdminService;
import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.presentation.api.request.MemberLockRequest;
import com.pairing.admin.member.presentation.api.response.MemberDetailResponse;
import com.pairing.admin.member.presentation.api.response.MemberSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 관리. (관리자 &gt; 회원 관리)
 *
 * <p>이 도메인은 스켈레톤이 아니라 <b>실제로 동작하는 예시</b>다.
 * 새 관리 화면을 만들 때 이 구성(Controller → Service → Repository/Specs → Response)을 그대로 따라가면 된다.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
@Tag(name = "10. Member", description = "회원 관리 API")
public class MemberAdminController {

    private final MemberAdminService memberAdminService;

    @GetMapping
    @Operation(summary = "[관리자] 회원 목록 조회",
            description = "역할·상태·키워드(이름/이메일/휴대폰)로 검색합니다. 조건은 비우면 전체입니다.")
    public ResponseEntity<ApiResponse<PageResponse<MemberSummaryResponse>>> findMembers(

            @Parameter(description = "역할 필터", example = "FREELANCER")
            @RequestParam(required = false) Role role,

            @Parameter(description = "상태 필터", example = "ACTIVE")
            @RequestParam(required = false) AccountStatus status,

            @Parameter(description = "이름·이메일·휴대폰 부분 검색어", example = "홍길동")
            @RequestParam(required = false) String keyword,

            // 정렬 기본값은 최신 가입순. 관리자 화면에서 가장 자주 보는 순서다.
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<MemberSummaryResponse> members =
                memberAdminService.search(role, status, keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success("MEMBER_LIST_FOUND", "조회에 성공했습니다.", members));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "[관리자] 회원 상세 조회")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> findMember(@PathVariable Long accountId) {
        MemberDetailResponse member = memberAdminService.findDetail(accountId);
        return ResponseEntity.ok(ApiResponse.success("MEMBER_FOUND", "조회에 성공했습니다.", member));
    }

    @PatchMapping("/{accountId}/lock")
    @Operation(summary = "[관리자] 회원 정지",
            description = "계정을 LOCKED 로 바꿉니다. 백엔드 로그인이 즉시 막힙니다. 관리자 계정은 대상이 될 수 없습니다.")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> lockMember(@PathVariable Long accountId,
                                                                        @Valid @RequestBody MemberLockRequest request,
                                                                        @CurrentAdmin AdminPrincipal admin) {

        MemberDetailResponse member = memberAdminService.lock(accountId, request.reason(), admin.getAdminId());
        return ResponseEntity.ok(ApiResponse.success("MEMBER_LOCKED", "회원을 정지했습니다.", member));
    }

    @PatchMapping("/{accountId}/unlock")
    @Operation(summary = "[관리자] 회원 정지 해제",
            description = "계정을 ACTIVE 로 되돌리고 비밀번호 실패 횟수를 초기화합니다.")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> unlockMember(@PathVariable Long accountId,
                                                                          @CurrentAdmin AdminPrincipal admin) {

        MemberDetailResponse member = memberAdminService.unlock(accountId, admin.getAdminId());
        return ResponseEntity.ok(ApiResponse.success("MEMBER_UNLOCKED", "정지를 해제했습니다.", member));
    }
}
