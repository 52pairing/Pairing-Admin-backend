package com.pairing.admin.negotiation.presentation.api.response;

import com.pairing.admin.negotiation.domain.AgentType;
import com.pairing.admin.negotiation.domain.AiCallStatus;
import com.pairing.admin.negotiation.infrastructure.persistence.AiAgentLogAdminRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AI 협상 상세 — "토큰 사용량" 탭.
 *
 * <p>협상 1건이 모델을 몇 번 부르고 토큰을 얼마나 썼는지 본다. 협상은 <b>라운드마다 모델을
 * 다시 부르는</b> 구조라 한 건이 쌓는 호출이 적지 않고, 여기서만 보이는 게 있다 — 예를 들어
 * 재시도({@code retryCount})가 계속 올라가면 프롬프트나 모델 쪽에 문제가 생긴 것이다.
 *
 * <p><b>비용 칸은 없다.</b> {@code ai_agent_log.cost_amount} 가 전부 null 이기 때문이고,
 * 그건 버그가 아니라 판단이다 — 모델 단가를 코드에 박아 두면 구글이 단가를 바꾼 뒤부터
 * <b>조용히 틀린 금액</b>이 쌓인다. 토큰 수는 남아 있으니 단가가 정해지면 그때 역산하면 된다.
 *
 * <p>모든 시각은 <b>KST</b> 다. 원본은 UTC 인데 협상 로그 탭과 나란히 보려고 서버에서 맞춰 둔다.
 */
