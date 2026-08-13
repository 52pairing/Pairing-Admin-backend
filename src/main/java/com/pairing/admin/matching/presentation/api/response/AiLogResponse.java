package com.pairing.admin.matching.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "AI matching log")
public record AiLogResponse(
        Long logId,
        String agentType,
        String refType,
        Long refId,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
}
