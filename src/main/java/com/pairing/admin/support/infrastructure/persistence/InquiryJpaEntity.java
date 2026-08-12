package com.pairing.admin.support.infrastructure.persistence;

import com.pairing.admin.member.domain.Role;
import com.pairing.admin.support.domain.InquiryStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * inquiry 테이블 매핑. <b>백엔드와 공유하는 테이블</b>이다.
 *
 * <p>이 서버는 문의를 <b>만들지 않는다.</b> 접수는 사용자가 백엔드로 하고, 여기서는 조회와 답변만 한다.
 *
 * <p>{@code writerName}/{@code writerRole}/{@code writerEmail} 은 백엔드가 접수 시점에 복사해 둔
 * 스냅샷이다. 덕분에 관리자 목록 검색을 account 조인 없이 이 테이블만으로 처리할 수 있다.
 *
 * <p>세터를 열지 않는다. 상태를 바꾸는 경로는 {@link #answer(String)} 하나뿐이어야
 * "답변은 있는데 상태가 PENDING" 같은 어긋난 행이 생기지 않는다.
 */
@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryJpaEntity {

    /** 화면에 보이는 문의번호 형식. 백엔드와 같은 규칙이어야 사용자와 관리자가 같은 번호를 본다. */
    private static final DateTimeFormatter INQUIRY_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "writer_account_id", nullable = false)
    private Long writerAccountId;

    @Column(name = "writer_name", nullable = false, length = 100)
    private String writerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_role", nullable = false, length = 20)
    private Role writerRole;

    @Column(name = "writer_email", length = 100)
    private String writerEmail;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /**
     * 첨부파일 id 목록. inquiry_file 테이블에 순서까지 저장된다.
     *
     * <p>파일 이름·경로는 여기 없다. file 테이블에서 따로 읽어야 한다
     * ({@link InquiryJpaRepository#findAttachments}).
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "inquiry_file", joinColumns = @JoinColumn(name = "inquiry_id"))
    @Column(name = "file_id")
    @OrderColumn(name = "sort_order")
    private List<Long> fileIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InquiryStatus status;

    @Column(name = "answer", length = 2000)
    private String answer;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 화면·검색에 쓰는 문의번호. 컬럼이 아니라 id·작성일로 만든다. (예: QNA-20260805-0012) */
    public String getInquiryNo() {
        return "QNA-" + createdAt.toLocalDate().format(INQUIRY_NO_DATE_FORMAT) + "-" + String.format("%04d", id);
    }

    /**
     * 답변을 등록한다. 이미 답변한 문의도 다시 답변(수정)할 수 있다.
     *
     * <p>답변 등록일은 그때마다 갱신된다. 화면의 "답변등록일"은 마지막 답변 시각이다.
     */
    public void answer(String answer) {
        this.answer = answer;
        this.status = InquiryStatus.ANSWERED;
        this.answeredAt = LocalDateTime.now();
    }
}
