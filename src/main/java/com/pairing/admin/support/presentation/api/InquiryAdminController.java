package com.pairing.admin.support.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.security.AdminPrincipal;
import com.pairing.admin.global.security.CurrentAdmin;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.support.application.InquiryAdminService;
import com.pairing.admin.support.domain.InquiryStatus;
import com.pairing.admin.support.presentation.api.request.InquiryAnswerRequest;
import com.pairing.admin.support.presentation.api.response.InquiryDetailResponse;
import com.pairing.admin.support.presentation.api.response.InquirySummaryResponse;
import com.pairing.admin.support.presentation.api.response.InquirySummaryRowResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1:1 문의 관리. (관리자 &gt; 1:1 문의 관리)
 *
 * <p>접수는 사용자가 백엔드로 한다. 이 서버에 문의 생성 API 는 없다.
 */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
@Tag(name = "50. Inquiry", description = "1:1 문의 관리 API")
public class InquiryAdminController {

    private final InquiryAdminService inquiryAdminService;

    @GetMapping("/summary")
    @Operation(summary = "[관리자] 문의 요약",
            description = "목록 상단 요약 카드입니다. 전체 / 답변 대기 / 답변 완료 / 오늘 접수 네 값을 내려줍니다.")
    public ResponseEntity<ApiResponse<InquirySummaryResponse>> findSummary() {
        InquirySummaryResponse data = inquiryAdminService.getSummary();
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_SUMMARY_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping
    @Operation(summary = "[관리자] 문의 목록 조회",
            description = "keyword 는 회원명·제목·문의번호를 한 번에 검색합니다. 정렬은 최신순 기본입니다.")
    public ResponseEntity<ApiResponse<PageResponse<InquirySummaryRowResponse>>> findInquiries(

            @Parameter(description = "회원명·제목·문의번호 부분 검색어", example = "착수금")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "작성자 회원유형 필터", example = "CLIENT")
            @RequestParam(required = false) Role writerRole,

            @Parameter(description = "상태 필터", example = "PENDING")
            @RequestParam(required = false) InquiryStatus status,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<InquirySummaryRowResponse> data =
                inquiryAdminService.search(keyword, writerRole, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("INQUIRIES_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/{inquiryId}")
    @Operation(summary = "[관리자] 문의 상세 조회",
            description = "작성자 정보(이름·유형·이메일)와 첨부파일까지 함께 내려줍니다.")
    public ResponseEntity<ApiResponse<InquiryDetailResponse>> findInquiry(@PathVariable Long inquiryId) {
        InquiryDetailResponse data = inquiryAdminService.findDetail(inquiryId);
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_FOUND", "조회에 성공했습니다.", data));
    }

    @PostMapping("/{inquiryId}/answer")
    @Operation(summary = "[관리자] 문의 답변 등록",
            description = "상태가 ANSWERED 로 바뀌고 작성자에게 알림이 남습니다. "
                    + "이미 답변한 문의에 다시 호출하면 답변이 교체되고 알림이 다시 발송됩니다.")
    public ResponseEntity<ApiResponse<InquiryDetailResponse>> answerInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request,
            @CurrentAdmin AdminPrincipal admin) {

        InquiryDetailResponse data = inquiryAdminService.answer(inquiryId, request.answer(), admin.getAdminId());
        return ResponseEntity.ok(ApiResponse.success("INQUIRY_ANSWERED", "답변을 등록했습니다.", data));
    }
}
