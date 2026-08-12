package com.pairing.admin.support.presentation.api.response;

import com.pairing.admin.member.domain.Role;
import com.pairing.admin.support.domain.InquiryStatus;
import com.pairing.admin.support.infrastructure.persistence.InquiryJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 문의 상세. 관리자는 작성자 정보(이름·유형·이메일)까지 본다. */
@Schema(description = "1:1 문의 상세")
public record InquiryDetailResponse(

        @Schema(description = "문의 ID", example = "12")
        Long inquiryId,

        @Schema(description = "화면에 보이는 문의번호", example = "QNA-20260805-0012")
        String inquiryNo,

        @Schema(description = "작성자 계정 ID", example = "301")
        Long writerAccountId,

        @Schema(description = "작성자명", example = "오이랩")
        String writerName,

        @Schema(description = "작성자 회원유형", example = "CLIENT")
        Role writerRole,

        @Schema(description = "작성자 이메일. 답변 안내 메일을 보낼 때 쓴다.", example = "contact@oilab.kr")
        String writerEmail,

        @Schema(description = "제목", example = "착수금 수수료 결제 문의")
        String title,

        @Schema(description = "문의 내용", example = "착수금 수수료 결제 버튼이 활성화되지 않습니다.")
        String content,

        @Schema(description = "첨부파일. 없으면 빈 배열")
        List<InquiryAttachmentResponse> files,

        @Schema(description = "PENDING(대기중) / ANSWERED(답변완료)", example = "ANSWERED")
        InquiryStatus status,

        @Schema(description = "답변 내용. 미답변이면 null")
        String answer,

        @Schema(description = "답변자 표시명. 미답변이면 null", example = "페어링 고객지원")
        String answererName,

        @Schema(description = "답변 등록일. 미답변이면 null", example = "2026-08-06T10:05:00")
        LocalDateTime answeredAt,

        @Schema(description = "작성일", example = "2026-08-05T14:20:00")
        LocalDateTime createdAt
) {

    /** 답변자는 개별 관리자 이름을 노출하지 않는다. 사용자 화면과 같은 표시명을 쓴다. */
    private static final String ANSWERER_NAME = "페어링 고객지원";

    public static InquiryDetailResponse from(InquiryJpaEntity inquiry, List<InquiryAttachmentResponse> files) {
        return new InquiryDetailResponse(
                inquiry.getId(),
                inquiry.getInquiryNo(),
                inquiry.getWriterAccountId(),
                inquiry.getWriterName(),
                inquiry.getWriterRole(),
                inquiry.getWriterEmail(),
                inquiry.getTitle(),
                inquiry.getContent(),
                files,
                inquiry.getStatus(),
                inquiry.getAnswer(),
                inquiry.getStatus() == InquiryStatus.ANSWERED ? ANSWERER_NAME : null,
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt());
    }
}
