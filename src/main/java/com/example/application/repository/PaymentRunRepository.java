package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.PaymentRun;
import com.example.application.domain.PaymentRun.PaymentRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRunRepository extends JpaRepository<PaymentRun, Long> {

    List<PaymentRun> findByCompanyOrderByCreatedAtDesc(Company company);

    List<PaymentRun> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, PaymentRunStatus status);
}
