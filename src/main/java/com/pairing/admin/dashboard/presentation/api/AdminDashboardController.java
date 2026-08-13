package com.pairing.admin.dashboard.presentation.api;

import com.pairing.admin.dashboard.presentation.api.response.AdminDashboardResponse;
import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드. (관리자 &gt; 대시보드)
 *
 * <p><b>회원 지표만 실제 집계</b>이고 나머지는 고정값이다.
 * project / negotiation / settlement 엔티티를 이 프로젝트에 추가하면서 하나씩 실제 쿼리로 바꾸면 된다.
 * 어떤 모양으로 채우면 되는지는 {@code members} 부분을 보면 된다.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "00. Dashboard", description = "관리자 대시보드 API")
public class AdminDashboardController {

    private final AccountJpaRepository accountJpaRepository;

    @GetMapping
    @Operation(summary = "[관리자] 대시보드",
            description = "회원·프로젝트·협상·정산 요약과 처리 대기 항목을 반환합니다. (회원 외 항목은 아직 고정값)")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> findDashboard() {

        AdminDashboardResponse dashboard = new AdminDashboardResponse(
                LocalDate.now(),
                countMembers(),
                // TODO: project 엔티티를 추가하고 실제 집계로 교체
                new AdminDashboardResponse.Projects(0, 0, 0),
                // TODO: negotiation 엔티티를 추가하고 실제 집계로 교체
                new AdminDashboardResponse.Negotiations(0, 0, 0),
                // TODO: settlement 엔티티를 추가하고 실제 집계로 교체
                new AdminDashboardResponse.Settlements(0L, 0L, 0L),
                // TODO: inquiry / site_review / settlement 대기 건수로 교체
                List.of(new AdminDashboardResponse.PendingItem("INQUIRY", "답변 대기 문의", 0),
                        new AdminDashboardResponse.PendingItem("SITE_REVIEW", "공개 검토 대기 리뷰", 0),
                        new AdminDashboardResponse.PendingItem("SETTLEMENT", "미납 정산", 0)));

        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_FOUND", "조회에 성공했습니다.", dashboard));
    }

    /**
     * 회원 지표. 실제 DB 집계다.
     *
     * <p>"오늘"의 경계는 JVM 타임존을 따른다. {@code AdminApplication} 에서 KST로 고정해 두었으므로
     * 백엔드와 같은 기준으로 계산된다.
     */
    private AdminDashboardResponse.Members countMembers() {
        return new AdminDashboardResponse.Members(
                accountJpaRepository.countByDeletedAtIsNull(),
                accountJpaRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                        LocalDate.now().atStartOfDay()),
                // 관리자가 건 정지만 센다. LOCKED(비밀번호 5회 실패로 자동 잠김)를 여기 넣으면
                // 관리 조치가 아닌 건이 정지 지표에 섞인다.
                accountJpaRepository.countBySuspendedAtIsNotNull());
    }
}
