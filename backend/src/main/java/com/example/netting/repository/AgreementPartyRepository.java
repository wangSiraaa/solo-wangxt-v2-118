package com.example.netting.repository;

import com.example.netting.domain.AgreementParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgreementPartyRepository extends JpaRepository<AgreementParty, Long> {
    List<AgreementParty> findByAgreementId(Long agreementId);
}
