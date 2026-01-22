package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.SupplierBill;
import com.example.application.domain.SupplierBill.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierBillRepository extends JpaRepository<SupplierBill, Long> {

    List<SupplierBill> findByCompanyOrderByBillDateDescBillNumberDesc(Company company);

    List<SupplierBill> findByCompanyAndStatusOrderByBillDateDesc(Company company, BillStatus status);

    List<SupplierBill> findByCompanyAndContactOrderByBillDateDesc(Company company, Contact contact);

    Optional<SupplierBill> findByCompanyAndBillNumber(Company company, String billNumber);

    boolean existsByCompanyAndBillNumber(Company company, String billNumber);

    // Overdue bills (status = POSTED, due_date < today, balance > 0)
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company AND b.status = 'POSTED' " +
           "AND b.dueDate < :today AND (b.total - b.amountPaid) > 0 " +
           "ORDER BY b.dueDate ASC")
    List<SupplierBill> findOverdueByCompany(@Param("company") Company company,
                                             @Param("today") LocalDate today);

    // Outstanding bills (status = POSTED, balance > 0)
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company AND b.status = 'POSTED' " +
           "AND (b.total - b.amountPaid) > 0 ORDER BY b.dueDate ASC")
    List<SupplierBill> findOutstandingByCompany(@Param("company") Company company);

    // Outstanding bills for a specific supplier
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company AND b.contact = :contact " +
           "AND b.status = 'POSTED' AND (b.total - b.amountPaid) > 0 ORDER BY b.dueDate ASC")
    List<SupplierBill> findOutstandingByCompanyAndContact(@Param("company") Company company,
                                                          @Param("contact") Contact contact);

    // Total outstanding AP balance
    @Query("SELECT COALESCE(SUM(b.total - b.amountPaid), 0) FROM SupplierBill b " +
           "WHERE b.company = :company AND b.status = 'POSTED' AND (b.total - b.amountPaid) > 0")
    BigDecimal sumOutstandingByCompany(@Param("company") Company company);

    // Total overdue AP balance
    @Query("SELECT COALESCE(SUM(b.total - b.amountPaid), 0) FROM SupplierBill b " +
           "WHERE b.company = :company AND b.status = 'POSTED' AND b.dueDate < :today " +
           "AND (b.total - b.amountPaid) > 0")
    BigDecimal sumOverdueByCompany(@Param("company") Company company,
                                    @Param("today") LocalDate today);

    // Bills by date range
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company " +
           "AND b.billDate >= :startDate AND b.billDate <= :endDate " +
           "ORDER BY b.billDate DESC, b.billNumber DESC")
    List<SupplierBill> findByCompanyAndDateRange(@Param("company") Company company,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // Search bills by number or contact name
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company AND " +
           "(LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(b.contact.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(b.supplierReference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY b.billDate DESC")
    List<SupplierBill> searchByCompany(@Param("company") Company company,
                                        @Param("search") String search);

    // Get all bill numbers for a company (for auto-numbering)
    @Query("SELECT b.billNumber FROM SupplierBill b WHERE b.company = :company")
    List<String> findAllBillNumbersByCompany(@Param("company") Company company);

    // Count by status for dashboard
    @Query("SELECT COUNT(b) FROM SupplierBill b WHERE b.company = :company AND b.status = :status")
    long countByCompanyAndStatus(@Param("company") Company company, @Param("status") BillStatus status);

    // Bills due within date range (for payment run selection)
    @Query("SELECT b FROM SupplierBill b WHERE b.company = :company AND b.status = 'POSTED' " +
           "AND (b.total - b.amountPaid) > 0 AND b.dueDate <= :dueBy " +
           "ORDER BY b.dueDate ASC, b.contact.name ASC")
    List<SupplierBill> findPayableBillsDueBy(@Param("company") Company company,
                                              @Param("dueBy") LocalDate dueBy);
}
