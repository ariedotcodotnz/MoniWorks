package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.ReceivableAllocation;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ReceivableAllocationRepository extends JpaRepository<ReceivableAllocation, Long> {

    List<ReceivableAllocation> findBySalesInvoice(SalesInvoice salesInvoice);

    List<ReceivableAllocation> findByReceiptTransaction(Transaction receiptTransaction);

    List<ReceivableAllocation> findByCompanyOrderByAllocatedAtDesc(Company company);

    // Total allocated to a specific invoice
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM ReceivableAllocation a WHERE a.salesInvoice = :invoice")
    BigDecimal sumByInvoice(@Param("invoice") SalesInvoice invoice);

    // Total allocated from a specific receipt
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM ReceivableAllocation a WHERE a.receiptTransaction = :receipt")
    BigDecimal sumByReceipt(@Param("receipt") Transaction receipt);

    // Check if allocation exists
    boolean existsByReceiptTransactionAndSalesInvoice(Transaction receiptTransaction, SalesInvoice salesInvoice);
}
