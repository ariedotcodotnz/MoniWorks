# MoniWorks Implementation Plan

## Current Status
- Project initialized with Spring Boot 4.0.1, Vaadin 25.0.3, Java 21
- **Phase 1 Foundation COMPLETE** - Tag: 0.0.1
- **Phase 2 Core Accounting COMPLETE** - Tag: 0.0.2
- **Phase 3 Tax & Bank COMPLETE** - Tag: 0.0.3
- **Phase 4 Reports & Polish COMPLETE** - Tag: 0.0.8
- **Phase 5 Contacts & Products COMPLETE** - Tag: 0.0.9
- **Phase 6 A/R and A/P COMPLETE** - Tag: 0.1.2
- **Phase 7 Budgeting & Departments COMPLETE** - Tag: 0.1.4
- **Phase 8 Recurring Transactions COMPLETE** - Tag: 0.1.5
- All 37 tests passing (PostingServiceTest: 7, ReportingServiceTest: 5, TaxCalculationServiceTest: 14, AttachmentServiceTest: 10, ApplicationTest: 1)
- Core domain entities created: Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, TaxLine, TaxReturn, TaxReturnLine, Department, Role, Permission, CompanyMembership, AuditEvent, BankStatementImport, BankFeedItem, AllocationRule, Attachment, AttachmentLink, Contact, ContactPerson, ContactNote, Product, SalesInvoice, SalesInvoiceLine, ReceivableAllocation, SupplierBill, SupplierBillLine, PayableAllocation, PaymentRun, Budget, BudgetLine, KPI, KPIValue, RecurringTemplate, RecurrenceExecutionLog
- Database configured: H2 for development, PostgreSQL for production
- Flyway migrations: V1__initial_schema.sql, V2__bank_accounts.sql, V3__tax_lines.sql, V4__tax_returns.sql, V5__attachments.sql, V6__contacts.sql, V7__products.sql, V8__sales_invoices.sql, V9__supplier_bills.sql, V10__budgets_kpis.sql, V11__rename_kpi_value_column.sql, V12__recurring_templates.sql
- All repository interfaces created (37 repositories)
- Full service layer: CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService, CompanyContextService, TaxCodeService, FiscalYearService, BankImportService, TaxCalculationService, TaxReturnService, AttachmentService, ContactService, ProductService, SalesInvoiceService, ReceivableAllocationService, SupplierBillService, PayableAllocationService, PaymentRunService, RemittanceAdviceService, DepartmentService, BudgetService, KPIService, RecurringTemplateService
- Full UI views: MainLayout, LoginView, DashboardView, TransactionsView, AccountsView, PeriodsView, TaxCodesView, ReportsView, BankReconciliationView, GstReturnsView, AuditEventsView, ContactsView, ProductsView, SalesInvoicesView, SupplierBillsView, DepartmentsView, BudgetsView, KPIsView, RecurringTemplatesView
- Security configuration with SecurityConfig and UserDetailsServiceImpl (using VaadinSecurityConfigurer API)

## Release 1 (SLC) - Target Features
Per specs, Release 1 must deliver:
1. Single company support (multi-tenancy foundation) - DONE
2. Chart of Accounts with hierarchical structure - DONE
3. Fiscal Years and Periods with lock/unlock - DONE
4. Cashbook transactions (Payments, Receipts, Journals) - DONE
5. Posting to immutable ledger - DONE
6. GST/Tax coding and returns - DONE (tax calculation, TaxLine entities, GST return generation)
7. Bank import (QIF/OFX) and reconciliation - DONE (import + reconciliation UI)
8. Financial reports (Trial Balance, P&L, Balance Sheet) - DONE
9. Attachments for source documents - DONE (PDF/image upload, link to transactions)
10. Audit trail - DONE (backend logging + AuditEventsView UI)

## Implementation Order

### Phase 1: Foundation (COMPLETE) - Tag: 0.0.1
- [x] Project setup with Maven, Spring Boot, Vaadin
- [x] Add database dependencies (PostgreSQL, H2, JPA, Flyway)
- [x] Configure database connections (H2 for dev, PostgreSQL for prod)
- [x] Create core domain entities
- [x] Create Flyway migrations (V1__initial_schema.sql)
- [x] Create repository layer
- [x] Create basic service layer
- [x] Create Vaadin UI shell with AppLayout
- [x] Create security configuration

