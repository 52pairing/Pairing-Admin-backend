package com.pairing.admin.review.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 홍보 활용 설정 변경 요청.
 *
 * <p>공개/비공개는 두지 않는다. 사이트 후기는 이 관리 화면 말고는 어디에도 그대로 노출되지 않고,
 * 사용자가 보는 것은 <b>관리자가 홍보로 고른 후기와 평균 별점</b>뿐이다. 그래서 홍보를 끄면
 * 이미 안 보이고, 공개 여부는 아무것도 바꾸지 않는 스위치였다.
 */
@Schema(description = "사이트 리뷰 홍보 활용 설정 변경 요청")
public record SiteReviewPromotionRequest(

        @Schema(description = "홍보 활용 여부. true 면 메인 노출 후보가 된다.", example = "true")
        @NotNull(message = "홍보 활용 여부를 선택해 주세요.")
        Boolean promoted
) {
}
