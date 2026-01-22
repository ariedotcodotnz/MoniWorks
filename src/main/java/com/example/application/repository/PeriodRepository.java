package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.FiscalYear;
import com.example.application.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PeriodRepository extends JpaRepository<Period, Long> {

    List<Period> findByFiscalYearOrderByPeriodIndex(FiscalYear fiscalYear);

    @Query("SELECT p FROM Period p WHERE p.fiscalYear.company = :company ORDER BY p.startDate")
    List<Period> findByCompanyOrderByStartDate(@Param("company") Company company);

    @Query("SELECT p FROM Period p WHERE p.fiscalYear.company = :company AND p.status = :status ORDER BY p.startDate")
    List<Period> findByCompanyAndStatus(@Param("company") Company company,
                                        @Param("status") Period.Status status);

    @Query("SELECT p FROM Period p WHERE p.fiscalYear.company = :company " +
           "AND :date BETWEEN p.startDate AND p.endDate")
    Optional<Period> findByCompanyAndDate(@Param("company") Company company,
                                          @Param("date") LocalDate date);

    @Query("SELECT p FROM Period p WHERE p.fiscalYear.company.id = :companyId " +
           "AND :date BETWEEN p.startDate AND p.endDate")
    Optional<Period> findByCompanyIdAndDate(@Param("companyId") Long companyId,
                                            @Param("date") LocalDate date);

    /**
     * Find all periods that overlap with the given date range.
     * A period overlaps if its start date <= rangeEndDate AND its end date >= rangeStartDate.
     */
    @Query("SELECT p FROM Period p WHERE p.fiscalYear.company = :company " +
           "AND p.startDate <= :endDate AND p.endDate >= :startDate " +
           "ORDER BY p.startDate")
    List<Period> findByFiscalYearCompanyAndDateRangeOverlap(@Param("company") Company company,
                                                             @Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate);
}