### Phase 2: Core Accounting (COMPLETE) - Tag: 0.0.2
- [x] CompanyContextService for session-scoped company management
- [x] TaxCodeService for tax code management
- [x] FiscalYearService for fiscal year and period management
- [x] AccountsView with TreeGrid, search, add/edit dialogs
- [x] PeriodsView with fiscal year selection and period lock/unlock
- [x] TransactionsView with full CRUD, transaction lines grid, posting, reversals
- [x] PostingService with validation (balanced entries, open periods, active accounts)

### Phase 3: Tax & Bank (COMPLETE) - Tag: 0.0.3
- [x] Tax codes management UI
- [x] Tax calculation on transactions
- [x] Bank account linking (mark accounts as bank accounts)
- [x] Bank import (OFX/QIF parsing)
- [x] Reconciliation matching UI

### Phase 4: Reports & Polish (COMPLETE) - Tag: 0.0.8
- [x] Dashboard tiles (Cash Balance, This Month income/expenses, GST Estimate)
- [x] Trial Balance report view
- [x] P&L report view
- [x] Balance Sheet report view
- [x] GST return generation
- [x] Attachments support
- [x] Audit event logging UI

### Phase 5: Release 2 - Contacts & Products (COMPLETE) - Tag: 0.0.9
- [x] Contacts/Customers/Suppliers (spec 07)
  - Created Contact entity with code (max 11 chars), name, type (CUSTOMER/SUPPLIER/BOTH), category, colorTag
  - Full address fields (line1, line2, city, region, postalCode, country)
  - Contact details (phone, mobile, email, website)
  - Bank details for remittance (bankName, bankAccountNumber, bankRouting)
  - Tax override code for special tax handling (e.g., zero-rated exports)
  - Default account for GL allocation
  - Payment terms and credit limit
  - Created ContactPerson entity for multiple people per contact with name, email, phone, roleLabel, isPrimary
  - Created ContactNote entity for notes and follow-up reminders with noteText, followUpDate, createdBy
  - Created V6__contacts.sql migration with proper indexes
  - Created ContactRepository, ContactPersonRepository, ContactNoteRepository
  - Created ContactService with full CRUD, search, audit logging
  - Created ContactsView with master-detail split layout, searchable list, type filter
  - Detail view with tabs: General (info), People (CRUD), Defaults (accounts/terms), Notes (CRUD)
  - Added Contacts navigation to MainLayout with USERS icon
- [x] Products (spec 08 - non-inventory v1)
  - Created Product entity with code (max 31 chars), name, description, category
  - Pricing: buyPrice, sellPrice (BigDecimal 19,2)
  - Tax code reference for default tax handling
  - Default accounts: salesAccount (income), purchaseAccount (expense)
  - Barcode field, imageAttachmentId reference, isInventoried flag (for Phase 2)
  - Sticky note that appears when product selected on invoices/bills
  - Created V7__products.sql migration with proper indexes
  - Created ProductRepository with search and category queries
  - Created ProductService with full CRUD, search by barcode, audit logging
  - Created ProductsView with master-detail split layout, category filter
  - Detail view showing pricing, tax/accounts, other info, sticky note display
  - Added Products navigation to MainLayout with PACKAGE icon

### Phase 6: Release 2 - A/R and A/P (COMPLETE) - Tag: 0.1.2
- [x] Accounts Receivable (spec 09)
  - Created SalesInvoice and SalesInvoiceLine entities
  - SalesInvoice with invoiceNumber, contactId, issueDate, dueDate, status (DRAFT/ISSUED/VOID)
  - SalesInvoiceLine with product reference, description, qty, unitPrice, account, taxCode
  - Created ReceivableAllocation entity for receipt-to-invoice allocation
  - Created V8__sales_invoices.sql migration
  - Created SalesInvoiceRepository, SalesInvoiceLineRepository, ReceivableAllocationRepository
  - Created SalesInvoiceService with full CRUD, invoice issuing (posts to ledger), void capability
  - Created ReceivableAllocationService for payment allocation with auto-suggestion
  - Created SalesInvoicesView with master-detail layout, status filtering, line item management
  - Invoice issuing creates balanced journal entries (AR debit, Income credit, GST credit)
  - Added Sales Invoices navigation to MainLayout with INVOICE icon
