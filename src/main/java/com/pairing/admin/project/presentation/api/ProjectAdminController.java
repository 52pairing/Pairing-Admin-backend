package com.pairing.admin.project.presentation.api;

import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.common.api.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 프로젝트 관리. (관리자 &gt; 프로젝트 관리) — <b>스켈레톤</b>
 *
 * <p>채우는 순서는 member 도메인과 같다.
 * <ol>
 *   <li>{@code project/infrastructure/persistence} 에 ProjectJpaEntity 를 만든다.
 *       컬럼은 백엔드 db/init/02-create-schema.sql 의 project 테이블을 그대로 옮긴다.</li>
 *   <li>Repository + Specs 로 검색 조건을 조립한다.</li>
 *   <li>Service 에서 조회/상태변경을 구현하고 여기서 호출한다.</li>
 * </ol>
 *
 * <p>엔티티를 추가하면 {@code ddl-auto=validate} 가 스키마와 대조해 준다.
 * 컬럼명을 잘못 적으면 기동 단계에서 바로 걸린다.
 */
@RestController
@RequestMapping("/api/v1/admin/projects")
@Tag(name = "20. Project", description = "프로젝트 관리 API (스켈레톤)")
public class ProjectAdminController {

    @GetMapping
    @Operation(summary = "[관리자] 프로젝트 목록 조회", description = "아직 구현되지 않았습니다. 빈 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<PageResponse<Object>>> findProjects() {
        // TODO: ProjectAdminService.search(...) 로 교체
        PageResponse<Object> empty = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
        return ResponseEntity.ok(ApiResponse.success("PROJECT_LIST_FOUND", "조회에 성공했습니다.", empty));
    }
}
