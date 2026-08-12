package com.pairing.admin.negotiation.presentation.api.response;

import com.pairing.admin.negotiation.domain.ConditionType;
import com.pairing.admin.negotiation.domain.NegotiationMessageType;
import com.pairing.admin.negotiation.domain.NegotiationStatus;
import com.pairing.admin.negotiation.domain.SenderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 협상 상세 — "협상 로그" 탭.
 *
 * <p>당사자 화면과 달리 <b>양측 대리인의 발언을 모두</b> 보여 준다. 다만 마지노선은 담지 않는다
 * (조건 엔티티에서 아예 매핑하지 않았다).
 *
 * <p>모든 코드값에 {@code ~Label} 을 함께 내려보낸다. 화면이 코드→한글 표를 다시 두면 백엔드에
 * enum 이 하나 늘 때마다 양쪽을 고쳐야 하고, 빠뜨리면 관리자 화면에 {@code ONSITE} 같은 값이
 * 그대로 노출된다.
 */
@Schema(description = "협상 상세(협상 로그)")
public record NegotiationDetailResponse(

        @Schema(description = "협상 ID", example = "10")
        Long negotiationId,

        @Schema(description = "화면 표시용 협상번호. 저장된 값이 아니라 ID·시작연도로 만든 표기다",
                example = "NEG-2026-010")
        String negotiationNo,

        @Schema(description = "프로젝트 ID", example = "18")
        Long projectId,

        @Schema(description = "프로젝트명", example = "로봇개 프로그램 개발")
        String projectTitle,

        @Schema(description = "클라이언트명", example = "유어커피")
        String clientName,

        @Schema(description = "프리랜서명", example = "김개발3")
        String freelancerName,

        @Schema(description = "상태 코드", example = "AGREED")
        NegotiationStatus status,

        @Schema(description = "상태 라벨", example = "타결")
        String statusLabel,

        @Schema(description = "시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각. 진행 중이면 null")
        LocalDateTime endedAt,

        @Schema(description = "총 협상 횟수(라운드)", example = "5")
        int totalRound,

        @Schema(description = "최종 협상 결과. 합의된 조건만 채워지고 나머지는 null")
        FinalResult finalResult,

        @Schema(description = "라운드별 대화")
        List<RoundLog> rounds
) {

    /**
     * 최종 협상 결과 카드.
     *
     * <p>합의된 조건만 값이 있다. 결렬되거나 진행 중이면 전부 null 이고, <b>일부만 합의된 상태도
     * 정상</b>이다 — 접점이 없는 쟁점은 미합의로 남겨 사람에게 넘기는 게 설계된 동작이다.
     *
     * <p>와이어 초안의 "작업 범위" 칸은 뺐다. 협상 쟁점이 아니라 프로젝트에 이미 적힌 값이라
     * 항상 비어 있었다.
     */
    @Schema(description = "최종 협상 결과")
    public record FinalResult(

            @Schema(description = "최종 금액(월 단가)", example = "월 3,900,000원")
            String amountLabel,

            @Schema(description = "계약 기간", example = "5개월")
            String periodLabel,

            @Schema(description = "시작일", example = "2026-09-01")
            String startDateLabel,

            @Schema(description = "근무 방식", example = "상주")
            String workStyleLabel,

            @Schema(description = "근무 형태", example = "풀타임")
            String workFormLabel
    ) {
    }

    /**
     * 라운드 1개.
     *
     * <p>와이어 초안은 "1라운드 = 제안 1건"을 가정했는데, 실제로는 한 라운드에 <b>쟁점별로 여러
     * 턴</b>이 오간다(조건 5개면 10건 넘게 나온다). 그래서 아코디언 안을 대화 표로 바꿨다.
     */
    @Schema(description = "라운드별 대화")
    public record RoundLog(

            @Schema(description = "라운드 번호. 0은 마지노선 입력 등 협상 시작 전 안내", example = "1")
            int roundNo,

            @Schema(description = "이 라운드의 발언 수", example = "10")
            int messageCount,

            @Schema(description = "이 라운드 첫 발언 시각")
            LocalDateTime startedAt,

            @Schema(description = "대화")
            List<Message> messages
    ) {
    }

    /** 대화 한 줄. */
    @Schema(description = "대화 한 줄")
    public record Message(

            @Schema(description = "시각")
            LocalDateTime sentAt,

            @Schema(description = "발신 주체 코드", example = "FREELANCER_AGENT")
            SenderType senderType,

            @Schema(description = "발신 주체 라벨", example = "프리랜서 AI")
            String senderLabel,

            @Schema(description = "대리인 발언 여부. 사람이 개입한 지점을 구분한다", example = "true")
            boolean byAgent,

            @Schema(description = "쟁점 코드. 시스템 안내는 null", example = "AMOUNT")
            ConditionType conditionType,

            @Schema(description = "쟁점 라벨. 시스템 안내는 null", example = "단가(월)")
            String conditionLabel,

            @Schema(description = "발언 종류 코드", example = "PROPOSAL")
            NegotiationMessageType messageType,

            @Schema(description = "발언 종류 라벨", example = "제안")
            String messageTypeLabel,

            @Schema(description = "제시값 원문", example = "3900000")
            String proposedValue,

            @Schema(description = "제시값 표기", example = "월 3,900,000원")
            String proposedValueLabel,

            @Schema(description = "발언 내용")
            String content,

            @Schema(description = "제안 근거")
            String reason,

            @Schema(description = "사람이 누른 응답. 대리인 발언은 null", example = "ACCEPT")
            String response,

            @Schema(description = "응답 라벨", example = "수락")
            String responseLabel
    ) {
    }
}