- [x] Accounts Payable (spec 10)
  - Created SupplierBill and SupplierBillLine entities with status (DRAFT/POSTED/VOID)
  - SupplierBill with billNumber, contactId, billDate, dueDate, supplierReference
  - SupplierBillLine with product reference, description, qty, unitPrice, account, taxCode
  - Created PayableAllocation entity for payment-to-bill allocation
  - Created PaymentRun entity for batch supplier payments
  - Created V9__supplier_bills.sql migration with proper indexes
  - Created SupplierBillRepository, SupplierBillLineRepository, PayableAllocationRepository, PaymentRunRepository
  - Created SupplierBillService with full CRUD, bill posting (posts to ledger), void capability
  - Created PayableAllocationService for payment allocation with auto-suggestion
  - Created PaymentRunService for batch payment processing grouped by supplier
  - Created SupplierBillsView with master-detail layout, status filtering, line item management
  - Bill posting creates balanced journal entries (AP credit, Expense debit, GST paid debit)
  - Added Supplier Bills navigation to MainLayout with RECORDS icon
- [x] Dashboard Overdue AR/AP tiles
  - Added Overdue Receivables tile showing total overdue AR balance and up to 3 oldest overdue invoices
  - Added Overdue Payables tile showing total overdue AP balance and up to 3 oldest overdue bills
  - Uses existing repository methods: SalesInvoiceRepository.sumOverdueByCompany, SupplierBillRepository.sumOverdueByCompany
- [x] Remittance advice PDF generation (PaymentRun output)
  - Added OpenPDF library dependency for PDF generation
  - Created RemittanceAdviceService that generates professional PDFs
  - Each supplier gets their own page with company/supplier info, bill details table, and payment total
  - PDF automatically generated when PaymentRun is completed and attached to the run
  - Added PAYMENT_RUN to AttachmentLink.EntityType enum

### Phase 7: Budgeting & Departments (COMPLETE) - Tag: 0.1.3
- [x] Departments (spec 12)
  - Department entity already existed in domain with code (max 5 chars), name, groupName, classification, active
  - Created DepartmentService with full CRUD, audit logging, createDefaultDepartments
  - Created DepartmentsView with grid, add/edit/deactivate dialogs, search, create defaults button
  - Added Departments navigation to MainLayout with SITEMAP icon
- [x] Budgets (spec 12)
  - Created Budget entity with name, type (A/B), currency, active
  - Created BudgetLine entity for budget amounts per account/period/department
  - Created V10__budgets_kpis.sql migration with budget and budget_line tables
  - Created BudgetRepository and BudgetLineRepository with fiscal year queries
  - Created BudgetService with full CRUD for budgets and budget lines
  - Created BudgetsView with master-detail split layout, fiscal year selector, budget line management
  - Added Budgets navigation to MainLayout with MONEY icon
- [x] KPIs (spec 12)
  - Created KPI entity with code, name, unit, description, active
  - Created KPIValue entity for KPI values per period
  - Added kpi and kpi_value tables to V10__budgets_kpis.sql migration
  - Created KPIRepository and KPIValueRepository with fiscal year queries
  - Created KPIService with full CRUD for KPIs and KPI values, createDefaultKPIs
  - Created KPIsView with master-detail split layout, fiscal year selector, value management
  - Added KPIs navigation to MainLayout with TRENDING_UP icon
- [x] Department Filtering in P&L Report (spec 12 acceptance criteria)
  - Added department-filtered queries to LedgerEntryRepository (findByAccountAndDateRangeAndDepartment, sum methods)
  - Added overloaded generateProfitAndLoss method with optional Department parameter
  - Added department ComboBox filter to P&L tab in ReportsView
  - P&L report now shows department filter status in subtitle when filtered
- [x] Budget vs Actual Report (spec 12 acceptance criteria)
  - Added BudgetVsActual and BudgetVsActualLine record types to ReportingService
  - Added generateBudgetVsActual method that compares budget lines to actual ledger entries
  - Supports optional department filtering for both budget and actual amounts
  - Added new "Budget vs Actual" tab to ReportsView with budget selector, department filter
  - Shows variance amounts and percentages with totals row
  - Added findByFiscalYearCompanyAndDateRangeOverlap to PeriodRepository for date range queries

### Phase 8: Recurring Transactions (COMPLETE) - Tag: 0.1.5
- [x] Fixed H2 reserved keyword issue
  - Renamed 'value' column in kpi_value table to 'metric_value' (V11 migration)
  - Updated KPIValue entity with @Column(name = "metric_value") annotation
