package com.example.application.repository;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.Department;
import com.example.application.domain.LedgerEntry;
import com.example.application.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransaction(Transaction transaction);

    List<LedgerEntry> findByAccount(Account account);

    @Query("SELECT le FROM LedgerEntry le WHERE le.company = :company " +
           "AND le.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY le.entryDate, le.id")
    List<LedgerEntry> findByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT le FROM LedgerEntry le WHERE le.account = :account " +
           "AND le.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY le.entryDate, le.id")
    List<LedgerEntry> findByAccountAndDateRange(
            @Param("account") Account account,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(le.amountDr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate <= :asOfDate")
    BigDecimal sumDebitsByAccountAsOf(@Param("account") Account account,
                                       @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT COALESCE(SUM(le.amountCr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate <= :asOfDate")
    BigDecimal sumCreditsByAccountAsOf(@Param("account") Account account,
                                        @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT COALESCE(SUM(le.amountDr) - SUM(le.amountCr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate <= :asOfDate")
    BigDecimal getBalanceByAccountAsOf(@Param("account") Account account,
                                        @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT COALESCE(SUM(le.amountDr), 0) FROM LedgerEntry le " +
           "WHERE le.company = :company AND le.entryDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitsByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(le.amountCr), 0) FROM LedgerEntry le " +
           "WHERE le.company = :company AND le.entryDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditsByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByTransaction(Transaction transaction);

    // Department-filtered queries for departmental reporting
    @Query("SELECT le FROM LedgerEntry le WHERE le.account = :account " +
           "AND le.entryDate BETWEEN :startDate AND :endDate " +
           "AND le.department = :department " +
           "ORDER BY le.entryDate, le.id")
    List<LedgerEntry> findByAccountAndDateRangeAndDepartment(
            @Param("account") Account account,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") Department department);

    @Query("SELECT COALESCE(SUM(le.amountDr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate BETWEEN :startDate AND :endDate " +
           "AND le.department = :department")
    BigDecimal sumDebitsByAccountAndDateRangeAndDepartment(
            @Param("account") Account account,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") Department department);

    @Query("SELECT COALESCE(SUM(le.amountCr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate BETWEEN :startDate AND :endDate " +
           "AND le.department = :department")
    BigDecimal sumCreditsByAccountAndDateRangeAndDepartment(
            @Param("account") Account account,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") Department department);

    @Query("SELECT COALESCE(SUM(le.amountDr) - SUM(le.amountCr), 0) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryDate BETWEEN :startDate AND :endDate " +
           "AND le.department = :department")
    BigDecimal getBalanceByAccountAndDateRangeAndDepartment(
            @Param("account") Account account,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") Department department);
}
