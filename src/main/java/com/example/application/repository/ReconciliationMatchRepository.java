package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.BankFeedItem;
import com.example.application.domain.Company;
import com.example.application.domain.ReconciliationMatch;
import com.example.application.domain.Transaction;

/**
 * Repository for ReconciliationMatch entity. Provides queries for tracking and auditing bank
 * reconciliation matches per spec 05.
 */
@Repository
public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {

  /** Find all active matches for a company. */
  List<ReconciliationMatch> findByCompanyAndActiveTrue(Company company);

  /** Find all matches (including inactive) for a company. */
  List<ReconciliationMatch> findByCompany(Company company);

  /** Find active match for a specific bank feed item. */
  Optional<ReconciliationMatch> findByBankFeedItemAndActiveTrue(BankFeedItem bankFeedItem);

  /** Find all matches (including inactive) for a bank feed item, for audit history. */
  List<ReconciliationMatch> findByBankFeedItemOrderByMatchedAtDesc(BankFeedItem bankFeedItem);

  /** Find active matches for a transaction. */
  List<ReconciliationMatch> findByTransactionAndActiveTrue(Transaction transaction);

  /** Find all matches (including inactive) for a transaction, for audit history. */
  List<ReconciliationMatch> findByTransactionOrderByMatchedAtDesc(Transaction transaction);

  /** Count active matches by match type for a company (for statistics). */
  @Query(
      "SELECT rm.matchType, COUNT(rm) FROM ReconciliationMatch rm "
          + "WHERE rm.company = :company AND rm.active = true "
          + "GROUP BY rm.matchType")
  List<Object[]> countByMatchTypeForCompany(@Param("company") Company company);

  /** Check if a bank feed item already has an active match. */
  boolean existsByBankFeedItemAndActiveTrue(BankFeedItem bankFeedItem);

  /** Find recently matched items for a company (for dashboard/reporting). */
  @Query(
      "SELECT rm FROM ReconciliationMatch rm "
          + "WHERE rm.company = :company AND rm.active = true "
          + "ORDER BY rm.matchedAt DESC")
  List<ReconciliationMatch> findRecentMatchesByCompany(
      @Param("company") Company company, org.springframework.data.domain.Pageable pageable);
}
