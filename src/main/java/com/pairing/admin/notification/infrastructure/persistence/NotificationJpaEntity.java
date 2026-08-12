package com.pairing.admin.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * notification 테이블 매핑. <b>백엔드와 공유하는 테이블</b>이다.
 *
 * <p>관리자 서버가 행을 넣으면 사용자는 알림 목록에서 그대로 볼 수 있다. 다만
 * <b>실시간 push(STOMP)는 나가지 않는다.</b> WebSocket 세션을 들고 있는 쪽이 백엔드라서,
 * 이 서버에서 넣은 행은 사용자가 새로고침하거나 알림을 다시 조회할 때 보인다.
 * 실시간까지 필요해지면 백엔드에 관리자 서버용 내부 API 를 두고 그쪽을 호출하는 방식으로 바꾼다.
 *
 * <p>{@code type} 은 문자열로 둔다. 백엔드의 NotificationType 은 12종이고 계속 늘어나는데,
 * 관리자 서버가 쓰는 값은 INQUIRY_ANSWERED 하나다. enum 을 복제하면 백엔드에서 값이 추가될 때마다
 * 두 곳을 맞춰야 하고, 빠뜨리면 DB CHECK 제약에서 걸려 500 이 난다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "link_url", length = 255)
    private String linkUrl;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private NotificationJpaEntity(Long ownerAccountId, String type, String title, String content, String linkUrl) {
        this.ownerAccountId = ownerAccountId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    /** 방금 만든 알림은 당연히 안 읽음이다. 읽음 처리는 사용자가 백엔드에서 한다. */
    public static NotificationJpaEntity create(Long ownerAccountId, String type, String title,
                                               String content, String linkUrl) {
        return new NotificationJpaEntity(ownerAccountId, type, title, content, linkUrl);
    }
}
