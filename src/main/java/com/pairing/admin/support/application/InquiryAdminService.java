package com.pairing.admin.support.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.global.infrastructure.s3.S3Settings;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.notification.application.NotificationWriter;
import com.pairing.admin.support.domain.InquiryStatus;
import com.pairing.admin.support.exception.InquiryErrorCode;
import com.pairing.admin.support.infrastructure.persistence.InquiryJpaEntity;
import com.pairing.admin.support.infrastructure.persistence.InquiryJpaRepository;
import com.pairing.admin.support.infrastructure.persistence.InquirySpecs;
import com.pairing.admin.support.presentation.api.response.InquiryAttachmentResponse;
import com.pairing.admin.support.presentation.api.response.InquiryDetailResponse;
import com.pairing.admin.support.presentation.api.response.InquirySummaryResponse;
import com.pairing.admin.support.presentation.api.response.InquirySummaryRowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 1:1 문의 관리. (관리자 &gt; 1:1 문의 관리)
 *
 * <p>백엔드와 같은 inquiry 테이블을 쓴다. 문의 <b>접수는 하지 않는다</b> — 사용자가 백엔드로 접수하고,
 * 이 서버는 조회와 답변만 담당한다.
 *
 * <p>답변을 등록하면 작성자에게 알림이 남는다. 알림 실패는 답변을 되돌리지 않는다
 * ({@link NotificationWriter} 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAdminService {

    private final InquiryJpaRepository inquiryJpaRepository;
    private final NotificationWriter notificationWriter;
    private final S3Settings s3Settings;

    /** 목록 상단 요약 카드. 네 값 모두 필터와 무관한 전체 기준이다. */
    @Transactional(readOnly = true)
    public InquirySummaryResponse getSummary() {
        return new InquirySummaryResponse(
                inquiryJpaRepository.count(),
                inquiryJpaRepository.countByStatus(InquiryStatus.PENDING),
                inquiryJpaRepository.countByStatus(InquiryStatus.ANSWERED),
                inquiryJpaRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay()));
    }

    /** 회원유형·상태로 걸러내고, keyword 는 회원명·제목·문의번호를 한 번에 찾는다. */
    @Transactional(readOnly = true)
    public PageResponse<InquirySummaryRowResponse> search(String keyword, Role writerRole,
                                                          InquiryStatus status, Pageable pageable) {

        Specification<InquiryJpaEntity> spec = Specification.allOf(
                InquirySpecs.writerRoleEquals(writerRole),
                InquirySpecs.statusEquals(status),
                InquirySpecs.keywordContains(keyword));

        Page<InquiryJpaEntity> page = inquiryJpaRepository.findAll(spec, pageable);

        return PageResponse.from(page, InquirySummaryRowResponse::from);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponse findDetail(Long inquiryId) {
        InquiryJpaEntity inquiry = loadInquiry(inquiryId);
        return InquiryDetailResponse.from(inquiry, loadAttachments(inquiry));
    }

    /**
     * 답변을 등록한다. 이미 답변한 문의도 다시 답변(수정)할 수 있고, 그때도 알림이 다시 나간다.
     *
     * <p>답변 수정 이력은 남기지 않는다. 필요해지면 별도 이력 테이블을 두는 쪽이 맞다 —
     * 답변 컬럼을 배열로 바꾸면 사용자 화면까지 영향을 받는다.
     */
    @Transactional
    public InquiryDetailResponse answer(Long inquiryId, String answer, Long actorAdminId) {
        InquiryJpaEntity inquiry = loadInquiry(inquiryId);

        if (answer == null || answer.isBlank()) {
            throw new BusinessException(InquiryErrorCode.INVALID_ANSWER);
        }

        inquiry.answer(answer);

        notificationWriter.notifyInquiryAnswered(
                inquiry.getWriterAccountId(), inquiry.getId(), inquiry.getTitle());

        // 누가 어떤 문의에 답했는지 남긴다. 감사 로그 테이블이 생기면 이 자리에 기록을 추가한다.
        log.info("[문의 답변] inquiryId={}, actorAdminId={}", inquiryId, actorAdminId);

        return InquiryDetailResponse.from(inquiry, loadAttachments(inquiry));
    }

    // ------------------------------------------------------------------

    private InquiryJpaEntity loadInquiry(Long inquiryId) {
        return inquiryJpaRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND));
    }

    /**
     * 첨부파일 메타를 채운다.
     *
     * <p>파일이 나중에 삭제됐어도 문의 조회 자체는 막지 않는다. 지워진 파일은 목록에서 빠진다.
     * 화면 순서는 {@code fileIds} 기준으로 맞춘다 — DB 조회 결과 순서는 보장되지 않는다.
     */
    private List<InquiryAttachmentResponse> loadAttachments(InquiryJpaEntity inquiry) {
        List<Long> fileIds = inquiry.getFileIds();
        if (fileIds.isEmpty()) {
            return List.of();
        }

        Map<Long, InquiryAttachmentResponse> byId = inquiryJpaRepository.findAttachments(fileIds).stream()
                .map(row -> new InquiryAttachmentResponse(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        s3Settings.toUrl((String) row[2])))
                .collect(Collectors.toMap(InquiryAttachmentResponse::fileId, Function.identity(),
                        (left, right) -> left));

        return fileIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }
}
