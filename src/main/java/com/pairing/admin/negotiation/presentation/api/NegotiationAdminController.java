package com.pairing.admin.negotiation.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.negotiation.application.NegotiationAdminService;
import com.pairing.admin.negotiation.domain.NegotiationStatus;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationDetailResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationListItemResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationSummaryResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationTokenUsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Agent 관리. (관리자 &gt; AI Agent 관리)
 *
 * <p>조회만 있다. 협상 진행·조건 확정은 백엔드의 협상 루프가 하고 이 서버는 결과를 볼 뿐이다.
 */
@RestController
@RequestMapping("/api/v1/admin/negotiations")
@RequiredArgsConstructor
@Tag(name = "60. AI Agent", description = "AI 협상 관리 API")
public class NegotiationAdminController {

    private final NegotiationAdminService negotiationAdminService;

    @GetMapping("/summary")
    @Operation(summary = "[관리자] 협상 요약",
            description = "목록 화면 상단 카드입니다. 전체·진행중·타결·결렬 건수와 평균 라운드/소요일수를 반환합니다.")
    public ResponseEntity<ApiResponse<NegotiationSummaryResponse>> findSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                "NEGOTIATION_SUMMARY_FOUND", "조회에 성공했습니다.",
                negotiationAdminService.getSummary()));
    }

    @GetMapping
    @Operation(summary = "[관리자] 협상 목록",
            description = "프로젝트명·클라이언트명·프리랜서명으로 검색하고 상태로 필터링합니다. 조건은 비우면 전체입니다.")
    public ResponseEntity<ApiResponse<PageResponse<NegotiationListItemResponse>>> findNegotiations(

            @Parameter(description = "상태 필터", example = "AGREED")
            @RequestParam(required = false) NegotiationStatus status,

            @Parameter(description = "프로젝트명·클라이언트명·프리랜서명 부분 검색어", example = "로봇개")
            @RequestParam(required = false) String keyword,

            // 정렬은 시작일 역순으로 서버가 고정한다(리포지토리 주석 참고). size 만 화면이 정한다.
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                "NEGOTIATION_LIST_FOUND", "조회에 성공했습니다.",
                negotiationAdminService.search(status, keyword, pageable)));
    }

    @GetMapping("/{negotiationId}")
    @Operation(summary = "[관리자] 협상 상세",
            description = "기본 정보와 최종 협상 결과, 라운드별 대화를 반환합니다. 화면의 '협상 로그' 탭입니다. "
                    + "코드값에는 라벨이 함께 내려갑니다(예: ONSITE → 상주).")
    public ResponseEntity<ApiResponse<NegotiationDetailResponse>> findNegotiation(
            @PathVariable Long negotiationId) {

        return ResponseEntity.ok(ApiResponse.success(
                "NEGOTIATION_FOUND", "조회에 성공했습니다.",
                negotiationAdminService.getDetail(negotiationId)));
    }

    @GetMapping("/{negotiationId}/token-usage")
    @Operation(summary = "[관리자] 협상 토큰 사용량",
            description = "화면의 '토큰 사용량' 탭입니다. 협상 1건이 일으킨 모델 호출을 "
                    + "토큰·응답시간·재시도와 함께 돌려줍니다. 대리인이 아직 돌지 않았으면 "
                    + "집계는 0, 목록은 빈 배열입니다. "
                    + "**비용 필드는 없습니다** — 모델 단가를 코드에 박지 않기로 해 원본이 비어 있습니다. "
                    + "응답시간은 모델 호출 구간이며 사용자가 체감하는 총 왕복 시간이 아닙니다. "
                    + "시각은 KST 입니다.")
    public ResponseEntity<ApiResponse<NegotiationTokenUsageResponse>> findTokenUsage(
            @PathVariable Long negotiationId) {

        return ResponseEntity.ok(ApiResponse.success(
                "NEGOTIATION_TOKEN_USAGE_FOUND", "조회에 성공했습니다.",
                negotiationAdminService.getTokenUsage(negotiationId)));
    }
}
