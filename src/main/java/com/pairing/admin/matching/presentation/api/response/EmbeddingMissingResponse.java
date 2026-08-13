package com.pairing.admin.matching.presentation.api.response;

import com.pairing.admin.global.common.api.response.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Missing matching embedding list")
public record EmbeddingMissingResponse(
        Summary summary,
        PageResponse<Item> items
) {

    public record Summary(long freelancerMissingCount, long positionMissingCount) {
    }

    public record Item(
            String targetType,
            Long targetId,
            String displayName,
            String status,
            String reason,
            String model,
            String lastLogStatus,
            LocalDateTime lastLogAt
    ) {
    }
}
