package com.pairing.admin.project.presentation.api.response;

import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.StatusCountRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 목록 상단 탭의 괄호 숫자. 피그마의 "전체 (6) · 등록 완료 (1) · 모집중 (1) ..." 이다.
 *
 * <p>탭을 배열로 내려 주는 이유는, 상태가 늘어도 화면이 필드명을 몰라도 되기 때문이다.
 * 순서는 프로젝트가 실제로 지나가는 순서이고 취소됨이 맨 뒤다.
 *
 * <p><b>검색 조건과 무관한 전체 집계다.</b> 키워드를 넣어도 이 숫자는 바뀌지 않는다.
 */
@Schema(description = "프로젝트 상태별 탭 카운트")
public record ProjectStatusCountResponse(

        @Schema(description = "전체 건수(취소·종료 포함)", example = "6")
        long total,

        @Schema(description = "상태별 건수. 화면 탭 순서 그대로다.")
        List<Item> items
) {

    @Schema(description = "상태 탭 하나")
    public record Item(

            @Schema(description = "상태 코드. 목록 조회의 status 파라미터에 그대로 넣는다.",
                    example = "IN_PROGRESS")
            ProjectStatus status,

            @Schema(description = "상태 표시명", example = "진행중")
            String label,

            @Schema(description = "건수", example = "1")
            long count
    ) {
    }

    public static ProjectStatusCountResponse from(StatusCountRow row) {
        List<Item> items = List.of(
                item(ProjectStatus.REGISTERED, row.getRegistered()),
                item(ProjectStatus.RECRUITING, row.getRecruiting()),
                item(ProjectStatus.NEGOTIATING, row.getNegotiating()),
                item(ProjectStatus.CONTRACT_PENDING, row.getContractPending()),
                item(ProjectStatus.IN_PROGRESS, row.getInProgress()),
                item(ProjectStatus.COMPLETION_PENDING, row.getCompletionPending()),
                item(ProjectStatus.CLOSED, row.getClosed()),
                item(ProjectStatus.CANCELED, row.getCanceled()));

        return new ProjectStatusCountResponse(row.getTotal(), items);
    }

    private static Item item(ProjectStatus status, long count) {
        return new Item(status, status.getLabel(), count);
    }
}
