package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.SalesInvoice.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    List<SalesInvoice> findByCompanyOrderByIssueDateDescInvoiceNumberDesc(Company company);

    List<SalesInvoice> findByCompanyAndStatusOrderByIssueDateDesc(Company company, InvoiceStatus status);

    List<SalesInvoice> findByCompanyAndContactOrderByIssueDateDesc(Company company, Contact contact);

    Optional<SalesInvoice> findByCompanyAndInvoiceNumber(Company company, String invoiceNumber);

    boolean existsByCompanyAndInvoiceNumber(Company company, String invoiceNumber);

    // Overdue invoices (status = ISSUED, due_date < today, balance > 0)
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND i.status = 'ISSUED' " +
           "AND i.dueDate < :today AND (i.total - i.amountPaid) > 0 " +
           "ORDER BY i.dueDate ASC")
    List<SalesInvoice> findOverdueByCompany(@Param("company") Company company,
                                            @Param("today") LocalDate today);

    // Outstanding invoices (status = ISSUED, balance > 0)
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND i.status = 'ISSUED' " +
           "AND (i.total - i.amountPaid) > 0 ORDER BY i.dueDate ASC")
    List<SalesInvoice> findOutstandingByCompany(@Param("company") Company company);

    // Outstanding invoices for a specific customer
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND i.contact = :contact " +
           "AND i.status = 'ISSUED' AND (i.total - i.amountPaid) > 0 ORDER BY i.dueDate ASC")
    List<SalesInvoice> findOutstandingByCompanyAndContact(@Param("company") Company company,
                                                          @Param("contact") Contact contact);

    // Total outstanding AR balance
    @Query("SELECT COALESCE(SUM(i.total - i.amountPaid), 0) FROM SalesInvoice i " +
           "WHERE i.company = :company AND i.status = 'ISSUED' AND (i.total - i.amountPaid) > 0")
    BigDecimal sumOutstandingByCompany(@Param("company") Company company);

    // Total overdue AR balance
    @Query("SELECT COALESCE(SUM(i.total - i.amountPaid), 0) FROM SalesInvoice i " +
           "WHERE i.company = :company AND i.status = 'ISSUED' AND i.dueDate < :today " +
           "AND (i.total - i.amountPaid) > 0")
    BigDecimal sumOverdueByCompany(@Param("company") Company company,
                                    @Param("today") LocalDate today);

    // Invoices by date range
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company " +
           "AND i.issueDate >= :startDate AND i.issueDate <= :endDate " +
           "ORDER BY i.issueDate DESC, i.invoiceNumber DESC")
    List<SalesInvoice> findByCompanyAndDateRange(@Param("company") Company company,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // Search invoices by number or contact name
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND " +
           "(LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.contact.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.reference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY i.issueDate DESC")
    List<SalesInvoice> searchByCompany(@Param("company") Company company,
                                        @Param("search") String search);

    // Get all invoice numbers for a company (for auto-numbering)
    @Query("SELECT i.invoiceNumber FROM SalesInvoice i WHERE i.company = :company")
    List<String> findAllInvoiceNumbersByCompany(@Param("company") Company company);

    // Count by status for dashboard
    @Query("SELECT COUNT(i) FROM SalesInvoice i WHERE i.company = :company AND i.status = :status")
    long countByCompanyAndStatus(@Param("company") Company company, @Param("status") InvoiceStatus status);

    // Find credit notes for an original invoice
    List<SalesInvoice> findByOriginalInvoice(SalesInvoice originalInvoice);

    // Find only invoices (not credit notes) for a company
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND i.type = 'INVOICE' " +
           "ORDER BY i.issueDate DESC, i.invoiceNumber DESC")
    List<SalesInvoice> findInvoicesByCompany(@Param("company") Company company);

    // Find only credit notes for a company
    @Query("SELECT i FROM SalesInvoice i WHERE i.company = :company AND i.type = 'CREDIT_NOTE' " +
           "ORDER BY i.issueDate DESC, i.invoiceNumber DESC")
    List<SalesInvoice> findCreditNotesByCompany(@Param("company") Company company);
}
