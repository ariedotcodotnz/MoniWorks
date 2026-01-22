package com.example.application.repository;

import com.example.application.domain.SupplierBill;
import com.example.application.domain.SupplierBillLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierBillLineRepository extends JpaRepository<SupplierBillLine, Long> {

    List<SupplierBillLine> findByBillOrderByLineIndex(SupplierBill bill);

    void deleteByBill(SupplierBill bill);
}
