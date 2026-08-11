package com.pairing.admin.settlement.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정산 관리. (관리자 &gt; 정산 관리) — <b>스켈레톤</b>
 *
 * <p>정산은 금액을 다루므로 조회와 변경의 권한을 나누는 편이 좋다.
 * 메서드에 {@code @PreAuthorize("hasAuthority('SETTLEMENT_WRITE')")} 같은 조건을 걸고,
 * {@code AdminPrincipal.getAuthorities()} 에서 권한을 여러 개 돌려주면 된다.
 */
@RestController
@RequestMapping("/api/v1/admin/settlements")
@Tag(name = "40. Settlement", description = "정산 관리 API (스켈레톤)")
public class SettlementAdminController {

    @GetMapping
    @Operation(summary = "[관리자] 정산 목록 조회", description = "아직 구현되지 않았습니다. 빈 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<PageResponse<Object>>> findSettlements() {
        // TODO: SettlementAdminService.search(...) 로 교체
        PageResponse<Object> empty = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_LIST_FOUND", "조회에 성공했습니다.", empty));
    }
}
