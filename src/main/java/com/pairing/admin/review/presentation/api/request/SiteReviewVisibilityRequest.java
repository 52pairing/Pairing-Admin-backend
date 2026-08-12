package com.pairing.admin.review.presentation.api.request;

import com.pairing.admin.review.domain.SiteReviewVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 공개·홍보 설정 변경 요청.
 *
 * <p>화면의 버튼은 "공개로 변경"과 "홍보 활용"이 따로지만 요청은 하나로 받는다.
 * 둘을 따로 받으면 "비공개인데 홍보 활용" 같은 조합이 잠깐씩 생긴다.
 * 버튼 하나를 누를 때 나머지 값은 현재 값을 그대로 실어 보내면 된다.
 */
@Schema(description = "사이트 리뷰 공개·홍보 설정 변경 요청")
public record SiteReviewVisibilityRequest(

        @Schema(description = "PRIVATE(비공개) / PUBLIC(공개)", example = "PUBLIC")
        @NotNull(message = "공개 여부를 선택해 주세요.")
        SiteReviewVisibility visibility,

        @Schema(description = "홍보 활용 여부. 비공개면 false 여야 합니다.", example = "true")
        @NotNull(message = "홍보 활용 여부를 선택해 주세요.")
        Boolean promoted
) {
}
