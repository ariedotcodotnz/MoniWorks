package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.PayableAllocation;
import com.example.application.domain.SupplierBill;
import com.example.application.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PayableAllocationRepository extends JpaRepository<PayableAllocation, Long> {

    List<PayableAllocation> findBySupplierBill(SupplierBill supplierBill);

    List<PayableAllocation> findByPaymentTransaction(Transaction paymentTransaction);

    List<PayableAllocation> findByCompanyOrderByAllocatedAtDesc(Company company);

    // Total allocated to a specific bill
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM PayableAllocation a WHERE a.supplierBill = :bill")
    BigDecimal sumByBill(@Param("bill") SupplierBill bill);

    // Total allocated from a specific payment
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM PayableAllocation a WHERE a.paymentTransaction = :payment")
    BigDecimal sumByPayment(@Param("payment") Transaction payment);

    // Check if allocation exists
    boolean existsByPaymentTransactionAndSupplierBill(Transaction paymentTransaction, SupplierBill supplierBill);
}
