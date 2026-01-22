package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.LedgerEntry;
import com.example.application.domain.TaxLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for TaxLine entities.
 * Provides queries for tax reporting and GST return generation.
 */
@Repository
public interface TaxLineRepository extends JpaRepository<TaxLine, Long> {

    /**
     * Find all tax lines for a company within a date range.
     */
    @Query("SELECT t FROM TaxLine t WHERE t.company = :company " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate " +
           "ORDER BY t.entryDate, t.taxCode")
    List<TaxLine> findByCompanyAndDateRange(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find tax lines by tax code within a date range.
     */
    @Query("SELECT t FROM TaxLine t WHERE t.company = :company " +
           "AND t.taxCode = :taxCode " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate " +
           "ORDER BY t.entryDate")
    List<TaxLine> findByCompanyAndTaxCodeAndDateRange(
        @Param("company") Company company,
        @Param("taxCode") String taxCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find tax lines by report box within a date range.
     */
    @Query("SELECT t FROM TaxLine t WHERE t.company = :company " +
           "AND t.reportBox = :reportBox " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate " +
           "ORDER BY t.entryDate")
    List<TaxLine> findByCompanyAndReportBoxAndDateRange(
        @Param("company") Company company,
        @Param("reportBox") String reportBox,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Sum tax amounts by tax code for a date range.
     * Returns [taxCode, sumTaxableAmount, sumTaxAmount]
     */
    @Query("SELECT t.taxCode, SUM(t.taxableAmount), SUM(t.taxAmount) FROM TaxLine t " +
           "WHERE t.company = :company " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate " +
           "GROUP BY t.taxCode ORDER BY t.taxCode")
    List<Object[]> sumByTaxCode(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Sum tax amounts by report box for a date range.
     * Returns [reportBox, sumTaxableAmount, sumTaxAmount]
     */
    @Query("SELECT t.reportBox, SUM(t.taxableAmount), SUM(t.taxAmount) FROM TaxLine t " +
           "WHERE t.company = :company " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate " +
           "GROUP BY t.reportBox ORDER BY t.reportBox")
    List<Object[]> sumByReportBox(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total taxable amount for a date range.
     */
    @Query("SELECT COALESCE(SUM(t.taxableAmount), 0) FROM TaxLine t " +
           "WHERE t.company = :company " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate")
    BigDecimal getTotalTaxableAmount(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total tax amount for a date range.
     */
    @Query("SELECT COALESCE(SUM(t.taxAmount), 0) FROM TaxLine t " +
           "WHERE t.company = :company " +
           "AND t.entryDate >= :startDate AND t.entryDate <= :endDate")
    BigDecimal getTotalTaxAmount(
        @Param("company") Company company,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find tax lines for a specific ledger entry.
     */
    List<TaxLine> findByLedgerEntry(LedgerEntry ledgerEntry);

    /**
     * Check if tax lines exist for a ledger entry.
     */
    boolean existsByLedgerEntry(LedgerEntry ledgerEntry);
}
