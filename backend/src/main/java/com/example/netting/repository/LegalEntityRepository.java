package com.example.netting.repository;

import com.example.netting.domain.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {
    Optional<LegalEntity> findByCode(String code);
}
