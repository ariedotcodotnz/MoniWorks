package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.RecurringTemplate.*;
import com.example.application.repository.RecurrenceExecutionLogRepository;
import com.example.application.repository.RecurringTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing recurring transaction templates and executing scheduled recurrences.
 */
@Service
@Transactional
public class RecurringTemplateService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTemplateService.class);

    private final RecurringTemplateRepository templateRepository;
    private final RecurrenceExecutionLogRepository logRepository;
    private final TransactionService transactionService;
    private final PostingService postingService;
    private final SalesInvoiceService salesInvoiceService;
    private final SupplierBillService supplierBillService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RecurringTemplateService(RecurringTemplateRepository templateRepository,
                                     RecurrenceExecutionLogRepository logRepository,
                                     TransactionService transactionService,
                                     PostingService postingService,
                                     SalesInvoiceService salesInvoiceService,
                                     SupplierBillService supplierBillService,
                                     AccountService accountService,
                                     AuditService auditService) {
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.transactionService = transactionService;
        this.postingService = postingService;
        this.salesInvoiceService = salesInvoiceService;
        this.supplierBillService = supplierBillService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }

    // CRUD Operations

    public RecurringTemplate save(RecurringTemplate template) {
        return templateRepository.save(template);
    }

    public Optional<RecurringTemplate> findById(Long id) {
        return templateRepository.findById(id);
    }

    public List<RecurringTemplate> findByCompany(Long companyId) {
        return templateRepository.findByCompanyIdOrderByNameAsc(companyId);
    }

    public List<RecurringTemplate> findByCompanyAndStatus(Long companyId, Status status) {
        return templateRepository.findByCompanyIdAndStatusOrderByNameAsc(companyId, status);
    }

    public List<RecurringTemplate> findByCompanyAndType(Long companyId, TemplateType type) {
        return templateRepository.findByCompanyIdAndTemplateTypeOrderByNameAsc(companyId, type);
    }

    public List<RecurringTemplate> searchByName(Long companyId, String search) {
        return templateRepository.searchByName(companyId, search);
    }

    public List<RecurringTemplate> findDueTemplates(Long companyId, LocalDate date) {
        return templateRepository.findDueTemplates(companyId, Status.ACTIVE, date);
    }

    public long countDueTemplates(Long companyId) {
        return templateRepository.countDueTemplates(companyId, Status.ACTIVE, LocalDate.now());
    }

    public boolean existsByName(Long companyId, String name) {
        return templateRepository.existsByCompanyIdAndName(companyId, name);
    }

    /**
     * Create a new recurring template.
     */
    public RecurringTemplate createTemplate(Company company, String name, TemplateType templateType,
                                             Frequency frequency, LocalDate startDate,
                                             String payloadJson, User createdBy) {
        if (existsByName(company.getId(), name)) {
            throw new IllegalArgumentException("A template with this name already exists");
        }

        RecurringTemplate template = new RecurringTemplate(company, name, templateType, payloadJson, frequency, startDate);
        template.setCreatedBy(createdBy);
        template = templateRepository.save(template);

        auditService.logEvent(company, createdBy, "RECURRING_TEMPLATE_CREATE", "RecurringTemplate",
                template.getId(), "Created recurring template: " + name);

        return template;
    }

    /**
     * Create a recurring template from an existing transaction.
     */
    public RecurringTemplate createFromTransaction(Transaction transaction, String name,
                                                    Frequency frequency, LocalDate startDate,
                                                    User createdBy) {
        String payloadJson = serializeTransactionPayload(transaction);

        RecurringTemplate template = createTemplate(
                transaction.getCompany(), name,
                mapTransactionTypeToTemplateType(transaction.getType()),
                frequency, startDate, payloadJson, createdBy);

        template.setSourceEntityId(transaction.getId());
        template.setBankAccount(findBankAccountFromTransaction(transaction));

        return templateRepository.save(template);
    }

    /**
     * Create a recurring template from an existing invoice.
     */
    public RecurringTemplate createFromInvoice(SalesInvoice invoice, String name,
                                                Frequency frequency, LocalDate startDate,
                                                User createdBy) {
        String payloadJson = serializeInvoicePayload(invoice);

        RecurringTemplate template = createTemplate(
                invoice.getCompany(), name, TemplateType.INVOICE,
                frequency, startDate, payloadJson, createdBy);

        template.setSourceEntityId(invoice.getId());
        template.setContact(invoice.getContact());

        return templateRepository.save(template);
    }

    /**
     * Create a recurring template from an existing supplier bill.
     */
    public RecurringTemplate createFromBill(SupplierBill bill, String name,
                                             Frequency frequency, LocalDate startDate,
                                             User createdBy) {
        String payloadJson = serializeBillPayload(bill);

        RecurringTemplate template = createTemplate(
                bill.getCompany(), name, TemplateType.BILL,
                frequency, startDate, payloadJson, createdBy);

        template.setSourceEntityId(bill.getId());
        template.setContact(bill.getContact());

        return templateRepository.save(template);
    }

    /**
     * Pause a recurring template.
     */
    public RecurringTemplate pause(RecurringTemplate template, User actor) {
        template.pause();
        template = templateRepository.save(template);

        auditService.logEvent(template.getCompany(), actor, "RECURRING_TEMPLATE_PAUSE", "RecurringTemplate",
                template.getId(), "Paused recurring template: " + template.getName());

        return template;
    }

    /**
     * Resume a paused recurring template.
     */
    public RecurringTemplate resume(RecurringTemplate template, User actor) {
        template.resume(LocalDate.now());
        template = templateRepository.save(template);

        auditService.logEvent(template.getCompany(), actor, "RECURRING_TEMPLATE_RESUME", "RecurringTemplate",
                template.getId(), "Resumed recurring template: " + template.getName());

        return template;
    }

    /**
     * Cancel a recurring template permanently.
     */
    public RecurringTemplate cancel(RecurringTemplate template, User actor) {
        template.cancel();
        template = templateRepository.save(template);

        auditService.logEvent(template.getCompany(), actor, "RECURRING_TEMPLATE_CANCEL", "RecurringTemplate",
                template.getId(), "Cancelled recurring template: " + template.getName());

        return template;
    }

    /**
     * Delete a recurring template and its execution logs.
     */
    public void delete(RecurringTemplate template, User actor) {
        auditService.logEvent(template.getCompany(), actor, "RECURRING_TEMPLATE_DELETE", "RecurringTemplate",
                template.getId(), "Deleted recurring template: " + template.getName());

        logRepository.deleteByTemplate(template);
        templateRepository.delete(template);
    }

    // Execution History

    public List<RecurrenceExecutionLog> getExecutionHistory(RecurringTemplate template) {
        return logRepository.findByTemplateOrderByRunAtDesc(template);
    }

    public Optional<RecurrenceExecutionLog> getLastExecution(RecurringTemplate template) {
        return logRepository.findTopByTemplateOrderByRunAtDesc(template);
    }

    public long countRecentFailures(Long companyId, int days) {
        return logRepository.countRecentFailures(companyId,
                java.time.Instant.now().minus(java.time.Duration.ofDays(days)));
    }

    // Scheduled Execution

    /**
     * Run due recurring templates for all companies.
     * Scheduled to run daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runScheduledRecurrences() {
        log.info("Running scheduled recurring template execution");
        LocalDate today = LocalDate.now();
        List<RecurringTemplate> dueTemplates = templateRepository.findAllDueTemplates(Status.ACTIVE, today);

        for (RecurringTemplate template : dueTemplates) {
            try {
                executeTemplate(template, null);
            } catch (Exception e) {
                log.error("Failed to execute recurring template {}: {}", template.getId(), e.getMessage());
            }
        }
    }

    /**
     * Manually execute a recurring template (run now).
     */
    public RecurrenceExecutionLog executeNow(RecurringTemplate template, User actor) {
        return executeTemplate(template, actor);
    }

    /**
     * Execute a recurring template to generate the next entity.
     */
    private RecurrenceExecutionLog executeTemplate(RecurringTemplate template, User actor) {
        LocalDate today = LocalDate.now();
        RecurrenceExecutionLog logEntry;

        try {
            Long createdEntityId = switch (template.getTemplateType()) {
                case PAYMENT, RECEIPT, JOURNAL -> executeTransactionTemplate(template, actor);
                case INVOICE -> executeInvoiceTemplate(template, actor);
                case BILL -> executeBillTemplate(template, actor);
            };

            logEntry = RecurrenceExecutionLog.success(template, createdEntityId, template.getTemplateType());
            template.recordExecution(today);

            log.info("Successfully executed recurring template {} (ID: {}), created entity ID: {}",
                    template.getName(), template.getId(), createdEntityId);

        } catch (Exception e) {
            log.error("Error executing recurring template {}: {}", template.getId(), e.getMessage(), e);
            logEntry = RecurrenceExecutionLog.failure(template, e.getMessage());
        }

        logRepository.save(logEntry);
        templateRepository.save(template);

        return logEntry;
    }

    /**
     * Execute a transaction-type template (Payment, Receipt, Journal).
     */
    private Long executeTransactionTemplate(RecurringTemplate template, User actor) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(template.getPayloadJson());

        Transaction.TransactionType txType = switch (template.getTemplateType()) {
            case PAYMENT -> Transaction.TransactionType.PAYMENT;
            case RECEIPT -> Transaction.TransactionType.RECEIPT;
            default -> Transaction.TransactionType.JOURNAL;
        };

        // Create transaction
        LocalDate txDate = template.getNextRunDate();
        String description = payload.has("description") ? payload.get("description").asText() : template.getName();

        Transaction transaction = transactionService.createTransaction(
                template.getCompany(), txType, txDate, description, actor);

        if (payload.has("reference")) {
            transaction.setReference(payload.get("reference").asText());
        }

        // Add lines
        ArrayNode lines = (ArrayNode) payload.get("lines");
        if (lines != null) {
            for (JsonNode lineNode : lines) {
                Long accountId = lineNode.get("accountId").asLong();
                BigDecimal amount = new BigDecimal(lineNode.get("amount").asText());
                String direction = lineNode.get("direction").asText();
                String taxCode = lineNode.has("taxCode") ? lineNode.get("taxCode").asText() : null;
                String memo = lineNode.has("memo") ? lineNode.get("memo").asText() : null;

                Account account = accountService.findById(accountId)
                        .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

                transactionService.addLine(transaction, account, amount,
                        TransactionLine.Direction.valueOf(direction), taxCode, memo);
            }
        }

        // Auto-post if configured
        if (template.getExecutionMode() == ExecutionMode.AUTO_POST) {
            postingService.postTransaction(transaction, actor);
        }

        auditService.logEvent(template.getCompany(), actor, "TRANSACTION_CREATED_FROM_RECURRING", "Transaction",
                transaction.getId(), "Created from recurring template: " + template.getName());

        return transaction.getId();
    }

    /**
     * Execute an invoice-type template.
     */
    private Long executeInvoiceTemplate(RecurringTemplate template, User actor) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(template.getPayloadJson());

        LocalDate issueDate = template.getNextRunDate();
        int dueDays = payload.has("dueDays") ? payload.get("dueDays").asInt() : 30;
        LocalDate dueDate = issueDate.plusDays(dueDays);

        SalesInvoice invoice = salesInvoiceService.createInvoice(
                template.getCompany(), template.getContact(), issueDate, dueDate, actor);

        // Add lines
        ArrayNode lines = (ArrayNode) payload.get("lines");
        if (lines != null) {
            for (JsonNode lineNode : lines) {
                Long accountId = lineNode.get("accountId").asLong();
                String lineDescription = lineNode.has("description") ? lineNode.get("description").asText() : "";
                BigDecimal quantity = new BigDecimal(lineNode.get("quantity").asText());
                BigDecimal unitPrice = new BigDecimal(lineNode.get("unitPrice").asText());
                String taxCode = lineNode.has("taxCode") ? lineNode.get("taxCode").asText() : null;

                Account account = accountService.findById(accountId)
                        .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

                salesInvoiceService.addLine(invoice, account, lineDescription, quantity, unitPrice, taxCode);
            }
        }

        // Auto-issue if configured
        if (template.getExecutionMode() == ExecutionMode.AUTO_POST) {
            salesInvoiceService.issueInvoice(invoice, actor);
        }

        auditService.logEvent(template.getCompany(), actor, "INVOICE_CREATED_FROM_RECURRING", "SalesInvoice",
                invoice.getId(), "Created from recurring template: " + template.getName());

        return invoice.getId();
    }

    /**
     * Execute a bill-type template.
     */
    private Long executeBillTemplate(RecurringTemplate template, User actor) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(template.getPayloadJson());

        LocalDate billDate = template.getNextRunDate();
        int dueDays = payload.has("dueDays") ? payload.get("dueDays").asInt() : 30;
        LocalDate dueDate = billDate.plusDays(dueDays);

        SupplierBill bill = supplierBillService.createBill(
                template.getCompany(), template.getContact(), billDate, dueDate, actor);

        // Add lines
        ArrayNode lines = (ArrayNode) payload.get("lines");
        if (lines != null) {
            for (JsonNode lineNode : lines) {
                Long accountId = lineNode.get("accountId").asLong();
                String lineDescription = lineNode.has("description") ? lineNode.get("description").asText() : "";
                BigDecimal quantity = new BigDecimal(lineNode.get("quantity").asText());
                BigDecimal unitPrice = new BigDecimal(lineNode.get("unitPrice").asText());
                String taxCode = lineNode.has("taxCode") ? lineNode.get("taxCode").asText() : null;

                Account account = accountService.findById(accountId)
                        .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

                supplierBillService.addLine(bill, account, lineDescription, quantity, unitPrice, taxCode);
            }
        }

        // Auto-post if configured
        if (template.getExecutionMode() == ExecutionMode.AUTO_POST) {
            supplierBillService.postBill(bill, actor);
        }

        auditService.logEvent(template.getCompany(), actor, "BILL_CREATED_FROM_RECURRING", "SupplierBill",
                bill.getId(), "Created from recurring template: " + template.getName());

        return bill.getId();
    }

    // Serialization helpers

    private String serializeTransactionPayload(Transaction transaction) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("description", transaction.getDescription());
            if (transaction.getReference() != null) {
                payload.put("reference", transaction.getReference());
            }

            ArrayNode lines = objectMapper.createArrayNode();
            for (TransactionLine line : transaction.getLines()) {
                ObjectNode lineNode = objectMapper.createObjectNode();
                lineNode.put("accountId", line.getAccount().getId());
                lineNode.put("amount", line.getAmount().toPlainString());
                lineNode.put("direction", line.getDirection().name());
                if (line.getTaxCode() != null) {
                    lineNode.put("taxCode", line.getTaxCode());
                }
                if (line.getMemo() != null) {
                    lineNode.put("memo", line.getMemo());
                }
                lines.add(lineNode);
            }
            payload.set("lines", lines);

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize transaction payload", e);
        }
    }

    private String serializeInvoicePayload(SalesInvoice invoice) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("dueDays", (int) java.time.temporal.ChronoUnit.DAYS.between(
                    invoice.getIssueDate(), invoice.getDueDate()));

            ArrayNode lines = objectMapper.createArrayNode();
            for (SalesInvoiceLine line : invoice.getLines()) {
                ObjectNode lineNode = objectMapper.createObjectNode();
                lineNode.put("accountId", line.getAccount().getId());
                lineNode.put("description", line.getDescription());
                lineNode.put("quantity", line.getQuantity().toPlainString());
                lineNode.put("unitPrice", line.getUnitPrice().toPlainString());
                if (line.getTaxCode() != null) {
                    lineNode.put("taxCode", line.getTaxCode());
                }
                lines.add(lineNode);
            }
            payload.set("lines", lines);

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize invoice payload", e);
        }
    }

    private String serializeBillPayload(SupplierBill bill) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("dueDays", (int) java.time.temporal.ChronoUnit.DAYS.between(
                    bill.getBillDate(), bill.getDueDate()));

            ArrayNode lines = objectMapper.createArrayNode();
            for (SupplierBillLine line : bill.getLines()) {
                ObjectNode lineNode = objectMapper.createObjectNode();
                lineNode.put("accountId", line.getAccount().getId());
                lineNode.put("description", line.getDescription());
                lineNode.put("quantity", line.getQuantity().toPlainString());
                lineNode.put("unitPrice", line.getUnitPrice().toPlainString());
                if (line.getTaxCode() != null) {
                    lineNode.put("taxCode", line.getTaxCode());
                }
                lines.add(lineNode);
            }
            payload.set("lines", lines);

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize bill payload", e);
        }
    }

    private TemplateType mapTransactionTypeToTemplateType(Transaction.TransactionType txType) {
        return switch (txType) {
            case PAYMENT -> TemplateType.PAYMENT;
            case RECEIPT -> TemplateType.RECEIPT;
            case JOURNAL, TRANSFER -> TemplateType.JOURNAL;
        };
    }

    private Account findBankAccountFromTransaction(Transaction transaction) {
        // Find the first bank account in the transaction lines
        for (TransactionLine line : transaction.getLines()) {
            if (line.getAccount().isBankAccount()) {
                return line.getAccount();
            }
        }
        return null;
    }
}
