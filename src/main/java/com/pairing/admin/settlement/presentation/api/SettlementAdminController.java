package com.pairing.admin.settlement.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.settlement.application.SettlementAdminService;
import com.pairing.admin.settlement.domain.PayerRole;
import com.pairing.admin.settlement.domain.SettlementPhase;
import com.pairing.admin.settlement.domain.SettlementStatus;
import com.pairing.admin.settlement.presentation.api.response.SettlementDetailResponse;
import com.pairing.admin.settlement.presentation.api.response.SettlementStatsResponse;
import com.pairing.admin.settlement.presentation.api.response.SettlementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 거래·정산 관리. (관리자 &gt; 거래·정산 관리, 요구사항 R39)
 *
 * <p>화면은 세 조각이다 — 요약 카드({@code /summary}), 목록({@code /}),
 * 상세({@code /{settlementId}}).
 *
 * <p><b>조회만 있다.</b> 정산은 금액을 다루므로 조회와 변경의 권한을 나누는 편이 좋은데,
 * 지금은 변경 자체가 없어 별도 권한을 걸지 않았다. 결제·재청구를 열게 되면 그 메서드에만
 * {@code @PreAuthorize("hasAuthority('SETTLEMENT_WRITE')")} 를 걸고
 * {@code AdminPrincipal.getAuthorities()} 에서 권한을 여러 개 돌려주면 된다.
 */
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
@Tag(name = "40. Settlement", description = "거래·정산 관리 API")
public class SettlementAdminController {

    private final SettlementAdminService settlementAdminService;

    @GetMapping("/summary")
    @Operation(summary = "[관리자] 정산 요약 카드",
            description = "총 수수료 수익·이번달 수익·결제 예정·미납·결제 실패·위약금 수수료를 반환합니다. "
                    + "모든 금액은 수수료 기준이며 기준금액(거래 규모)이 아닙니다. "
                    + "취소된 정산은 어느 카드에도 포함되지 않습니다. "
                    + "위약금은 백엔드에 Penalty 도메인이 아직 없어 현재 항상 0 입니다.")
    public ResponseEntity<ApiResponse<SettlementStatsResponse>> findStats() {
        SettlementStatsResponse stats = settlementAdminService.findStats();
        return ResponseEntity.ok(
                ApiResponse.success("SETTLEMENT_STATS_FOUND", "조회에 성공했습니다.", stats));
    }

    @GetMapping
    @Operation(summary = "[관리자] 정산 목록 조회",
            description = "상태·유형·납부자 구분·키워드·생성일 기간으로 검색합니다. 조건은 비우면 전체입니다. "
                    + "정렬은 최근 생성순으로 고정이며 sort 파라미터는 무시합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SettlementSummaryResponse>>> findSettlements(

            @Parameter(description = "상태 필터. PENDING 이 화면의 \"결제 가능\"에 해당합니다.",
                    example = "OVERDUE")
            @RequestParam(required = false) SettlementStatus status,

            @Parameter(description = "수수료 유형 필터. SUCCESS_FEE 가 화면의 \"완료금 수수료\"입니다.",
                    example = "DEPOSIT")
            @RequestParam(required = false) SettlementPhase phase,

            @Parameter(description = "납부자 구분 필터", example = "CLIENT")
            @RequestParam(required = false) PayerRole payerRole,

            @Parameter(description = "정산번호·프로젝트명·회원명·회사명 부분 검색어",
                    example = "ST-2026")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "정산 생성일 시작(포함). yyyy-MM-dd", example = "2026-08-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "정산 생성일 종료(당일 포함). yyyy-MM-dd", example = "2026-08-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<SettlementSummaryResponse> settlements = settlementAdminService
                .search(status, phase, payerRole, keyword, fromDate, toDate, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("SETTLEMENT_LIST_FOUND", "조회에 성공했습니다.", settlements));
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "[관리자] 정산 상세 조회",
            description = "목록 값에 더해 결제 승인번호·실패 사유·미납 사유와 연결된 프로젝트·계약 정보를 반환합니다.")
    public ResponseEntity<ApiResponse<SettlementDetailResponse>> findSettlement(
            @PathVariable Long settlementId) {

        SettlementDetailResponse settlement = settlementAdminService.findDetail(settlementId);
        return ResponseEntity.ok(
                ApiResponse.success("SETTLEMENT_FOUND", "조회에 성공했습니다.", settlement));
    }
}
