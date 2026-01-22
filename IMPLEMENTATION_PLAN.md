# MoniWorks Implementation Plan

## Current Status
- Project initialized with Spring Boot 4.0.1, Vaadin 25.0.3, Java 21
- **Phase 1 Foundation COMPLETE** - Tag: 0.0.1
- **Phase 2 Core Accounting COMPLETE** - Tag: 0.0.2
- **Phase 3 Tax & Bank COMPLETE** - Tag: 0.0.3
- **Phase 4 Reports & Polish COMPLETE** - Tag: 0.0.8
- **Phase 5 Contacts & Products COMPLETE** - Tag: 0.0.9
- **Phase 6 A/R and A/P COMPLETE** - Tag: 0.1.1
- All 37 tests passing (PostingServiceTest: 7, ReportingServiceTest: 5, TaxCalculationServiceTest: 14, AttachmentServiceTest: 10, ApplicationTest: 1)
- Core domain entities created: Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, TaxLine, TaxReturn, TaxReturnLine, Department, Role, Permission, CompanyMembership, AuditEvent, BankStatementImport, BankFeedItem, AllocationRule, Attachment, AttachmentLink, Contact, ContactPerson, ContactNote, Product, SalesInvoice, SalesInvoiceLine, ReceivableAllocation, SupplierBill, SupplierBillLine, PayableAllocation, PaymentRun
- Database configured: H2 for development, PostgreSQL for production
- Flyway migrations: V1__initial_schema.sql, V2__bank_accounts.sql, V3__tax_lines.sql, V4__tax_returns.sql, V5__attachments.sql, V6__contacts.sql, V7__products.sql, V8__sales_invoices.sql, V9__supplier_bills.sql
- All repository interfaces created (31 repositories)
- Full service layer: CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService, CompanyContextService, TaxCodeService, FiscalYearService, BankImportService, TaxCalculationService, TaxReturnService, AttachmentService, ContactService, ProductService, SalesInvoiceService, ReceivableAllocationService, SupplierBillService, PayableAllocationService, PaymentRunService
- Full UI views: MainLayout, LoginView, DashboardView, TransactionsView, AccountsView, PeriodsView, TaxCodesView, ReportsView, BankReconciliationView, GstReturnsView, AuditEventsView, ContactsView, ProductsView, SalesInvoicesView, SupplierBillsView
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

### Phase 6: Release 2 - A/R and A/P (COMPLETE) - Tag: 0.1.1
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
- [ ] Dashboard Overdue AR/AP tiles
- [ ] Remittance advice PDF generation (PaymentRun output)

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
