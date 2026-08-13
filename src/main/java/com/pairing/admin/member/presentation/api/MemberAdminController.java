package com.pairing.admin.member.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.security.AdminPrincipal;
import com.pairing.admin.global.security.CurrentAdmin;
import com.pairing.admin.member.application.MemberAdminService;
import com.pairing.admin.member.domain.MemberStatusFilter;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupMethod;
import com.pairing.admin.member.presentation.api.request.MemberSuspendRequest;
import com.pairing.admin.member.presentation.api.response.MemberDetailResponse;
import com.pairing.admin.member.presentation.api.response.MemberStatsResponse;
import com.pairing.admin.member.presentation.api.response.MemberSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * <p>화면은 세 조각이다 — 상단 요약 카드({@code /summary}), 목록({@code /}), 상세({@code /{id}}).
 * 요약을 목록 응답에 끼워 넣지 않은 이유는, 페이지를 넘길 때마다 같은 집계를 다시 돌리게 되기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
@Tag(name = "10. Member", description = "회원 관리 API")
public class MemberAdminController {

    private final MemberAdminService memberAdminService;

    @GetMapping("/summary")
    @Operation(summary = "[관리자] 회원 요약 카드",
            description = "전체·정상·정지·탈퇴·클라이언트·프리랜서 건수를 반환합니다. "
                    + "정상은 정지되지 않은 ACTIVE 만 세므로 여섯 값의 합은 전체와 다릅니다.")
    public ResponseEntity<ApiResponse<MemberStatsResponse>> findStats() {
        MemberStatsResponse stats = memberAdminService.findStats();
        return ResponseEntity.ok(ApiResponse.success("MEMBER_STATS_FOUND", "조회에 성공했습니다.", stats));
    }

    @GetMapping
    @Operation(summary = "[관리자] 회원 목록 조회",
            description = "유형·상태·가입방식·키워드로 검색합니다. 조건은 비우면 전체입니다. "
                    + "정렬은 최신 가입순으로 고정이며 sort 파라미터는 무시합니다. "
                    + "탈퇴 회원도 목록에 포함됩니다(상태 필터 WITHDRAWN 으로 추릴 수 있습니다).")
    public ResponseEntity<ApiResponse<PageResponse<MemberSummaryResponse>>> findMembers(

            @Parameter(description = "유형 필터", example = "FREELANCER")
            @RequestParam(required = false) Role role,

            @Parameter(description = "상태 필터. SUSPENDED 는 관리자 정지, LOCKED 는 비밀번호 5회 실패 잠금입니다.",
                    example = "ACTIVE")
            @RequestParam(required = false) MemberStatusFilter status,

            @Parameter(description = "가입방식 필터. SOCIAL 은 공급자를 가리지 않습니다.", example = "KAKAO")
            @RequestParam(required = false) SignupMethod signupMethod,

            @Parameter(description = "이름·이메일·휴대폰·기업명 부분 검색어", example = "홍길동")
            @RequestParam(required = false) String keyword,

            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<MemberSummaryResponse> members =
                memberAdminService.search(role, status, signupMethod, keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success("MEMBER_LIST_FOUND", "조회에 성공했습니다.", members));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "[관리자] 회원 상세 조회",
            description = "기본 정보와 함께 역할별 프로필(기업명·사업자번호·사업분야·직원수 등)과 "
                    + "활동 현황(프로젝트 건수, 누적 거래금액, 리뷰 수·평균 별점)을 반환합니다.")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> findMember(@PathVariable Long accountId) {
        MemberDetailResponse member = memberAdminService.findDetail(accountId);
        return ResponseEntity.ok(ApiResponse.success("MEMBER_FOUND", "조회에 성공했습니다.", member));
    }

    @PatchMapping("/{accountId}/suspension")
    @Operation(summary = "[관리자] 회원 정지",
            description = "새 로그인이 막히고, 이미 로그인된 세션도 즉시 끊깁니다. "
                    + "백엔드와 공유하는 Redis 에 정지 마커를 만들고 세션 키를 지웁니다. "
                    + "관리자 계정과 탈퇴 회원은 대상이 될 수 없습니다.")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> suspendMember(
            @PathVariable Long accountId,
            @Valid @RequestBody MemberSuspendRequest request,
            @CurrentAdmin AdminPrincipal admin) {

        MemberDetailResponse member =
                memberAdminService.suspend(accountId, request.reason(), admin.getAdminId());

        return ResponseEntity.ok(ApiResponse.success("MEMBER_SUSPENDED", "회원을 정지했습니다.", member));
    }

    @DeleteMapping("/{accountId}/suspension")
    @Operation(summary = "[관리자] 회원 정지 해제",
            description = "정지 마커를 지우고 비밀번호 실패 횟수를 초기화합니다. 언제든 다시 정지할 수 있습니다.")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> releaseSuspension(
            @PathVariable Long accountId,
            @CurrentAdmin AdminPrincipal admin) {

        MemberDetailResponse member =
                memberAdminService.releaseSuspension(accountId, admin.getAdminId());

        return ResponseEntity.ok(ApiResponse.success("SUSPENSION_RELEASED", "정지를 해제했습니다.", member));
    }
}
