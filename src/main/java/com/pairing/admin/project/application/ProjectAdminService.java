package com.pairing.admin.project.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.project.exception.ProjectAdminErrorCode;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.ContractRow;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.DetailRow;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.PositionRow;
import com.pairing.admin.project.presentation.api.response.ProjectDetailResponse;
import com.pairing.admin.project.presentation.api.response.ProjectStatusCountResponse;
import com.pairing.admin.project.presentation.api.response.ProjectSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 관리. (관리자 &gt; 프로젝트 관리, 요구사항 R38)
 *
 * <p><b>조회 전용이다.</b> 프로젝트 상태는 클라이언트의 등록·취소와 매칭·계약 흐름으로만 바뀐다.
 * 관리자가 여기서 상태를 건드리면 진행 중인 매칭·계약·정산이 함께 어긋나므로,
 * 이 서비스에는 쓰기 경로를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProjectAdminService {

    private final ProjectAdminQueryRepository projectAdminQueryRepository;

    /**
     * 프로젝트 목록.
     *
     * <p>정렬은 쿼리에 최근 등록순으로 고정되어 있다. {@code pageable} 에서는 페이지 번호와
     * 크기만 쓴다. 네이티브 쿼리에 정렬을 문자열로 이어 붙이면 SQL 주입 경로가 되기 때문이다.
     *
     * <p>기간 조건은 <b>등록일 기준</b>이다. 피그마 검색줄의 날짜 입력 두 칸이 이 값이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> search(ProjectStatus status,
                                                       String keyword,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       Pageable pageable) {

        validateRange(fromDate, toDate);

        String statusParam = status == null ? null : status.name();
        String keywordParam = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // 종료일은 그날을 포함해야 한다. 하루를 더해 "미만" 으로 비교하면 23:59 에 등록된 건도 들어온다.
        LocalDate toExclusive = toDate == null ? null : toDate.plusDays(1);

        List<ProjectSummaryResponse> content = projectAdminQueryRepository
                .findPage(statusParam, keywordParam, fromDate, toExclusive,
                        pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(ProjectSummaryResponse::from)
                .toList();

        long total = projectAdminQueryRepository.countPage(statusParam, keywordParam, fromDate, toExclusive);

        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    /** 목록 상단 탭 카운트. 검색 조건과 무관한 전체 집계다. */
    @Transactional(readOnly = true)
    public ProjectStatusCountResponse findStatusCounts() {
        return ProjectStatusCountResponse.from(projectAdminQueryRepository.findStatusCounts());
    }

    /**
     * 프로젝트 상세.
     *
     * <p>세 번 조회한다 — 기본 정보 · 포지션 · 계약. 한 쿼리로 조인하면 포지션 2개 × 계약 2건이
     * 4줄로 곱해져 서비스에서 다시 풀어야 한다. 상세는 한 번에 한 건만 여는 화면이라
     * 왕복 세 번이 그 복잡도보다 싸다.
     *
     * <p>기본 정보 쿼리가 비면 404 다. 그 쿼리는 목록과 같은 {@code deleted_at IS NULL} 을 걸고
     * 있어서, 없는 프로젝트와 보존기간이 끝나 지운 프로젝트를 같은 결과로 다룬다.
     * 등록 취소({@code status = 'CANCELED'})는 지워진 것이 아니라 정상적으로 조회된다.
     */
    @Transactional(readOnly = true)
    public ProjectDetailResponse findDetail(Long projectId) {
        DetailRow detail = projectAdminQueryRepository.findDetail(projectId)
                .orElseThrow(() -> new BusinessException(ProjectAdminErrorCode.PROJECT_NOT_FOUND));

        List<PositionRow> positions = projectAdminQueryRepository.findPositions(projectId);
        List<ContractRow> contracts = projectAdminQueryRepository.findContracts(projectId);

        return ProjectDetailResponse.from(detail, positions, contracts);
    }

    /**
     * 시작일이 종료일보다 늦으면 막는다.
     *
     * <p>SQL 은 이 조건에서 조용히 0건을 돌려준다. 관리자는 "해당 기간에 프로젝트가 없다" 로
     * 읽게 되는데, 실제로는 날짜를 거꾸로 넣은 것이다. 결과가 같아 보이는 두 상황을
     * 구분해 주는 편이 낫다.
     */
    private void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ProjectAdminErrorCode.INVALID_DATE_RANGE);
        }
    }
}
