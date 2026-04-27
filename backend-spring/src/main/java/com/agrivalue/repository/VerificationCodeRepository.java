package com.agrivalue.repository;

import com.agrivalue.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Integer> {
    Optional<VerificationCode> findFirstByEmailAndCodeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
        String email, String code, LocalDateTime now);
    void deleteByEmail(String email);
}
