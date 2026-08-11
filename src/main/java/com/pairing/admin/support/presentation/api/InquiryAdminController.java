package com.pairing.admin.support.presentation.api;

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
 * 1:1 문의 관리. (관리자 &gt; 고객지원) — <b>스켈레톤</b>
 *
 * <p>inquiry 테이블의 status 는 PENDING / ANSWERED 두 가지다. (스키마 v12 이후)
 * 답변 등록 시 알림 발송이 필요하면, 알림은 백엔드가 담당하므로
 * 관리자 서버는 notification 테이블에 행을 넣거나 백엔드 내부 API를 호출하는 방식 중 하나를 고른다.
 */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@Tag(name = "50. Inquiry", description = "1:1 문의 관리 API (스켈레톤)")
public class InquiryAdminController {

    @GetMapping
    @Operation(summary = "[관리자] 문의 목록 조회", description = "아직 구현되지 않았습니다. 빈 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<PageResponse<Object>>> findInquiries() {
        // TODO: InquiryAdminService.search(...) 로 교체
        PageResponse<Object> empty = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_LIST_FOUND", "조회에 성공했습니다.", empty));
    }
}
