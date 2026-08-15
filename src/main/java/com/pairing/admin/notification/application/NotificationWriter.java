package com.pairing.admin.notification.application;

import com.pairing.admin.notification.infrastructure.client.PairingBackendNotificationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 관리자 작업 결과를 사용자 알림으로 남긴다.
 *
 * <p>알림 도메인은 백엔드 소관이라 이 서버는 "만들어 달라고 요청하는 것"만 한다. 조회·읽음·삭제는
 * 사용자가 백엔드에서 하고, 문구·링크 규칙도 백엔드와 맞춰야 한다.
 *
 * <p><b>DB 에 직접 넣지 않고 사용자 서버 API 를 부른다.</b> 행만 넣으면 실시간 push 가 안 나가기
 * 때문이다 — WebSocket 세션은 사용자 서버가 들고 있어서 이 프로세스는 그 세션에 아무것도 보낼 수
 * 없다. 실제로 1:1 문의 답변 알림만 사용자가 새로고침해야 보였다(2026-08-15 QA 확인).
 *
 * <p><b>알림 실패가 본 작업을 되돌리지 않는다.</b> 답변은 저장됐는데 알림 한 건 때문에 롤백되면
 * 관리자는 답변 버튼을 눌러도 아무 일이 안 일어나는 것처럼 보인다. 예외는 삼키고 로그만 남긴다.
 * (백엔드의 MatchingNotifier 와 같은 방식)
 *
 * <p>HTTP 를 타게 되면서 실패할 경우가 늘었다 — 사용자 서버 배포 중, 키 불일치, 네트워크.
 * 그래도 삼키는 판단은 그대로다. 알림이 빠지는 것보다 답변이 안 남는 게 훨씬 나쁘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWriter {

    private final PairingBackendNotificationClient notificationClient;

    /** 1:1 문의 답변 등록 -&gt; 문의 작성자에게. */
    public void notifyInquiryAnswered(Long writerAccountId, Long inquiryId, String inquiryTitle) {
        write(writerAccountId, "INQUIRY_ANSWERED", "문의하신 내용에 답변이 등록되었습니다.",
                inquiryTitle, "/support/inquiries/" + inquiryId);
    }

    private void write(Long ownerAccountId, String type, String title, String content, String linkUrl) {
        try {
            notificationClient.create(ownerAccountId, type, title, content, linkUrl);
        } catch (Exception e) {
            log.warn("[관리자 알림 발송 실패 - 무시하고 진행] ownerAccountId={}, type={}", ownerAccountId, type, e);
        }
    }
}
