package com.pairing.admin.review.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.security.AdminPrincipal;
import com.pairing.admin.global.security.CurrentAdmin;
import com.pairing.admin.review.application.SiteReviewAdminService;
import com.pairing.admin.review.domain.PartyRole;
import com.pairing.admin.review.presentation.api.request.SiteReviewPromotionRequest;
import com.pairing.admin.review.presentation.api.response.SiteReviewRowResponse;
import com.pairing.admin.review.presentation.api.response.SiteReviewSummaryResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 리뷰 관리. (관리자 &gt; 사이트 리뷰 관리)
 *
 * <p>후기 작성·삭제 API 는 없다. 홍보 활용 여부만 바꾼다.
 */
@RestController
@RequestMapping("/api/v1/admin/site-reviews")
@RequiredArgsConstructor
@Tag(name = "51. Site Review", description = "사이트 리뷰 관리 API")
public class SiteReviewAdminController {

    private final SiteReviewAdminService siteReviewAdminService;

    @GetMapping("/summary")
    @Operation(summary = "[관리자] 사이트 리뷰 요약",
            description = "요약 카드 5개와 별점 분포 그래프에 쓰는 값입니다. 필터와 무관한 전체 기준입니다.")
    public ResponseEntity<ApiResponse<SiteReviewSummaryResponse>> findSummary() {
        SiteReviewSummaryResponse data = siteReviewAdminService.getSummary();
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_SUMMARY_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping
    @Operation(summary = "[관리자] 사이트 리뷰 목록 조회",
            description = "별점·작성자 구분·홍보 여부로 필터링합니다. "
                    + "keyword 는 회원명·후기 내용·프로젝트명을 한 번에 검색합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SiteReviewRowResponse>>> findSiteReviews(

            @Parameter(description = "별점 필터 1~5", example = "5")
            @RequestParam(required = false) Integer score,

            @Parameter(description = "작성자 구분 필터", example = "CLIENT")
            @RequestParam(required = false) PartyRole writerRole,

            @Parameter(description = "홍보 활용 여부 필터. 비우면 전체", example = "true")
            @RequestParam(required = false) Boolean promoted,

            @Parameter(description = "회원명·후기 내용·프로젝트명 부분 검색어", example = "삼성전자")
            @RequestParam(required = false) String keyword,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<SiteReviewRowResponse> data =
                siteReviewAdminService.search(score, writerRole, promoted, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEWS_FOUND", "조회에 성공했습니다.", data));
    }

    @PutMapping("/{siteReviewId}/promotion")
    @Operation(summary = "[관리자] 사이트 리뷰 홍보 활용 설정",
            description = "켜면 비로그인 메인 노출 후보가 되고, 끄면 사용자에게 보이지 않습니다. "
                    + "공개/비공개 설정은 없앴습니다 — 사용자는 홍보로 고른 후기만 봅니다.")
    public ResponseEntity<ApiResponse<SiteReviewRowResponse>> updatePromotion(
            @PathVariable Long siteReviewId,
            @Valid @RequestBody SiteReviewPromotionRequest request,
            @CurrentAdmin AdminPrincipal admin) {

        SiteReviewRowResponse data = siteReviewAdminService.updatePromotion(
                siteReviewId, request.promoted(), admin.getAdminId());
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_UPDATED", "설정을 변경했습니다.", data));
    }
}
