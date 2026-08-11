package com.pairing.admin.global.common.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "페이지 응답")
public record PageResponse<T>(

        @Schema(description = "현재 페이지 데이터")
        List<T> content,

        @Schema(description = "현재 페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 데이터 개수", example = "135")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "7")
        int totalPages,

        @Schema(description = "첫 페이지 여부", example = "true")
        boolean first,

        @Schema(description = "마지막 페이지 여부", example = "false")
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /**
     * 엔티티 페이지를 응답 DTO 페이지로 바꾼다.
     *
     * <p>{@code Page.map()} 을 쓰면 되지만, 관리자 API는 조회가 대부분이라
     * "엔티티 -> 응답" 변환이 반복된다. 호출부를 짧게 유지하려고 열어 뒀다.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
