package com.example.application.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.User;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.UserRepository;
import com.example.application.repository.UserSecurityLevelRepository;
import com.example.application.security.Permissions;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

/**
 * Session-scoped service that manages the current company context. For Release 1 (single company
 * support), this auto-selects or creates the first company. Future releases will support company
 * switching via UI.
 */
@Service
@VaadinSessionScope
public class CompanyContextService {

  private static final Logger log = LoggerFactory.getLogger(CompanyContextService.class);

  private final CompanyService companyService;
  private final UserRepository userRepository;
  private final CompanyMembershipRepository membershipRepository;
  private final UserSecurityLevelRepository securityLevelRepository;
  private Company currentCompany;
  private User currentUser;
  private CompanyMembership currentMembership;
  private Integer cachedSecurityLevel;

  public CompanyContextService(
      CompanyService companyService,
      UserRepository userRepository,
      CompanyMembershipRepository membershipRepository,
      UserSecurityLevelRepository securityLevelRepository) {
    this.companyService = companyService;
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
    this.securityLevelRepository = securityLevelRepository;
  }

  /**
   * Gets the current company for this session. Prefers a company where the user has an active
   * membership. If no such company exists, falls back to the first available company or creates a
   * default one.
   *
   * @return the current company
   */
  public Company getCurrentCompany() {
    if (currentCompany == null) {
      // First, try to get a company where the current user has an active membership
      User user = getCurrentUser();
      if (user != null) {
        List<Company> accessibleCompanies = membershipRepository.findCompaniesByActiveUser(user);
        if (!accessibleCompanies.isEmpty()) {
          currentCompany = accessibleCompanies.get(0);
          return currentCompany;
        }
      }
      // Fall back to first company or create default
      currentCompany =
          companyService.findAll().stream().findFirst().orElseGet(this::createDefaultCompany);
    }
    return currentCompany;
  }

  /**
   * Sets the current company for this session.
   *
   * @param company the company to set as current
   */
  public void setCurrentCompany(Company company) {
    this.currentCompany = company;
  }

  /**
   * Returns the ID of the current company.
   *
   * @return company ID
   */
  public Long getCurrentCompanyId() {
    return getCurrentCompany().getId();
  }

  /**
   * Creates a default company for first-time setup. Uses sensible defaults for NZ-based business
   * (per spec).
   */
  private Company createDefaultCompany() {
    // Default to NZ company with April 1 fiscal year start (common in NZ)
    LocalDate fiscalYearStart = LocalDate.of(LocalDate.now().getYear(), 4, 1);
    if (LocalDate.now().isBefore(fiscalYearStart)) {
      fiscalYearStart = fiscalYearStart.minusYears(1);
    }
    return companyService.createCompany("My Company", "NZ", "NZD", fiscalYearStart);
  }

  /**
   * Refreshes the current company from the database. Call this after the company has been modified.
   */
  public void refresh() {
    if (currentCompany != null) {
      currentCompany = companyService.findById(currentCompany.getId()).orElse(null);
    }
  }

  /**
   * Gets the current authenticated user.
   *
   * @return the current user, or null if not authenticated
   */
  public User getCurrentUser() {
    if (currentUser == null) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
        currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
      }
    }
    return currentUser;
  }

  /** Clears the current user cache. Call this when the user context may have changed. */
  public void clearUserCache() {
    currentUser = null;
    currentMembership = null;
    cachedSecurityLevel = null;
  }

  /**
   * Gets the current user's membership in the current company.
   *
   * @return the membership, or null if not a member
   */
  public CompanyMembership getCurrentMembership() {
    if (currentMembership == null) {
      User user = getCurrentUser();
      Company company = getCurrentCompany();
      if (user != null && company != null) {
        currentMembership = membershipRepository.findByUserAndCompany(user, company).orElse(null);
      }
    }
    return currentMembership;
  }

  /**
   * Gets all companies the current user has access to.
   *
   * @return list of accessible companies
   */
  public List<Company> getAccessibleCompanies() {
    User user = getCurrentUser();
    if (user == null) {
      return List.of();
    }
    return membershipRepository.findCompaniesByActiveUser(user);
  }

  /**
   * Checks if the current user has the specified permission in the current company.
   *
   * @param permissionName the permission to check
   * @return true if user has the permission
   */
  public boolean hasPermission(String permissionName) {
    CompanyMembership membership = getCurrentMembership();
    if (membership == null) {
      log.debug(
          "Permission check for '{}' failed: no membership found for user '{}' in company '{}'",
          permissionName,
          getCurrentUser() != null ? getCurrentUser().getEmail() : "null",
          getCurrentCompany() != null ? getCurrentCompany().getName() : "null");
      return false;
    }
    if (membership.getStatus() != CompanyMembership.MembershipStatus.ACTIVE) {
      log.debug(
          "Permission check for '{}' failed: membership status is {}",
          permissionName,
          membership.getStatus());
      return false;
    }
    // ADMIN has all permissions
    if (membership.getRole().hasPermission(Permissions.ADMIN)) {
      return true;
    }
    boolean hasPermission = membership.getRole().hasPermission(permissionName);
    if (!hasPermission) {
      log.debug(
          "Permission check for '{}' failed: role '{}' does not have this permission",
          permissionName,
          membership.getRole().getName());
    }
    return hasPermission;
  }

  /**
   * Checks if the current user has any of the specified permissions.
   *
   * @param permissionNames the permissions to check
   * @return true if user has at least one of the permissions
   */
  public boolean hasAnyPermission(String... permissionNames) {
    for (String perm : permissionNames) {
      if (hasPermission(perm)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the current user's role name in the current company.
   *
   * @return the role name, or null if not a member
   */
  public String getCurrentRoleName() {
    CompanyMembership membership = getCurrentMembership();
    return membership != null ? membership.getRole().getName() : null;
  }

  /**
   * Gets the current user's maximum security level for the current company. Accounts with
   * security_level > this value should be hidden from the user.
   *
   * @return the max security level (0 if not set, Integer.MAX_VALUE for admins)
   */
  public int getCurrentSecurityLevel() {
    // Admins can see all accounts regardless of security level
    if (hasPermission(Permissions.ADMIN)) {
      return Integer.MAX_VALUE;
    }

    if (cachedSecurityLevel != null) {
      return cachedSecurityLevel;
    }

    User user = getCurrentUser();
    Company company = getCurrentCompany();
    if (user == null || company == null) {
      cachedSecurityLevel = 0;
      return 0;
    }

    cachedSecurityLevel =
        securityLevelRepository
            .getMaxLevelByUserIdAndCompanyId(user.getId(), company.getId())
            .orElse(0);

    return cachedSecurityLevel;
  }

  /**
   * Checks if the current user can view an account based on its security level.
   *
   * @param accountSecurityLevel the account's security level (null treated as 0)
   * @return true if the user can view the account
   */
  public boolean canViewAccountWithSecurityLevel(Integer accountSecurityLevel) {
    int effectiveLevel = accountSecurityLevel != null ? accountSecurityLevel : 0;
    return effectiveLevel <= getCurrentSecurityLevel();
  }
}
