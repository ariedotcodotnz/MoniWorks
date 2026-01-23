package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Account;
import com.example.application.domain.Company;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

  List<Account> findByCompanyOrderByCode(Company company);

  List<Account> findByCompanyAndActiveOrderByCode(Company company, boolean active);

  Optional<Account> findByCompanyAndCode(Company company, String code);

  boolean existsByCompanyAndCode(Company company, String code);

  Optional<Account> findByCompanyAndAltCode(Company company, String altCode);

  boolean existsByCompanyAndAltCode(Company company, String altCode);

  @Query("SELECT a FROM Account a WHERE a.company = :company AND a.parent IS NULL ORDER BY a.code")
  List<Account> findRootAccountsByCompany(@Param("company") Company company);

  List<Account> findByParent(Account parent);

  @Query(
      "SELECT a FROM Account a WHERE a.company.id = :companyId AND a.type = :type ORDER BY a.code")
  List<Account> findByCompanyIdAndType(
      @Param("companyId") Long companyId, @Param("type") Account.AccountType type);

  @Query(
      "SELECT a FROM Account a WHERE a.company = :company AND a.bankAccount = true AND a.active = true ORDER BY a.code")
  List<Account> findBankAccountsByCompany(@Param("company") Company company);

  // Security-filtered queries - filter accounts by user's max security level
  // Accounts with securityLevel > maxSecurityLevel are hidden
  // securityLevel null is treated as 0 (unrestricted)

  @Query(
      "SELECT a FROM Account a WHERE a.company = :company AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findByCompanyWithSecurityLevel(
      @Param("company") Company company, @Param("maxSecurityLevel") Integer maxSecurityLevel);

  @Query(
      "SELECT a FROM Account a WHERE a.company = :company AND a.active = :active AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findByCompanyAndActiveWithSecurityLevel(
      @Param("company") Company company,
      @Param("active") boolean active,
      @Param("maxSecurityLevel") Integer maxSecurityLevel);

  @Query(
      "SELECT a FROM Account a WHERE a.company = :company AND a.parent IS NULL AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findRootAccountsByCompanyWithSecurityLevel(
      @Param("company") Company company, @Param("maxSecurityLevel") Integer maxSecurityLevel);

  @Query(
      "SELECT a FROM Account a WHERE a.parent = :parent AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findByParentWithSecurityLevel(
      @Param("parent") Account parent, @Param("maxSecurityLevel") Integer maxSecurityLevel);

  @Query(
      "SELECT a FROM Account a WHERE a.company.id = :companyId AND a.type = :type AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findByCompanyIdAndTypeWithSecurityLevel(
      @Param("companyId") Long companyId,
      @Param("type") Account.AccountType type,
      @Param("maxSecurityLevel") Integer maxSecurityLevel);

  @Query(
      "SELECT a FROM Account a WHERE a.company = :company AND a.bankAccount = true AND a.active = true AND (a.securityLevel IS NULL OR a.securityLevel <= :maxSecurityLevel) ORDER BY a.code")
  List<Account> findBankAccountsByCompanyWithSecurityLevel(
      @Param("company") Company company, @Param("maxSecurityLevel") Integer maxSecurityLevel);
}
