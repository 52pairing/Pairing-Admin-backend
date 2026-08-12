package com.pairing.admin.negotiation.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.negotiation.domain.ConditionType;
import com.pairing.admin.negotiation.domain.ConditionValueLabels;
import com.pairing.admin.negotiation.domain.NegotiationNo;
import com.pairing.admin.negotiation.domain.NegotiationStatus;
import com.pairing.admin.negotiation.exception.NegotiationAdminErrorCode;
import com.pairing.admin.negotiation.infrastructure.persistence.AiAgentLogAdminRepository;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationAdminRepository;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationConditionAdminRepository;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationConditionJpaEntity;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationMessageAdminRepository;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationMessageJpaEntity;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationDetailResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationListItemResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationSummaryResponse;
import com.pairing.admin.negotiation.presentation.api.response.NegotiationTokenUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Agent 관리. (관리자 &gt; AI Agent 관리)
 *
 * <p>협상 도메인의 <b>읽기 전용</b> 관리 화면이다. 이 서버는 협상을 만들지도 고치지도 않는다.
 * 조건 확정·라운드 진행은 전부 백엔드의 협상 루프가 하고, 여기서 손대면 대리인이 이미 봉인한
 * 로그(해시 체인)와 실제 값이 어긋나 분쟁 시 검증이 불가능해진다.
 */
@Service
@RequiredArgsConstructor
public class NegotiationAdminService {

    private final NegotiationAdminRepository negotiationAdminRepository;
    private final NegotiationConditionAdminRepository conditionRepository;
    private final NegotiationMessageAdminRepository messageRepository;
    private final AiAgentLogAdminRepository aiAgentLogRepository;

    /** 상단 카드 집계. */
    @Transactional(readOnly = true)
    public NegotiationSummaryResponse getSummary() {
        return NegotiationSummaryResponse.from(negotiationAdminRepository.findSummary());
    }

