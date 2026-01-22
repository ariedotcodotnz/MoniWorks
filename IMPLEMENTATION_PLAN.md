# MoniWorks Implementation Plan

## Current Status
- Project initialized with Spring Boot 4.0.1, Vaadin 25.0.3, Java 21
- **Phase 1 Foundation COMPLETE** - Tag: 0.0.1
- **Phase 2 Core Accounting COMPLETE** - Tag: 0.0.2
- All 13 tests passing (PostingServiceTest: 7, ReportingServiceTest: 5, ApplicationTest: 1)
- Core domain entities created: Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, Department, Role, Permission, CompanyMembership, AuditEvent
- Database configured: H2 for development, PostgreSQL for production
- Flyway migration V1__initial_schema.sql created
- All repository interfaces created
- Full service layer: CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService, CompanyContextService, TaxCodeService, FiscalYearService
- Full UI views: MainLayout, LoginView, DashboardView, TransactionsView, AccountsView, PeriodsView
- Security configuration with SecurityConfig and UserDetailsServiceImpl (using VaadinSecurityConfigurer API)

## Release 1 (SLC) - Target Features
Per specs, Release 1 must deliver:
1. Single company support (multi-tenancy foundation) - DONE
2. Chart of Accounts with hierarchical structure - DONE
3. Fiscal Years and Periods with lock/unlock - DONE
4. Cashbook transactions (Payments, Receipts, Journals) - DONE
5. Posting to immutable ledger - DONE
6. GST/Tax coding and returns - Tax codes on transactions done, returns pending
7. Bank import (QIF/OFX) and reconciliation - PENDING
8. Financial reports (Trial Balance, P&L, Balance Sheet) - PENDING
9. Attachments for source documents - PENDING
10. Audit trail - Core logging done, UI pending

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

### Phase 3: Tax & Bank (Current)
- [ ] Tax codes management UI
- [ ] Tax calculation on transactions
- [ ] Bank account linking (mark accounts as bank accounts)
- [ ] Bank import (OFX/QIF parsing)
- [ ] Reconciliation matching UI

### Phase 4: Reports & Polish
- [ ] Trial Balance report view
- [ ] P&L report view
- [ ] Balance Sheet report view
- [ ] GST return generation
- [ ] Attachments support
- [ ] Audit event logging UI

## Lessons Learned
- VaadinWebSecurity deprecated in Vaadin 24.8+ - use VaadinSecurityConfigurer.vaadin() instead
- Test profile should use hibernate.ddl-auto=create-drop with Flyway disabled to avoid schema conflicts
- AuditService should create its own ObjectMapper rather than injecting as bean for test isolation
- TreeGrid requires TreeDataProvider with proper parent-child hierarchy setup
- @VaadinSessionScope for session-scoped beans (like CompanyContextService)

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
