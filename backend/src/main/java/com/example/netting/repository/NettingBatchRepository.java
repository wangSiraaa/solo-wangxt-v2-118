package com.example.netting.repository;

import com.example.netting.domain.NettingBatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NettingBatchRepository extends JpaRepository<NettingBatch, Long> {

    @EntityGraph(attributePaths = {
            "groups",
            "groups.settlements",
            "groups.settlements.fxQuote",
            "groups.allocations",
            "groups.allocations.settlement",
            "groups.settlements.allocations"
    })
    Optional<NettingBatch> findWithDetailsByExternalId(UUID externalId);

    @EntityGraph(attributePaths = "groups")
    List<NettingBatch> findAllByOrderByCalculatedAtDesc();
}
