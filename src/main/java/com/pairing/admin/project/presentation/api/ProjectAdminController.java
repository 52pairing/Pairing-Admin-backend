package com.pairing.admin.project.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.project.application.ProjectAdminService;
import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.project.presentation.api.response.ProjectDetailResponse;
import com.pairing.admin.project.presentation.api.response.ProjectStatusCountResponse;
import com.pairing.admin.project.presentation.api.response.ProjectSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 프로젝트 관리. (관리자 &gt; 프로젝트 관리, 요구사항 R38)
 *
 * <p>화면은 세 조각이다 — 상단 탭 카운트({@code /status-counts}), 목록({@code /}),
 * 상세({@code /{projectId}}). 탭 카운트를 목록 응답에 끼워 넣지 않은 이유는 회원 관리와 같다.
 * 페이지를 넘길 때마다 같은 집계를 다시 돌리게 된다.
 *
 * <p><b>조회만 있다.</b> 프로젝트 상태를 관리자가 바꾸는 화면이 아니다.
 */
@RestController
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
@Tag(name = "20. Project", description = "프로젝트 관리 API")
public class ProjectAdminController {

    private final ProjectAdminService projectAdminService;

    @GetMapping("/status-counts")
    @Operation(summary = "[관리자] 프로젝트 상태별 탭 카운트",
            description = "화면 상단 탭의 괄호 숫자입니다. 전체 건수와 상태별 건수 8개를 반환합니다. "
                    + "검색 조건과 무관한 전체 집계라, 키워드를 넣어도 이 숫자는 바뀌지 않습니다.")
    public ResponseEntity<ApiResponse<ProjectStatusCountResponse>> findStatusCounts() {
        ProjectStatusCountResponse counts = projectAdminService.findStatusCounts();
        return ResponseEntity.ok(
                ApiResponse.success("PROJECT_STATUS_COUNTS_FOUND", "조회에 성공했습니다.", counts));
    }

    @GetMapping
    @Operation(summary = "[관리자] 프로젝트 목록 조회",
            description = "상태·키워드·등록일 기간으로 검색합니다. 조건은 비우면 전체입니다. "
                    + "정렬은 최근 등록순으로 고정이며 sort 파라미터는 무시합니다. "
                    + "삭제된 프로젝트는 제외되지만, 등록 취소(CANCELED)된 프로젝트는 포함됩니다.")
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> findProjects(

            @Parameter(description = "상태 필터. 비우면 전체입니다.", example = "IN_PROGRESS")
            @RequestParam(required = false) ProjectStatus status,

            @Parameter(description = "프로젝트명·클라이언트 회사명 부분 검색어", example = "쇼핑몰")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "등록일 시작(포함). yyyy-MM-dd", example = "2026-07-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "등록일 종료(당일 포함). yyyy-MM-dd", example = "2026-08-06")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<ProjectSummaryResponse> projects =
                projectAdminService.search(status, keyword, fromDate, toDate, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("PROJECT_LIST_FOUND", "조회에 성공했습니다.", projects));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "[관리자] 프로젝트 상세 조회",
            description = "기본 정보와 함께 모집 포지션 목록(직군·직무·기술스택)과 "
                    + "계약 목록(매칭 프리랜서·계약금액·계약기간·계약상태)을 반환합니다. "
                    + "포지션과 계약은 여러 건일 수 있어 배열입니다.")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> findProject(@PathVariable Long projectId) {
        ProjectDetailResponse project = projectAdminService.findDetail(projectId);
        return ResponseEntity.ok(ApiResponse.success("PROJECT_FOUND", "조회에 성공했습니다.", project));
    }
}