- [x] Recurring Templates (spec 11)
  - Created RecurringTemplate entity with:
    - Template types: PAYMENT, RECEIPT, JOURNAL, INVOICE, BILL
    - Frequency options: DAILY, WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY, YEARLY
    - Status: ACTIVE, PAUSED, COMPLETED, CANCELLED
    - Execution modes: AUTO_POST, CREATE_DRAFT
    - Schedule configuration: startDate, endDate, maxOccurrences, frequencyInterval
    - Payload stored as JSON for flexible template data
  - Created RecurrenceExecutionLog entity to track execution history
  - Created V12__recurring_templates.sql migration with proper indexes
  - Created RecurringTemplateRepository and RecurrenceExecutionLogRepository
  - Created RecurringTemplateService with:
    - Full CRUD for templates
    - Create from existing Transaction, SalesInvoice, or SupplierBill
    - Pause/Resume/Cancel functionality
    - Scheduled execution (cron: daily at 2:00 AM)
    - Manual "Run Now" capability
    - Execution logging with success/failure tracking
  - Created RecurringTemplatesView with:
    - Master-detail split layout
    - Status filtering
    - Execution history grid
    - Run Due Now button for batch execution
    - Pause/Resume/Cancel/Delete actions
    - Create template dialog with JSON payload editor
  - Added Recurring navigation to MainLayout with TIME_FORWARD icon

### Phase 9: Remaining Features (PENDING)
- [ ] PDF/Excel export for all reports (spec 13)
- [ ] Global search with query expressions (spec 15)
- [ ] Saved views and grid customization (spec 15)
- [ ] Email sending integration (spec 13)

## Lessons Learned
- VaadinWebSecurity deprecated in Vaadin 24.8+ - use VaadinSecurityConfigurer.vaadin() instead
- Test profile should use hibernate.ddl-auto=create-drop with Flyway disabled to avoid schema conflicts
- AuditService should create its own ObjectMapper rather than injecting as bean for test isolation
- TreeGrid requires TreeDataProvider with proper parent-child hierarchy setup
- @VaadinSessionScope for session-scoped beans (like CompanyContextService)
- When adding new dependencies to PostingService (like TaxCalculationService), update unit tests to include mocks
- Vaadin 25+ deprecates MemoryBuffer and StreamResource - functionality still works but will need update in future versions
- File attachments stored outside DB with checksums for integrity; deduplication by checksum within company scope
- AuditService.logEvent requires User parameter (can be null for system actions)
- AccountService.findByType takes companyId (Long), not Company object
- REGEXP is not supported in HQL/JPQL - use application-side filtering for regex-like matching
- TransactionService.createTransaction requires description parameter
- OpenPDF (com.github.librepdf:openpdf) used for PDF generation - provides Document, PdfWriter, PdfPTable classes
- When adding new services that need circular dependencies (like RemittanceAdviceService in PaymentRunService), ensure constructor injection order
- FiscalYear uses getLabel() not getName() - Period uses getStartDate()/getEndDate() without getName()
- Use DateTimeFormatter for displaying period names (e.g., "MMM yyyy")
- AccountService.findByCompany takes Company object, not companyId
- When adding new constructor parameters to services (like ReportingService), update all unit tests to include mocks for the new dependencies
- H2 reserves 'value' as a keyword - renamed kpi_value.value to metric_value via migration V11
- AuditService.logEvent signature: (Company, User, String eventType, String entityType, Long entityId, String summary) - use strings not enums
- @Scheduled annotation requires Spring's scheduling to be enabled (@EnableScheduling on Application class)
- JSON payload serialization for recurring templates uses Jackson ObjectMapper with ObjectNode/ArrayNode builders

## Technical Notes
- Build: `./mvnw compile`
- Test: `./mvnw test`
- Run: `./mvnw spring-boot:run` (starts on http://localhost:8080)
- Package: `./mvnw package -Pproduction`
- Run with production profile: `java -jar target/moniworks-1.0-SNAPSHOT.jar --spring.profiles.active=prod`
- mvnw needs `chmod +x` after clone
- H2 console available in dev mode at http://localhost:8080/h2-console

## Architecture Decisions
- Money stored as BigDecimal with 2 decimal places (minor units)
- Dates: LocalDate for accounting, Instant for audit timestamps
- Ledger entries are immutable - corrections via reversals only
- Multi-tenant: All entities include companyId, enforced in queries
- Session-scoped CompanyContextService manages current company per user session
- Tax calculation assumes tax-inclusive amounts (NZ standard) - extracts GST from totals
- TaxLine records created during posting for audit trail and return generation
- Attachments stored on filesystem with path: {storagePath}/{companyId}/{year}/{month}/{uuid}_{sanitizedFilename}
- Attachment deduplication by SHA-256 checksum within company scope (avoids storing same file twice)
- Audit events are append-only and immutable - no delete capability exposed in UI
- Contacts support CUSTOMER, SUPPLIER, or BOTH types for flexible use in A/R and A/P
- Products have separate sales and purchase accounts for proper P&L allocation
