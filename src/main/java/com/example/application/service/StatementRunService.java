package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.AttachmentLink.EntityType;
import com.example.application.domain.StatementRun.RunStatus;
import com.example.application.repository.SalesInvoiceRepository;
import com.example.application.repository.StatementRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing statement runs (batch statement generation).
 * Creates statements for multiple customers based on filter criteria.
 */
@Service
@Transactional
public class StatementRunService {

    private static final Logger log = LoggerFactory.getLogger(StatementRunService.class);

    private final StatementRunRepository runRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final ContactService contactService;
    private final StatementService statementService;
    private final AttachmentService attachmentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public StatementRunService(StatementRunRepository runRepository,
                               SalesInvoiceRepository invoiceRepository,
                               ContactService contactService,
                               StatementService statementService,
                               AttachmentService attachmentService,
                               AuditService auditService) {
        this.runRepository = runRepository;
        this.invoiceRepository = invoiceRepository;
        this.contactService = contactService;
        this.statementService = statementService;
        this.attachmentService = attachmentService;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Criteria for filtering which customers to include in a statement run.
     */
    public record StatementCriteria(
        BigDecimal minimumBalance,          // Only customers with balance >= this amount
        Integer minimumDaysOverdue,         // Only customers with invoices overdue >= this many days
        List<Long> contactIds,              // Specific customer IDs (if empty, all customers with balances)
        boolean includeZeroBalance,         // Include customers with zero balance (rarely wanted)
        StatementService.StatementType statementType,  // Open-item or balance-forward (spec 09)
        LocalDate periodStart               // For balance-forward: start of period (end is asOfDate)
    ) {
        public static StatementCriteria defaultCriteria() {
            return new StatementCriteria(BigDecimal.ZERO, null, List.of(), false,
                StatementService.StatementType.OPEN_ITEM, null);
        }

        public static StatementCriteria openItemCriteria(BigDecimal minimumBalance,
                Integer minimumDaysOverdue, List<Long> contactIds, boolean includeZeroBalance) {
            return new StatementCriteria(minimumBalance, minimumDaysOverdue, contactIds, includeZeroBalance,
                StatementService.StatementType.OPEN_ITEM, null);
        }

        public static StatementCriteria balanceForwardCriteria(LocalDate periodStart,
                List<Long> contactIds, boolean includeZeroBalance) {
            return new StatementCriteria(null, null, contactIds, includeZeroBalance,
                StatementService.StatementType.BALANCE_FORWARD, periodStart);
        }
    }

    /**
     * Creates a new statement run.
     *
     * @param company The company
     * @param asOfDate The date to generate statements as of
     * @param criteria Filter criteria for customers
     * @param createdBy The user creating the run
     * @return The created statement run (in PENDING status)
     */
    public StatementRun createRun(Company company, LocalDate asOfDate, StatementCriteria criteria, User createdBy) {
        StatementRun run = new StatementRun(company, asOfDate);
        run.setCreatedBy(createdBy);

        // Store criteria as JSON
        try {
            ObjectNode criteriaNode = objectMapper.createObjectNode();
            if (criteria.minimumBalance() != null) {
                criteriaNode.put("minimumBalance", criteria.minimumBalance().toPlainString());
            }
            if (criteria.minimumDaysOverdue() != null) {
                criteriaNode.put("minimumDaysOverdue", criteria.minimumDaysOverdue());
            }
            if (criteria.contactIds() != null && !criteria.contactIds().isEmpty()) {
                criteriaNode.putPOJO("contactIds", criteria.contactIds());
            }
            criteriaNode.put("includeZeroBalance", criteria.includeZeroBalance());
            // Statement type (spec 09 - balance-forward support)
            if (criteria.statementType() != null) {
                criteriaNode.put("statementType", criteria.statementType().name());
            }
            if (criteria.periodStart() != null) {
                criteriaNode.put("periodStart", criteria.periodStart().toString());
            }
            run.setCriteriaJson(objectMapper.writeValueAsString(criteriaNode));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize criteria JSON", e);
        }

        run = runRepository.save(run);

        auditService.logEvent(company, createdBy, "STATEMENT_RUN_CREATED", "StatementRun", run.getId(),
            "Created statement run for date " + asOfDate);

        return run;
    }

    /**
     * Processes a statement run, generating PDFs for all matching customers.
     * Combines all statements into a single PDF attachment.
     *
     * @param run The statement run to process
     * @param user The user processing the run
     * @return The updated run with completion status
     */
    public StatementRun processRun(StatementRun run, User user) {
        if (run.getStatus() != RunStatus.PENDING) {
            throw new IllegalStateException("Statement run is not in PENDING status");
        }

        Company company = run.getCompany();
        LocalDate asOfDate = run.getAsOfDate();

        run.setStatus(RunStatus.PROCESSING);
        run = runRepository.save(run);

        try {
            // Parse criteria
            StatementCriteria criteria = parseCriteria(run.getCriteriaJson());

            // Find customers with outstanding balances
            List<Contact> customersWithBalances = findCustomersWithBalances(company, asOfDate, criteria);

            if (customersWithBalances.isEmpty()) {
                run.setStatus(RunStatus.COMPLETED);
                run.setStatementCount(0);
                run.setCompletedAt(Instant.now());
                run = runRepository.save(run);

                auditService.logEvent(company, user, "STATEMENT_RUN_COMPLETED", "StatementRun", run.getId(),
                    "No customers with outstanding balances");

                return run;
            }

            // Generate combined PDF with all statements (spec 09 - open-item or balance-forward)
            byte[] combinedPdf;
            String filename;
            if (criteria.statementType() == StatementService.StatementType.BALANCE_FORWARD) {
                // Balance-forward statements show activity over a period
                LocalDate periodStart = criteria.periodStart() != null
                    ? criteria.periodStart()
                    : asOfDate.minusMonths(1);
                combinedPdf = generateCombinedBalanceForwardPdf(company, customersWithBalances, periodStart, asOfDate);
                filename = "BalanceForward_Statements_" + periodStart.toString() + "_to_" + asOfDate.toString() + ".pdf";
            } else {
                // Open-item statements (default) show outstanding invoices
                combinedPdf = generateCombinedStatementsPdf(company, customersWithBalances, asOfDate);
                filename = "Statements_" + asOfDate.toString() + ".pdf";
            }
            Attachment attachment = attachmentService.uploadAndLink(
                company, filename, "application/pdf", combinedPdf, user,
                EntityType.STATEMENT_RUN, run.getId()
            );

            run.setOutputAttachmentId(attachment.getId());
            run.setStatementCount(customersWithBalances.size());
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            run = runRepository.save(run);

            auditService.logEvent(company, user, "STATEMENT_RUN_COMPLETED", "StatementRun", run.getId(),
                "Generated " + customersWithBalances.size() + " statements");

            log.info("Statement run {} completed: {} statements generated",
                run.getId(), customersWithBalances.size());

        } catch (Exception e) {
            log.error("Statement run {} failed", run.getId(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
            run = runRepository.save(run);

            auditService.logEvent(company, user, "STATEMENT_RUN_FAILED", "StatementRun", run.getId(),
                "Failed: " + e.getMessage());
        }

        return run;
    }

    /**
     * Creates and immediately processes a statement run.
     */
    public StatementRun createAndProcessRun(Company company, LocalDate asOfDate, StatementCriteria criteria, User user) {
        StatementRun run = createRun(company, asOfDate, criteria, user);
        return processRun(run, user);
    }

    /**
     * Gets a single customer's statement PDF.
     * Convenience method that wraps StatementService.
     */
    public byte[] generateSingleStatement(Company company, Contact contact, LocalDate asOfDate) {
        return statementService.generateStatementPdf(company, contact, asOfDate);
    }

    /**
     * Finds all customers with outstanding balances matching the criteria.
     */
    private List<Contact> findCustomersWithBalances(Company company, LocalDate asOfDate, StatementCriteria criteria) {
        // If specific contact IDs provided, use those
        if (criteria.contactIds() != null && !criteria.contactIds().isEmpty()) {
            return criteria.contactIds().stream()
                .map(id -> contactService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(c -> c.getCompany().getId().equals(company.getId()))
                .filter(c -> hasOutstandingBalance(company, c, asOfDate, criteria))
                .toList();
        }

        // Otherwise, find all customers with balances
        List<Contact> allCustomers = contactService.findActiveCustomers(company);

        return allCustomers.stream()
            .filter(c -> hasOutstandingBalance(company, c, asOfDate, criteria))
            .sorted(Comparator.comparing(Contact::getCode))
            .toList();
    }

    /**
     * Checks if a customer has outstanding balance matching criteria.
     */
    private boolean hasOutstandingBalance(Company company, Contact contact, LocalDate asOfDate, StatementCriteria criteria) {
        StatementService.StatementData data = statementService.generateStatementData(company, contact, asOfDate);

        // Check total balance
        BigDecimal totalBalance = data.totalBalance();
        if (totalBalance.compareTo(BigDecimal.ZERO) == 0 && !criteria.includeZeroBalance()) {
            return false;
        }

        // Check minimum balance
        if (criteria.minimumBalance() != null && totalBalance.compareTo(criteria.minimumBalance()) < 0) {
            return false;
        }

        // Check minimum days overdue
        if (criteria.minimumDaysOverdue() != null && criteria.minimumDaysOverdue() > 0) {
            boolean hasOverdue = data.lines().stream()
                .anyMatch(line -> line.daysOverdue() >= criteria.minimumDaysOverdue());
            if (!hasOverdue) {
                return false;
            }
        }

        return true;
    }

    /**
     * Generates a combined PDF containing statements for multiple customers.
     * Each customer starts on a new page.
     */
    private byte[] generateCombinedStatementsPdf(Company company, List<Contact> customers, LocalDate asOfDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            boolean firstPage = true;
            for (Contact customer : customers) {
                if (!firstPage) {
                    document.newPage();
                }
                firstPage = false;

                // Generate statement content for this customer
                StatementService.StatementData data = statementService.generateStatementData(company, customer, asOfDate);

                // Use a separate PDF for each customer, then merge
                byte[] customerPdf = statementService.generateStatementPdf(data);

                // Import the first page from the customer statement
                PdfReader reader = new PdfReader(customerPdf);
                int numPages = reader.getNumberOfPages();
                PdfContentByte cb = writer.getDirectContent();

                for (int page = 1; page <= numPages; page++) {
                    if (page > 1) {
                        document.newPage();
                    }
                    PdfImportedPage importedPage = writer.getImportedPage(reader, page);
                    cb.addTemplate(importedPage, 0, 0);
                }

                reader.close();
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate combined statements PDF", e);
            throw new RuntimeException("Failed to generate combined statements PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a combined PDF containing balance-forward statements for multiple customers.
     * Each customer starts on a new page.
     * Shows activity over a period with opening/closing balances (spec 09).
     */
    private byte[] generateCombinedBalanceForwardPdf(Company company, List<Contact> customers,
                                                      LocalDate periodStart, LocalDate periodEnd) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            boolean firstPage = true;
            for (Contact customer : customers) {
                if (!firstPage) {
                    document.newPage();
                }
                firstPage = false;

                // Generate balance-forward statement for this customer
                StatementService.BalanceForwardStatementData data =
                    statementService.generateBalanceForwardStatementData(company, customer, periodStart, periodEnd);

                // Generate PDF and merge
                byte[] customerPdf = statementService.generateBalanceForwardStatementPdf(data);

                // Import pages from the customer statement
                PdfReader reader = new PdfReader(customerPdf);
                int numPages = reader.getNumberOfPages();
                PdfContentByte cb = writer.getDirectContent();

                for (int page = 1; page <= numPages; page++) {
                    if (page > 1) {
                        document.newPage();
                    }
                    PdfImportedPage importedPage = writer.getImportedPage(reader, page);
                    cb.addTemplate(importedPage, 0, 0);
                }

                reader.close();
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate combined balance-forward statements PDF", e);
            throw new RuntimeException("Failed to generate combined balance-forward statements PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Parses criteria JSON back into a StatementCriteria object.
     */
    private StatementCriteria parseCriteria(String json) {
        if (json == null || json.isBlank()) {
            return StatementCriteria.defaultCriteria();
        }

        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);

            BigDecimal minimumBalance = null;
            if (node.has("minimumBalance")) {
                minimumBalance = new BigDecimal(node.get("minimumBalance").asText());
            }

            Integer minimumDaysOverdue = null;
            if (node.has("minimumDaysOverdue")) {
                minimumDaysOverdue = node.get("minimumDaysOverdue").asInt();
            }

            List<Long> contactIds = new ArrayList<>();
            if (node.has("contactIds")) {
                node.get("contactIds").forEach(n -> contactIds.add(n.asLong()));
            }

            boolean includeZeroBalance = node.has("includeZeroBalance") && node.get("includeZeroBalance").asBoolean();

            // Parse statement type (spec 09 - balance-forward support)
            StatementService.StatementType statementType = StatementService.StatementType.OPEN_ITEM;
            if (node.has("statementType")) {
                try {
                    statementType = StatementService.StatementType.valueOf(node.get("statementType").asText());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid statement type in criteria, using OPEN_ITEM");
                }
            }

            LocalDate periodStart = null;
            if (node.has("periodStart")) {
                periodStart = LocalDate.parse(node.get("periodStart").asText());
            }

            return new StatementCriteria(minimumBalance, minimumDaysOverdue, contactIds, includeZeroBalance,
                statementType, periodStart);

        } catch (Exception e) {
            log.warn("Failed to parse criteria JSON, using defaults", e);
            return StatementCriteria.defaultCriteria();
        }
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<StatementRun> findById(Long id) {
        return runRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<StatementRun> findByCompany(Company company) {
        return runRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    @Transactional(readOnly = true)
    public List<StatementRun> findByCompanyAndStatus(Company company, RunStatus status) {
        return runRepository.findByCompanyAndStatusOrderByCreatedAtDesc(company, status);
    }

    @Transactional(readOnly = true)
    public List<StatementRun> findByDateRange(Company company, LocalDate startDate, LocalDate endDate) {
        return runRepository.findByCompanyAndDateRange(company, startDate, endDate);
    }

    /**
     * Gets customers who would be included in a statement run with the given criteria.
     * Useful for previewing before running.
     */
    @Transactional(readOnly = true)
    public List<Contact> previewCustomers(Company company, LocalDate asOfDate, StatementCriteria criteria) {
        return findCustomersWithBalances(company, asOfDate, criteria);
    }
}
