package com.example.netting.repository;

import com.example.netting.domain.NettingAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NettingAgreementRepository extends JpaRepository<NettingAgreement, Long> {
    Optional<NettingAgreement> findByCode(String code);
}
