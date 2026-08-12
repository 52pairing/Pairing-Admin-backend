package com.pairing.admin.negotiation.infrastructure.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;

/** 협상 대화 조회. 읽기 전용. */
public interface NegotiationMessageAdminRepository
        extends Repository<NegotiationMessageJpaEntity, Long> {

    /**
     * 라운드 → 시간 순. 화면이 라운드 아코디언 안에 대화 표를 그리는 순서와 같다.
     *
     * <p>같은 라운드 안에서 {@code created_at} 이 밀리초까지 겹치는 경우가 있다. A2A 응답 한 건에
     * 담긴 발언들이 같은 트랜잭션에서 한꺼번에 저장되기 때문이다. id 를 2차 정렬로 둬야
     * <b>새로고침할 때마다 대화 순서가 뒤바뀌지 않는다.</b>
     */
    List<NegotiationMessageJpaEntity> findByNegotiationIdOrderByRoundNoAscCreatedAtAscIdAsc(Long negotiationId);
}
