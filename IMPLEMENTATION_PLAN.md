# MoniWorks Implementation Plan

## Current Status
- Project initialized with Spring Boot 4.0.1, Vaadin 25.0.3, Java 21
- **Phase 1 Foundation COMPLETE** - Tag: 0.0.1
- All 13 tests passing (PostingServiceTest: 7, ReportingServiceTest: 5, ApplicationTest: 1)
- Core domain entities created: Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, Department, Role, Permission, CompanyMembership, AuditEvent
- Database configured: H2 for development, PostgreSQL for production
- Flyway migration V1__initial_schema.sql created
- All repository interfaces created
- Basic service layer implemented: CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService
- Vaadin UI shell with MainLayout, LoginView, DashboardView, TransactionsView, AccountsView
- Security configuration with SecurityConfig and UserDetailsServiceImpl (using VaadinSecurityConfigurer API)

## Release 1 (SLC) - Target Features
Per specs, Release 1 must deliver:
1. Single company support (multi-tenancy foundation)
2. Chart of Accounts with hierarchical structure
3. Fiscal Years and Periods with lock/unlock
4. Cashbook transactions (Payments, Receipts, Journals)
5. Posting to immutable ledger
6. GST/Tax coding and returns
7. Bank import (QIF/OFX) and reconciliation
8. Financial reports (Trial Balance, P&L, Balance Sheet)
9. Attachments for source documents
10. Audit trail

## Implementation Order

### Phase 1: Foundation (COMPLETE)
- [x] Project setup with Maven, Spring Boot, Vaadin
- [x] Add database dependencies (PostgreSQL, H2, JPA, Flyway)
- [x] Configure database connections (H2 for dev, PostgreSQL for prod)
- [x] Create core domain entities (Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, Department, Role, Permission, CompanyMembership, AuditEvent)
- [x] Create Flyway migrations (V1__initial_schema.sql)
- [x] Create repository layer (all repositories created)
- [x] Create basic service layer (CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService)
- [x] Create Vaadin UI shell with AppLayout (MainLayout, LoginView, DashboardView, TransactionsView, AccountsView)
- [x] Create security configuration (SecurityConfig, UserDetailsServiceImpl)

### Phase 2: Core Accounting (Current)
- [x] Company entity and management (created in Phase 1)
- [x] User/authentication (simplified for v1) - SecurityConfig, UserDetailsServiceImpl
- [ ] Chart of Accounts (Account entity, CRUD, tree view) - Entity exists, needs UI
- [ ] Fiscal Years and Periods - Entities exist, needs management UI
- [x] Transaction/TransactionLine entities (created in Phase 1)
- [x] LedgerEntry (immutable postings) - Entity created
- [ ] Posting service with validation - PostingService exists, needs completion

### Phase 3: Tax & Bank
- [ ] Tax codes and rates
- [ ] Tax calculation on transactions
- [ ] Bank account linking
- [ ] Bank import (OFX/QIF parsing)
- [ ] Reconciliation matching

### Phase 4: Reports & Polish
- [ ] Trial Balance report
- [ ] P&L report
- [ ] Balance Sheet report
- [ ] GST return generation
- [ ] Attachments support
- [ ] Audit event logging

## Known Issues
- Phase 2 entities created early in Phase 1 - need to build out full CRUD UI
- Posting service needs business logic completion
- Need to add more integration tests

## Lessons Learned
- VaadinWebSecurity deprecated in Vaadin 24.8+ - use VaadinSecurityConfigurer.vaadin() instead
- Test profile should use hibernate.ddl-auto=create-drop with Flyway disabled to avoid schema conflicts
- AuditService should create its own ObjectMapper rather than injecting as bean for test isolation

## Technical Notes
- Build: `./mvnw clean compile`
- Test: `./mvnw test`
- Run: `./mvnw spring-boot:run` (starts on http://localhost:8080)
- Package: `./mvnw clean package -Pproduction`
- Run with production profile: `java -jar target/moniworks-1.0-SNAPSHOT.jar --spring.profiles.active=prod`
- mvnw needs `chmod +x` after clone
- H2 console available in dev mode at http://localhost:8080/h2-console

## Architecture Decisions
- Money stored as BigDecimal with 2 decimal places (minor units)
- Dates: LocalDate for accounting, Instant for audit timestamps
- Ledger entries are immutable - corrections via reversals only
- Multi-tenant: All entities include companyId, enforced in queries
