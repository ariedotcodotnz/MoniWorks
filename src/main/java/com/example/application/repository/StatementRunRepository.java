package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.StatementRun;
import com.example.application.domain.StatementRun.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StatementRunRepository extends JpaRepository<StatementRun, Long> {

    List<StatementRun> findByCompanyOrderByCreatedAtDesc(Company company);

    List<StatementRun> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, RunStatus status);

    @Query("SELECT r FROM StatementRun r WHERE r.company = :company " +
           "AND r.runDate >= :startDate AND r.runDate <= :endDate " +
           "ORDER BY r.createdAt DESC")
    List<StatementRun> findByCompanyAndDateRange(@Param("company") Company company,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // Count by status
    @Query("SELECT COUNT(r) FROM StatementRun r WHERE r.company = :company AND r.status = :status")
    long countByCompanyAndStatus(@Param("company") Company company, @Param("status") RunStatus status);
}
