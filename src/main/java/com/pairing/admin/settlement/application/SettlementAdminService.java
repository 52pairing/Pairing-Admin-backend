package com.pairing.admin.settlement.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.settlement.domain.PayerRole;
import com.pairing.admin.settlement.domain.SettlementPhase;
import com.pairing.admin.settlement.domain.SettlementStatus;
import com.pairing.admin.settlement.exception.SettlementAdminErrorCode;
import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository;
import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository.DetailRow;
import com.pairing.admin.settlement.presentation.api.response.SettlementDetailResponse;
import com.pairing.admin.settlement.presentation.api.response.SettlementStatsResponse;
import com.pairing.admin.settlement.presentation.api.response.SettlementSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 거래·정산 관리. (관리자 &gt; 거래·정산 관리, 요구사항 R39)
 *
 * <p><b>조회 전용이다.</b> 정산 상태를 관리자 서버에서 바꾸면 백엔드의 결제 흐름
 * (원장 기록 · 프로젝트 결제 상태 전이 · 알림)과 어긋나 실제 입금과 장부가 달라진다.
 * 결제·재청구 같은 변경이 필요해지면 백엔드에 유스케이스를 만들고 여기서 호출하는 편이 맞다.
 * 그때는 스켈레톤 주석대로 {@code @PreAuthorize("hasAuthority('SETTLEMENT_WRITE')")} 로
 * 조회 권한과 변경 권한을 나눈다.
 */
@Service
@RequiredArgsConstructor
public class SettlementAdminService {

    private final SettlementAdminQueryRepository settlementAdminQueryRepository;

    /**
     * 정산 목록.
     *
     * <p>정렬은 쿼리에 최근 생성순으로 고정되어 있다. {@code pageable} 에서는 페이지 번호와
     * 크기만 쓴다.
     *
     * <p>기간 조건은 <b>정산 생성일</b> 기준이다. 완료일로 걸면 아직 내지 않은 건이 통째로 빠져,
     * "이번 달 미납" 을 찾으려는 조회가 언제나 0건이 된다.
     */
    @Transactional(readOnly = true)
    public PageResponse<SettlementSummaryResponse> search(SettlementStatus status,
                                                          SettlementPhase phase,
                                                          PayerRole payerRole,
                                                          String keyword,
                                                          LocalDate fromDate,
                                                          LocalDate toDate,
                                                          Pageable pageable) {

        validateRange(fromDate, toDate);

        String statusParam = status == null ? null : status.name();
        String phaseParam = phase == null ? null : phase.name();
        String roleParam = payerRole == null ? null : payerRole.name();
        String keywordParam = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // 종료일은 그날을 포함해야 한다. 하루를 더해 "미만" 으로 비교하면 23:59 에 생긴 건도 들어온다.
        LocalDate toExclusive = toDate == null ? null : toDate.plusDays(1);

        List<SettlementSummaryResponse> content = settlementAdminQueryRepository
                .findPage(statusParam, phaseParam, roleParam, keywordParam, fromDate, toExclusive,
                        pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(SettlementSummaryResponse::from)
                .toList();

        long total = settlementAdminQueryRepository
                .countPage(statusParam, phaseParam, roleParam, keywordParam, fromDate, toExclusive);

        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    /**
     * 목록 상단 요약 카드.
     *
     * <p>"이번 달" 의 경계를 여기서 계산해 쿼리에 넘긴다. SQL 의 {@code now()} 를 쓰면 DB 서버의
     * 타임존을 따르게 되는데, JVM 은 {@code AdminApplication} 에서 KST 로 고정되어 있어
     * 매월 1일 새벽에 두 기준이 갈린다. 백엔드 대시보드도 같은 방식으로 "오늘" 을 정한다.
     *
     * <p>위약금은 {@code penalty} 테이블에서 따로 읽는다. 정산과 조인하면 정산 건수가
     * 위약금 건수만큼 부풀어 나머지 카드까지 틀어진다.
     */
    @Transactional(readOnly = true)
    public SettlementStatsResponse findStats() {
        YearMonth month = YearMonth.now();

        return SettlementStatsResponse.from(
                settlementAdminQueryRepository.findSummary(month.atDay(1), month.plusMonths(1).atDay(1)),
                settlementAdminQueryRepository.findPenaltySummary(),
                month);
    }

    /**
     * 정산 상세.
     *
     * <p>정산은 소프트 삭제가 없다. 취소는 {@code status = 'CANCELED'} 로 남으므로,
     * 쿼리 결과가 비었다는 것은 그런 정산이 없다는 뜻이고 그대로 404 다.
     */
    @Transactional(readOnly = true)
    public SettlementDetailResponse findDetail(Long settlementId) {
        DetailRow detail = settlementAdminQueryRepository.findDetail(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementAdminErrorCode.SETTLEMENT_NOT_FOUND));

        return SettlementDetailResponse.from(detail);
    }

    /**
     * 시작일이 종료일보다 늦으면 막는다.
     *
     * <p>SQL 은 조용히 0건을 돌려준다. 관리자는 "그 기간에 정산이 없다" 로 읽지만 실제로는
     * 날짜를 거꾸로 넣은 것이다. 금액을 보는 화면에서 이 둘을 구분해 주지 않으면
     * "미납이 없다" 는 잘못된 결론이 나온다.
     */
    private void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(SettlementAdminErrorCode.INVALID_DATE_RANGE);
        }
    }
}
