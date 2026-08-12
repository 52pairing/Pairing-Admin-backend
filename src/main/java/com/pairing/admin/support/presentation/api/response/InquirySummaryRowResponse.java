package com.pairing.admin.support.presentation.api.response;

import com.pairing.admin.member.domain.Role;
import com.pairing.admin.support.domain.InquiryStatus;
import com.pairing.admin.support.infrastructure.persistence.InquiryJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 문의 목록 한 줄. 화면 표의 컬럼과 1:1 로 맞춘다.
 *
 * <p>본문과 답변 내용은 담지 않는다. 목록에서 안 보여주는데 내려주면 응답만 커진다.
 */
@Schema(description = "1:1 문의 목록 행")
public record InquirySummaryRowResponse(

        @Schema(description = "문의 ID (상세 조회에 사용)", example = "12")
        Long inquiryId,

        @Schema(description = "화면에 보이는 문의번호", example = "QNA-20260805-0012")
        String inquiryNo,

        @Schema(description = "작성자 회원유형", example = "CLIENT")
        Role writerRole,

        @Schema(description = "제목", example = "착수금 수수료 결제 문의")
        String title,

        @Schema(description = "작성자명", example = "오이랩")
        String writerName,

        @Schema(description = "작성일", example = "2026-08-05T14:20:00")
        LocalDateTime createdAt,

        @Schema(description = "PENDING(대기중) / ANSWERED(답변완료)", example = "ANSWERED")
        InquiryStatus status,

        @Schema(description = "답변 등록일. 미답변이면 null", example = "2026-08-06T10:05:00")
        LocalDateTime answeredAt
) {

    public static InquirySummaryRowResponse from(InquiryJpaEntity inquiry) {
        return new InquirySummaryRowResponse(
                inquiry.getId(),
                inquiry.getInquiryNo(),
                inquiry.getWriterRole(),
                inquiry.getTitle(),
                inquiry.getWriterName(),
                inquiry.getCreatedAt(),
                inquiry.getStatus(),
                inquiry.getAnsweredAt());
    }
}
