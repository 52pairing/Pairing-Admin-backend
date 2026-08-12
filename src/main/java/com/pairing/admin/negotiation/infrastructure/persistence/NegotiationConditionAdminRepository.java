package com.pairing.admin.negotiation.infrastructure.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;

/** 협상 조건 조회. 읽기 전용이라 {@code JpaRepository} 대신 필요한 메서드만 연다. */
public interface NegotiationConditionAdminRepository
        extends Repository<NegotiationConditionJpaEntity, Long> {

    List<NegotiationConditionJpaEntity> findByNegotiationIdOrderBySortOrderAscIdAsc(Long negotiationId);
}
