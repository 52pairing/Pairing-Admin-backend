package com.pairing.admin.support.infrastructure.persistence;

import com.pairing.admin.support.domain.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InquiryJpaRepository
        extends JpaRepository<InquiryJpaEntity, Long>, JpaSpecificationExecutor<InquiryJpaEntity> {

    long countByStatus(InquiryStatus status);

    /** 오늘 접수 건수. 자정 이후 생성된 것을 센다. */
    long countByCreatedAtAfter(LocalDateTime from);

    /**
     * 첨부파일 메타. {@code file} 은 다른 도메인 테이블이라 엔티티로 매핑하지 않고 필요한 컬럼만 읽는다.
     *
     * <p>문의 상세를 열 때마다 파일 수만큼 조회가 늘어나지 않도록 id 목록을 한 번에 받는다.
     * 반환 순서는 보장하지 않으므로, 화면 순서는 호출부가 {@code fileIds} 기준으로 다시 맞춘다.
     *
     * @return {@code [fileId, originalName, objectKey]} 배열들
     */
    @Query(value = """
            SELECT f.id, f.original_name, f.object_key
            FROM file f
            WHERE f.id IN (:fileIds)
            """, nativeQuery = true)
    List<Object[]> findAttachments(@Param("fileIds") Collection<Long> fileIds);
}
