package com.pairing.admin.member.presentation.api.response;

import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.SummaryRow;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 관리 목록 상단의 요약 카드 6개.
 *
 * <p><b>여섯 값의 합은 전체와 맞지 않는다.</b> 일부러 그렇다.
 * <ul>
 *   <li>가입 대기(PENDING)·잠금(LOCKED)은 카드가 없어서 정상/정지/탈퇴 어디에도 안 들어간다</li>
 *   <li>클라이언트·프리랜서 카드는 상태와 무관하게 세므로 앞의 세 값과 겹친다</li>
 * </ul>
 * 관리자 계정({@code role = 'ADMIN'})은 전부에서 제외한다. 회원 관리 화면의 대상이 아니다.
 */
@Schema(description = "회원 요약 카드")
public record MemberStatsResponse(

        @Schema(description = "전체 회원", example = "1250")
        long total,

        @Schema(description = "정상 (정지되지 않은 ACTIVE)", example = "1180")
        long active,

        @Schema(description = "정지", example = "12")
        long suspended,

        @Schema(description = "탈퇴", example = "58")
        long withdrawn,

        @Schema(description = "클라이언트", example = "420")
        long clients,

        @Schema(description = "프리랜서", example = "830")
        long freelancers
) {

    public static MemberStatsResponse from(SummaryRow row) {
        return new MemberStatsResponse(
                row.getTotal(),
                row.getActive(),
                row.getSuspended(),
                row.getWithdrawn(),
                row.getClients(),
                row.getFreelancers()
        );
    }
}
