package com.example.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.LedgerEntryRepository;

@Service
@Transactional
public class AccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AuditService auditService;

  public AccountService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AuditService auditService) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.auditService = auditService;
  }

  public Account createAccount(
      Company company, String code, String name, Account.AccountType type) {
    return createAccount(company, code, name, type, null, null);
  }

  public Account createAccount(
      Company company, String code, String name, Account.AccountType type, Account parent) {
    return createAccount(company, code, name, type, parent, null);
  }

  /**
   * Creates a new account with optional parent and audit logging.
   *
   * @param company the company
   * @param code account code
   * @param name account name
   * @param type account type
   * @param parent optional parent account
   * @param actor the user creating the account (for audit logging)
   * @return the created account
   * @throws IllegalArgumentException if code already exists
   */
  public Account createAccount(
      Company company,
      String code,
      String name,
      Account.AccountType type,
      Account parent,
      User actor) {
    if (accountRepository.existsByCompanyAndCode(company, code)) {
      throw new IllegalArgumentException("Account code already exists: " + code);
    }
    Account account = new Account(company, code, name, type);
    if (parent != null) {
      account.setParent(parent);
    }
    account = accountRepository.save(account);

    auditService.logEvent(
        company,
        actor,
        "ACCOUNT_CREATED",
        "Account",
        account.getId(),
        "Created account: " + code + " - " + name);

    return account;
  }

  @Transactional(readOnly = true)
  public List<Account> findByCompany(Company company) {
    return accountRepository.findByCompanyOrderByCode(company);
  }

  /**
   * Finds all accounts for a company, filtered by user's security level. Accounts with
   * securityLevel > maxSecurityLevel are excluded.
   *
   * @param company the company
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findByCompanyWithSecurityLevel(Company company, int maxSecurityLevel) {
    return accountRepository.findByCompanyWithSecurityLevel(company, maxSecurityLevel);
  }

  @Transactional(readOnly = true)
  public List<Account> findActiveByCompany(Company company) {
    return accountRepository.findByCompanyAndActiveOrderByCode(company, true);
  }

  /**
   * Finds active accounts for a company, filtered by user's security level.
   *
   * @param company the company
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of active accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findActiveByCompanyWithSecurityLevel(Company company, int maxSecurityLevel) {
    return accountRepository.findByCompanyAndActiveWithSecurityLevel(
        company, true, maxSecurityLevel);
  }

  @Transactional(readOnly = true)
  public Optional<Account> findByCompanyAndCode(Company company, String code) {
    return accountRepository.findByCompanyAndCode(company, code);
  }

  /**
   * Finds an account by its alternate code.
   *
   * @param company the company
   * @param altCode the alternate code
   * @return the account if found
   */
  @Transactional(readOnly = true)
  public Optional<Account> findByCompanyAndAltCode(Company company, String altCode) {
    return accountRepository.findByCompanyAndAltCode(company, altCode);
  }

  /**
   * Finds an account by either its primary code or alternate code.
   *
   * @param company the company
   * @param code the code to search (checks both code and altCode)
   * @return the account if found
   */
  @Transactional(readOnly = true)
  public Optional<Account> findByCompanyAndCodeOrAltCode(Company company, String code) {
    // First try primary code
    Optional<Account> byCode = accountRepository.findByCompanyAndCode(company, code);
    if (byCode.isPresent()) {
      return byCode;
    }
    // Fall back to alternate code
    return accountRepository.findByCompanyAndAltCode(company, code);
  }

  @Transactional(readOnly = true)
  public Optional<Account> findById(Long id) {
    return accountRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<Account> findRootAccounts(Company company) {
    return accountRepository.findRootAccountsByCompany(company);
  }

  /**
   * Finds root accounts for a company, filtered by user's security level.
   *
   * @param company the company
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of root accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findRootAccountsWithSecurityLevel(Company company, int maxSecurityLevel) {
    return accountRepository.findRootAccountsByCompanyWithSecurityLevel(company, maxSecurityLevel);
  }

  @Transactional(readOnly = true)
  public List<Account> findChildren(Account parent) {
    return accountRepository.findByParent(parent);
  }

  /**
   * Finds child accounts of a parent, filtered by user's security level.
   *
   * @param parent the parent account
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of child accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findChildrenWithSecurityLevel(Account parent, int maxSecurityLevel) {
    return accountRepository.findByParentWithSecurityLevel(parent, maxSecurityLevel);
  }

  @Transactional(readOnly = true)
  public List<Account> findByType(Long companyId, Account.AccountType type) {
    return accountRepository.findByCompanyIdAndType(companyId, type);
  }

  /**
   * Finds accounts by type for a company, filtered by user's security level.
   *
   * @param companyId the company ID
   * @param type the account type
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findByTypeWithSecurityLevel(
      Long companyId, Account.AccountType type, int maxSecurityLevel) {
    return accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
        companyId, type, maxSecurityLevel);
  }

  @Transactional(readOnly = true)
  public BigDecimal getBalance(Account account, LocalDate asOfDate) {
    BigDecimal balance = ledgerEntryRepository.getBalanceByAccountAsOf(account, asOfDate);

    // For liability, equity, and income accounts, credit increases the balance
    // So we negate the debit-credit difference for proper display
    if (account.getType() == Account.AccountType.LIABILITY
        || account.getType() == Account.AccountType.EQUITY
        || account.getType() == Account.AccountType.INCOME) {
      balance = balance.negate();
    }

    return balance;
  }

  public Account save(Account account) {
    return save(account, null);
  }

  /**
   * Saves an account with audit logging for edits. Captures before/after state for key fields.
   *
   * @param account the account to save
   * @param actor the user making the change
   * @return the saved account
   */
  public Account save(Account account, User actor) {
    boolean isNew = account.getId() == null;

    if (!isNew) {
      // Capture before state for existing account
      Account before = accountRepository.findById(account.getId()).orElse(null);
      if (before != null) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.getCode().equals(account.getCode())) {
          changes.put("code", Map.of("from", before.getCode(), "to", account.getCode()));
        }
        if (!before.getName().equals(account.getName())) {
          changes.put("name", Map.of("from", before.getName(), "to", account.getName()));
        }
        if (before.getType() != account.getType()) {
          changes.put(
              "type", Map.of("from", before.getType().name(), "to", account.getType().name()));
        }
        if (before.isActive() != account.isActive()) {
          changes.put("active", Map.of("from", before.isActive(), "to", account.isActive()));
        }
        if (!java.util.Objects.equals(before.getTaxDefaultCode(), account.getTaxDefaultCode())) {
          changes.put(
              "taxDefaultCode",
              Map.of(
                  "from", before.getTaxDefaultCode() != null ? before.getTaxDefaultCode() : "",
                  "to", account.getTaxDefaultCode() != null ? account.getTaxDefaultCode() : ""));
        }
        if (!java.util.Objects.equals(before.getSecurityLevel(), account.getSecurityLevel())) {
          changes.put(
              "securityLevel",
              Map.of(
                  "from", before.getSecurityLevel() != null ? before.getSecurityLevel() : 0,
                  "to", account.getSecurityLevel() != null ? account.getSecurityLevel() : 0));
        }
        if (before.isBankAccount() != account.isBankAccount()) {
          changes.put(
              "bankAccount", Map.of("from", before.isBankAccount(), "to", account.isBankAccount()));
        }
        if (!java.util.Objects.equals(before.getAltCode(), account.getAltCode())) {
          changes.put(
              "altCode",
              Map.of(
                  "from", before.getAltCode() != null ? before.getAltCode() : "",
                  "to", account.getAltCode() != null ? account.getAltCode() : ""));
        }

        if (!changes.isEmpty()) {
          Account saved = accountRepository.save(account);
          auditService.logEvent(
              account.getCompany(),
              actor,
              "ACCOUNT_UPDATED",
              "Account",
              account.getId(),
              "Updated account: " + account.getCode(),
              changes);
          return saved;
        }
      }
    }

    return accountRepository.save(account);
  }

  public void deactivate(Account account) {
    deactivate(account, null);
  }

  /**
   * Deactivates an account with audit logging.
   *
   * @param account the account to deactivate
   * @param actor the user making the change
   */
  public void deactivate(Account account, User actor) {
    account.setActive(false);
    accountRepository.save(account);

    auditService.logEvent(
        account.getCompany(),
        actor,
        "ACCOUNT_DEACTIVATED",
        "Account",
        account.getId(),
        "Deactivated account: " + account.getCode());
  }

  /**
   * Finds all bank accounts for a company.
   *
   * @param company the company
   * @return list of accounts marked as bank accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findBankAccountsByCompany(Company company) {
    return accountRepository.findBankAccountsByCompany(company);
  }

  /**
   * Finds bank accounts for a company, filtered by user's security level.
   *
   * @param company the company
   * @param maxSecurityLevel the user's maximum security level
   * @return filtered list of bank accounts
   */
  @Transactional(readOnly = true)
  public List<Account> findBankAccountsByCompanyWithSecurityLevel(
      Company company, int maxSecurityLevel) {
    return accountRepository.findBankAccountsByCompanyWithSecurityLevel(company, maxSecurityLevel);
  }
}