@Schema(description = "협상 토큰 사용량")
public record NegotiationTokenUsageResponse(

        @Schema(description = "협상 ID", example = "28")
        Long negotiationId,

        @Schema(description = "상단 카드 집계")
        Usage usage,

        @Schema(description = "라운드별 사용량(라운드 오름차순). 막대 차트용")
        List<RoundUsage> byRound,

        @Schema(description = "모델 호출 목록(시간 오름차순). 대리인이 아직 안 돌았으면 빈 배열")
        List<Call> calls
) {

    public static NegotiationTokenUsageResponse from(Long negotiationId,
                                                     AiAgentLogAdminRepository.UsageRow usage,
                                                     List<AiAgentLogAdminRepository.CallRow> calls) {
        List<Call> mapped = calls.stream().map(Call::from).toList();
        return new NegotiationTokenUsageResponse(
                negotiationId,
                Usage.from(usage),
                RoundUsage.of(mapped),
                mapped);
    }

    /**
     * 탭 상단 카드.
     *
     * <p><b>{@code successCalls + failedCalls} 가 {@code totalCalls} 보다 작을 수 있다.</b>
     * 파이썬이 아는 값({@code SUCCESS}/{@code FAILED}) 말고 다른 상태를 남기면 어느 쪽에도
     * 안 잡히기 때문이다. 그 경우에도 전체 건수와 토큰 합계에는 들어간다 — <b>실제로 쓴 토큰을
     * 빠뜨리는 것보다 낫다.</b> 합이 안 맞는 게 보이면 그 자체가 "모르는 상태가 쌓이고 있다"는
     * 신호이고, 어느 호출인지는 아래 목록에서 결과 라벨이 "기타"인 줄로 찾을 수 있다.
     */
    @Schema(description = "사용량 집계")
    public record Usage(

            @Schema(description = "모델 호출 횟수", example = "12")
            long totalCalls,

            @Schema(description = "성공", example = "11")
            long successCalls,

            @Schema(description = "실패", example = "1")
            long failedCalls,

            @Schema(description = "입력 토큰 합계", example = "11604")
            long promptTokens,

            @Schema(description = "출력 토큰 합계", example = "7967")
            long outputTokens,

            @Schema(description = "총 토큰(입력+출력)", example = "19571")
            long totalTokens,

            @Schema(description = "재시도 합계. 계속 오르면 프롬프트·모델 쪽을 의심한다", example = "0")
            long retryCount,

            @Schema(description = "평균 응답시간(ms). 성공한 호출만 센다. 모델 호출 구간이며 총 왕복이 아니다",
                    example = "15574.0")
            double averageLatencyMs,

            @Schema(description = "최대 응답시간(ms). 실패까지 포함한 최악값", example = "17200")
            Integer maxLatencyMs,

            @Schema(description = "쓰인 모델(중복 제거)", example = "gemini-2.5-flash")
            String models
    ) {

        static Usage from(AiAgentLogAdminRepository.UsageRow row) {
            return new Usage(
                    row.getTotalCalls(),
                    row.getSuccessCalls(),
                    row.getFailedCalls(),
                    row.getPromptTokens(),
                    row.getOutputTokens(),
                    row.getPromptTokens() + row.getOutputTokens(),
                    row.getRetryCount(),
                    round1(row.getAverageLatencyMs()),
                    row.getMaxLatencyMs(),
                    row.getModels());
        }

        /**
         * 성공한 호출이 하나도 없으면 평균이 NULL 로 온다. 카드에 빈칸을 띄우는 대신 0 을 준다.
         *
         * <p>소수점은 첫째 자리에서 끊는다. 화면마다 반올림 규칙이 갈리면 같은 수치가 다르게
         * 보이므로 서버에서 자른다({@link NegotiationSummaryResponse} 와 같은 이유).
         */
        private static double round1(Double value) {
            if (value == null) {
                return 0d;
            }
            return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
    }

    /**
     * 라운드 1개의 사용량. "라운드별 사용량" 막대 차트가 이걸 그대로 쓴다.
     *
     * <p><b>라운드를 모르는 호출은 빠진다.</b> 라운드 번호는 별도 컬럼이 아니라 AI 서버가 보낸
     * 요청 본문에서 꺼내는 값이라, 안 실어 보낸 호출은 비어 있다. 그런 호출을 "0라운드" 같은
     * 가짜 묶음에 넣으면 차트에 없는 라운드가 생긴다. 대신 전체 합계({@link Usage})에는 들어가므로
     * <b>라운드 막대의 합이 상단 카드 합계보다 작을 수 있다</b> — 그 차이가 곧 "라운드를 모르는
     * 호출"의 양이다.
     */
    @Schema(description = "라운드별 사용량")
    public record RoundUsage(

            @Schema(description = "라운드 번호", example = "1")
            int roundNo,

            @Schema(description = "이 라운드의 모델 호출 수", example = "2")
            int calls,

            @Schema(description = "입력 토큰 합계", example = "2920")
            long promptTokens,

            @Schema(description = "출력 토큰 합계", example = "1580")
            long outputTokens,

            @Schema(description = "총 토큰(입력+출력)", example = "4500")
            long totalTokens
    ) {

        /**
         * 호출 목록을 라운드로 묶는다.
         *
         * <p>합계에서는 값이 없는 토큰을 0 으로 본다(SQL {@code SUM} 과 같은 규칙). 줄 단위
         * 표시에서 null 을 유지하는 것과 다른데, 합계는 "모르면 안 더한다"가 맞고 표시는
         * "모르면 모른다고 그린다"가 맞기 때문이다.
         */
        static List<RoundUsage> of(List<Call> calls) {
            Map<Integer, RoundUsage> byRound = new TreeMap<>();
            for (Call call : calls) {
                if (call.roundNo() == null) {
                    continue;
                }
                byRound.merge(call.roundNo(),
                        new RoundUsage(call.roundNo(), 1,
                                zeroIfNull(call.promptTokens()), zeroIfNull(call.outputTokens()),
                                zeroIfNull(call.promptTokens()) + zeroIfNull(call.outputTokens())),
                        RoundUsage::plus);
            }
            return List.copyOf(byRound.values());
        }

        private RoundUsage plus(RoundUsage other) {
            return new RoundUsage(this.roundNo,
                    this.calls + other.calls,
                    this.promptTokens + other.promptTokens,
                    this.outputTokens + other.outputTokens,
                    this.totalTokens + other.totalTokens);
        }

        private static long zeroIfNull(Integer value) {
            return value == null ? 0L : value;
        }
    }

    /** 모델 호출 한 줄. */
    @Schema(description = "모델 호출 한 줄")
    public record Call(

            @Schema(description = "로그 ID", example = "301")
            Long logId,

            @Schema(description = "협상 라운드. 라운드를 싣지 않는 호출은 null", example = "1")
            Integer roundNo,

            @Schema(description = "대리인 코드", example = "NEGOTIATOR")
            AgentType agentType,

            @Schema(description = "대리인 라벨", example = "협상")
            String agentTypeLabel,

            @Schema(description = "모델명", example = "gemini-2.5-flash")
            String model,

            @Schema(description = "결과 코드", example = "SUCCESS")
            AiCallStatus status,

            @Schema(description = "결과 라벨", example = "성공")
            String statusLabel,

            @Schema(description = "입력 토큰", example = "1561")
            Integer promptTokens,

            @Schema(description = "출력 토큰", example = "1461")
            Integer outputTokens,

            @Schema(description = "총 토큰. 한쪽이 없으면 null", example = "3022")
            Integer totalTokens,

            @Schema(description = "응답시간(ms). 모델 호출 구간만이다", example = "15574")
            Integer latencyMs,

            @Schema(description = "재시도 횟수", example = "0")
            int retryCount,

            @Schema(description = "실패 사유. 성공이면 null")
            String errorMessage,

            @Schema(description = "호출 시각(KST)")
            LocalDateTime calledAt
    ) {

        static Call from(AiAgentLogAdminRepository.CallRow row) {
            AgentType agentType = AgentType.from(row.getAgentType());
            AiCallStatus status = AiCallStatus.from(row.getStatus());

            return new Call(
                    row.getLogId(),
                    row.getRoundNo(),
                    agentType,
                    agentType.getLabel(),
                    row.getModel(),
                    status,
                    status.getLabel(),
                    row.getPromptTokens(),
                    row.getOutputTokens(),
                    totalTokens(row),
                    row.getLatencyMs(),
                    row.getRetryCount() == null ? 0 : row.getRetryCount(),
                    row.getErrorMessage(),
                    row.getCalledAt());
        }

        /**
         * 줄 합계.
         *
         * <p>토큰이 안 남는 호출이 있다(응답을 못 받고 끊긴 실패 건). 그때 <b>없는 값을 0 으로
         * 채우지 않는다</b> — 0 토큰짜리 호출이 있었던 것처럼 보이기 때문이다. 모르는 건 null 로
         * 두고 화면이 "-" 로 그리게 한다.
         */
        private static Integer totalTokens(AiAgentLogAdminRepository.CallRow row) {
            if (row.getPromptTokens() == null || row.getOutputTokens() == null) {
                return null;
            }
            return row.getPromptTokens() + row.getOutputTokens();
        }
    }
}
