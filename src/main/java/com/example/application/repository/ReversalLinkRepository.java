package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.ReversalLink;
import com.example.application.domain.Transaction;

/**
 * Repository for ReversalLink entities. Provides methods to query reversal relationships between
 * transactions.
 */
@Repository
public interface ReversalLinkRepository extends JpaRepository<ReversalLink, Long> {

  /** Find the reversal link where this transaction was reversed. */
  Optional<ReversalLink> findByOriginalTransaction(Transaction originalTransaction);

  /** Find the reversal link where this transaction is a reversal. */
  Optional<ReversalLink> findByReversingTransaction(Transaction reversingTransaction);

  /** Check if a transaction has been reversed. */
  boolean existsByOriginalTransaction(Transaction originalTransaction);

  /** Check if a transaction is a reversal of another transaction. */
  boolean existsByReversingTransaction(Transaction reversingTransaction);

  /** Find all reversals for a given original transaction (in case of multiple reversals). */
  List<ReversalLink> findAllByOriginalTransaction(Transaction originalTransaction);

  /** Find the reversal link by original transaction ID. */
  @Query("SELECT rl FROM ReversalLink rl WHERE rl.originalTransaction.id = :transactionId")
  Optional<ReversalLink> findByOriginalTransactionId(@Param("transactionId") Long transactionId);

  /** Find the reversal link by reversing transaction ID. */
  @Query("SELECT rl FROM ReversalLink rl WHERE rl.reversingTransaction.id = :transactionId")
  Optional<ReversalLink> findByReversingTransactionId(@Param("transactionId") Long transactionId);
}