    /**
     * 협상 목록.
     *
     * <p>네이티브 쿼리라 {@code Page} 를 리포지토리가 만들어 주지 않는다. 목록과 건수를 따로
     * 조회해 {@link PageImpl} 로 합친다.
     *
     * <p>빈 문자열 키워드는 null 로 바꾼다. 화면에서 검색어를 지우면 {@code keyword=} 로
     * 넘어오는데, 그대로 두면 <b>모든 행이 걸리는 LIKE '%%'</b> 가 되어 인덱스를 못 탄다.
     */
    @Transactional(readOnly = true)
    public PageResponse<NegotiationListItemResponse> search(NegotiationStatus status,
                                                            String keyword,
                                                            Pageable pageable) {
        String statusCode = status == null ? null : status.name();
        String normalizedKeyword = blankToNull(keyword);

        List<NegotiationListItemResponse> content = negotiationAdminRepository
                .findPage(statusCode, normalizedKeyword, pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(NegotiationListItemResponse::from)
                .toList();

        long total = negotiationAdminRepository.countPage(statusCode, normalizedKeyword);

        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 협상 상세(협상 로그 탭).
     *
     * <p>대화를 라운드로 묶어 내려보낸다. 묶는 일을 화면에 맡기지 않는 이유는, 라운드 경계가
     * 단순 그룹핑이 아니라 <b>협상 규칙</b>이기 때문이다 — 예를 들어 [거절]은 라운드를 올리지
     * 않는다(거절은 "이 선으로는 안 된다"일 뿐 새 제안이 아니라서). 그 규칙을 아는 쪽에서
     * 묶어야 화면마다 다르게 세는 일이 없다.
     */
    @Transactional(readOnly = true)
    public NegotiationDetailResponse getDetail(Long negotiationId) {
        NegotiationAdminRepository.ListRow header = negotiationAdminRepository.findHeader(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationAdminErrorCode.NEGOTIATION_NOT_FOUND));

        List<NegotiationConditionJpaEntity> conditions =
                conditionRepository.findByNegotiationIdOrderBySortOrderAscIdAsc(negotiationId);

        NegotiationStatus status = NegotiationStatus.valueOf(header.getStatus());

        return new NegotiationDetailResponse(
                header.getNegotiationId(),
                NegotiationNo.of(header.getNegotiationId(), header.getStartedAt()),
                header.getProjectId(),
                header.getProjectTitle(),
                header.getClientName(),
                header.getFreelancerName(),
                status,
                status.getLabel(),
                header.getStartedAt(),
                header.getEndedAt(),
                header.getTotalRound() == null ? 0 : header.getTotalRound(),
                toFinalResult(conditions),
                toRounds(negotiationId, conditions));
    }

    /**
     * 토큰 사용량 탭.
     *
     * <p>협상 로그 탭과 <b>같은 화면의 다른 탭</b>이라 별도 엔드포인트로 뺐다. 상세 응답에
     * 합치지 않은 이유는, 라운드별 대화만 보려는 관리자가 매번 로그 표까지 같이 읽게 되기
     * 때문이다. 협상 한 건이 부르는 모델 호출은 라운드마다 쌓여서 적지 않다.
     *
     * <p>호출 기록이 없어도 정상이다 — 대리인이 아직 안 돈 협상이거나, 파이썬이 로그 INSERT 에
     * 실패한 경우다(파이썬은 <b>로그 실패로 본 기능을 죽이지 않는다</b>). 그래서 여기서도
     * 빈 목록을 그대로 내보내고 예외로 만들지 않는다.
     *
     * <p>다만 <b>협상 존재 여부는 확인한다.</b> 없는 ID 로 부르면 빈 사용량이 내려가서, 관리자가
     * "이 협상은 AI 를 안 썼구나"로 잘못 읽는다.
     */
    @Transactional(readOnly = true)
    public NegotiationTokenUsageResponse getTokenUsage(Long negotiationId) {
        if (negotiationAdminRepository.findById(negotiationId).isEmpty()) {
            throw new BusinessException(NegotiationAdminErrorCode.NEGOTIATION_NOT_FOUND);
        }

        return NegotiationTokenUsageResponse.from(
                negotiationId,
                aiAgentLogRepository.findUsage(negotiationId),
                aiAgentLogRepository.findCalls(negotiationId));
    }

    /**
     * 최종 협상 결과 카드.
     *
     * <p><b>합의된 조건만</b> 채운다. 미합의 쟁점의 마지막 제안값을 여기 넣으면, 아직 아무도
     * 동의하지 않은 값이 "최종 결과"로 보여서 관리자가 타결된 것으로 오인한다.
     */
    private NegotiationDetailResponse.FinalResult toFinalResult(List<NegotiationConditionJpaEntity> conditions) {
        Map<ConditionType, String> agreed = conditions.stream()
                .filter(NegotiationConditionJpaEntity::isAgreed)
                .collect(Collectors.toMap(
                        NegotiationConditionJpaEntity::getConditionType,
                        NegotiationConditionJpaEntity::getAgreedValue,
                        (a, b) -> a));

        return new NegotiationDetailResponse.FinalResult(
                label(agreed, ConditionType.AMOUNT),
                label(agreed, ConditionType.PERIOD),
                label(agreed, ConditionType.START_DATE),
                label(agreed, ConditionType.WORK_STYLE),
                label(agreed, ConditionType.WORK_FORM));
    }

    private String label(Map<ConditionType, String> agreed, ConditionType type) {
        return ConditionValueLabels.of(type, agreed.get(type));
    }

    private List<NegotiationDetailResponse.RoundLog> toRounds(Long negotiationId,
                                                              List<NegotiationConditionJpaEntity> conditions) {
        Map<Long, ConditionType> typeById = conditions.stream()
                .collect(Collectors.toMap(NegotiationConditionJpaEntity::getId,
                        NegotiationConditionJpaEntity::getConditionType,
                        (a, b) -> a));

        List<NegotiationMessageJpaEntity> messages =
                messageRepository.findByNegotiationIdOrderByRoundNoAscCreatedAtAscIdAsc(negotiationId);

        // 라운드 순서를 유지해야 하므로 LinkedHashMap. 조회가 이미 round_no 오름차순이다.
        Map<Integer, List<NegotiationMessageJpaEntity>> byRound = messages.stream()
                .collect(Collectors.groupingBy(NegotiationMessageJpaEntity::getRoundNo,
                        LinkedHashMap::new, Collectors.toList()));

        return byRound.entrySet().stream()
                .map(entry -> new NegotiationDetailResponse.RoundLog(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().get(0).getCreatedAt(),
                        entry.getValue().stream()
                                .map(message -> toMessage(message, typeById))
                                .toList()))
                .toList();
    }

    private NegotiationDetailResponse.Message toMessage(NegotiationMessageJpaEntity message,
                                                        Map<Long, ConditionType> typeById) {
        // 시스템 안내는 쟁점이 없다(condition_id = null). 그 경우 쟁점 칸은 비워 둔다.
        ConditionType conditionType = message.getConditionId() == null
                ? null
                : typeById.get(message.getConditionId());

        return new NegotiationDetailResponse.Message(
                message.getCreatedAt(),
                message.getSenderType(),
                message.getSenderType().getLabel(),
                message.getSenderType().isAgent(),
                conditionType,
                conditionType == null ? null : conditionType.getLabel(),
                message.getMessageType(),
                message.getMessageType().getLabel(),
                message.getProposedValue(),
                ConditionValueLabels.of(conditionType, message.getProposedValue()),
                message.getContent(),
                message.getReason(),
                message.getResponse(),
                responseLabel(message.getResponse()));
    }

    private String responseLabel(String response) {
        if (response == null) {
            return null;
        }
        return switch (response) {
            case "ACCEPT" -> "수락";
            case "REJECT" -> "거절";
            // 백엔드에 응답 값이 늘어나도 화면이 빈칸이 되지 않게 원문을 그대로 보여 준다.
            default -> response;
        };
    }
}
