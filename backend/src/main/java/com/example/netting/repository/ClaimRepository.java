package com.example.netting.repository;

import com.example.netting.domain.Claim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    @EntityGraph(attributePaths = {"creditor", "debtor", "agreement"})
    List<Claim> findAllByOrderByInvoiceNumber();
}
