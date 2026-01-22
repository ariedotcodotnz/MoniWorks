package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.TaxReturn;
import com.example.application.domain.TaxReturn.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TaxReturn entities.
 */
@Repository
public interface TaxReturnRepository extends JpaRepository<TaxReturn, Long> {

    /**
     * Find all returns for a company, ordered by end date descending.
     */
    List<TaxReturn> findByCompanyOrderByEndDateDesc(Company company);

    /**
     * Find returns for a company with a specific status.
     */
    List<TaxReturn> findByCompanyAndStatusOrderByEndDateDesc(Company company, Status status);

    /**
     * Find a return that covers a specific date.
     */
    @Query("SELECT r FROM TaxReturn r WHERE r.company = :company " +
           "AND r.startDate <= :date AND r.endDate >= :date")
    Optional<TaxReturn> findByCompanyAndDate(
        @Param("company") Company company,
        @Param("date") LocalDate date
    );

    /**
     * Find overlapping returns (for validation).
     */
    @Query("SELECT r FROM TaxReturn r WHERE r.company = :company " +
           "AND ((r.startDate <= :endDate AND r.endDate >= :startDate))")
    List<TaxReturn> findOverlapping(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Check if a return exists for a period.
     */
    @Query("SELECT COUNT(r) > 0 FROM TaxReturn r WHERE r.company = :company " +
           "AND r.startDate = :startDate AND r.endDate = :endDate")
    boolean existsByCompanyAndPeriod(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
