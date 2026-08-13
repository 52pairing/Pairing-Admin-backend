package com.pairing.admin.matching.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.matching.infrastructure.ai.PythonEmbeddingClient;
import com.pairing.admin.matching.infrastructure.persistence.MatchingAdminRepository;
import com.pairing.admin.matching.presentation.api.response.AiLogResponse;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import com.pairing.admin.matching.presentation.api.response.MatchingDiagnosticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingAdminService {

    private final MatchingAdminRepository matchingAdminRepository;
    private final PythonEmbeddingClient pythonEmbeddingClient;

    @Transactional(readOnly = true)
    public EmbeddingMissingResponse findMissingEmbeddings(String targetType, Pageable pageable) {
        long freelancerMissingCount = matchingAdminRepository.countMissingFreelancers();
        long positionMissingCount = matchingAdminRepository.countMissingPositions();
        PageImpl<EmbeddingMissingResponse.Item> page =
                matchingAdminRepository.findMissingEmbeddings(targetType, pageable);
        return new EmbeddingMissingResponse(
                new EmbeddingMissingResponse.Summary(freelancerMissingCount, positionMissingCount),
                PageResponse.from(page)
        );
    }

    @Async
    public void reindexAll() {
        int freelancerSuccess = 0;
        int freelancerFail = 0;
        for (Long freelancerId : matchingAdminRepository.findReindexableFreelancerIds()) {
            try {
                reindexFreelancerNow(freelancerId);
                freelancerSuccess++;
            } catch (Exception e) {
                freelancerFail++;
                log.warn("[Matching admin] freelancer reindex failed. freelancerId={}", freelancerId, e);
            }
        }

        int positionSuccess = 0;
        int positionFail = 0;
        for (MatchingAdminRepository.PositionRef position : matchingAdminRepository.findReindexablePositions()) {
            try {
                reindexPositionNow(position.projectId(), position.positionId());
                positionSuccess++;
            } catch (Exception e) {
                positionFail++;
                log.warn("[Matching admin] position reindex failed. projectId={}, positionId={}",
                        position.projectId(), position.positionId(), e);
            }
        }

        log.info("[Matching admin] reindex finished. freelancerSuccess={}, freelancerFail={}, "
                        + "positionSuccess={}, positionFail={}",
                freelancerSuccess, freelancerFail, positionSuccess, positionFail);
    }

    @Async
    public void reindexFreelancer(Long freelancerId) {
        try {
            reindexFreelancerNow(freelancerId);
        } catch (Exception e) {
            log.warn("[Matching admin] one freelancer reindex failed. freelancerId={}", freelancerId, e);
        }
    }

    @Async
    public void reindexPosition(Long projectId, Long positionId) {
        try {
            reindexPositionNow(projectId, positionId);
        } catch (Exception e) {
            log.warn("[Matching admin] one position reindex failed. projectId={}, positionId={}",
                    projectId, positionId, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AiLogResponse> findAiLogs(String agentType, String refType, Long refId, String status,
                                                  LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return PageResponse.from(
                matchingAdminRepository.findAiLogs(agentType, refType, refId, status, from, to, pageable));
    }

    @Transactional(readOnly = true)
    public MatchingDiagnosticsResponse findDiagnostics(Long projectId, Long positionId) {
        return matchingAdminRepository.findDiagnostics(projectId, positionId);
    }

    private void reindexFreelancerNow(Long freelancerId) {
        String text = buildText(matchingAdminRepository.findFreelancerEmbeddingSource(freelancerId));
        if (text.isBlank()) {
            log.warn("[Matching admin] skip freelancer reindex because source text is blank. freelancerId={}",
                    freelancerId);
            return;
        }
        pythonEmbeddingClient.upsertFreelancer(freelancerId, text);
    }

    private void reindexPositionNow(Long projectId, Long positionId) {
        String text = buildText(matchingAdminRepository.findPositionEmbeddingSource(projectId, positionId));
        if (text.isBlank()) {
            log.warn("[Matching admin] skip position reindex because source text is blank. projectId={}, positionId={}",
                    projectId, positionId);
            return;
        }
        pythonEmbeddingClient.upsertPosition(positionId, text);
    }

    private static String buildText(List<String> parts) {
        return parts.stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
