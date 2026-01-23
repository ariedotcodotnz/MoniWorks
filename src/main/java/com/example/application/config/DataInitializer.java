package com.example.application.config;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.Permission;
import com.example.application.domain.Role;
import com.example.application.domain.User;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.CompanyRepository;
import com.example.application.repository.PermissionRepository;
import com.example.application.repository.RoleRepository;
import com.example.application.service.UserService;

/**
 * Initializes default data on application startup. Creates permissions, roles, and a default admin
 * user for development/testing purposes.
 */
@Component
@Order(1)
public class DataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  private static final String DEFAULT_EMAIL = "admin@test.com";
  private static final String DEFAULT_PASSWORD = "admin";
  private static final String DEFAULT_DISPLAY_NAME = "Test Admin";

  // All permissions with their descriptions and categories
  private static final List<PermissionDef> ALL_PERMISSIONS =
      List.of(
          // System
          new PermissionDef("ADMIN", "Full system administration", "SYSTEM"),
          // Admin
          new PermissionDef("MANAGE_USERS", "Manage users and roles", "ADMIN"),
          new PermissionDef("MANAGE_COMPANY", "Manage company settings", "ADMIN"),
          new PermissionDef("MANAGE_DEPARTMENTS", "Create and manage departments", "ADMIN"),
          new PermissionDef("VIEW_AUDIT_LOG", "View audit trail", "ADMIN"),
          new PermissionDef("MANAGE_PERIODS", "Manage fiscal years and periods", "ADMIN"),
          // Accounting
          new PermissionDef("MANAGE_COA", "Manage chart of accounts", "ACCOUNTING"),
          new PermissionDef("VIEW_COA", "View chart of accounts", "ACCOUNTING"),
          // Transactions
          new PermissionDef("CREATE_TRANSACTION", "Create transactions", "TRANSACTIONS"),
          new PermissionDef("POST_TRANSACTION", "Post transactions", "TRANSACTIONS"),
          new PermissionDef("VIEW_TRANSACTION", "View transactions", "TRANSACTIONS"),
          new PermissionDef(
              "MANAGE_ALLOCATIONS", "Manage payment and receipt allocations", "TRANSACTIONS"),
          // Banking
          new PermissionDef("RECONCILE_BANK", "Perform bank reconciliation", "BANKING"),
          // Reporting
          new PermissionDef("VIEW_REPORTS", "View financial reports", "REPORTING"),
          new PermissionDef("EXPORT_REPORTS", "Export reports to PDF/Excel", "REPORTING"),
          // Tax
          new PermissionDef("MANAGE_TAX", "Manage tax codes and returns", "TAX"),
          // Contacts
          new PermissionDef("MANAGE_CONTACTS", "Manage customers and suppliers", "CONTACTS"),
          // Products
          new PermissionDef("MANAGE_PRODUCTS", "Manage products and services", "PRODUCTS"),
          // AR
          new PermissionDef("MANAGE_INVOICES", "Create and manage sales invoices", "AR"),
          new PermissionDef("VIEW_INVOICES", "View sales invoices", "AR"),
          new PermissionDef("MANAGE_STATEMENTS", "Generate customer statements", "AR"),
          // AP
          new PermissionDef("MANAGE_BILLS", "Create and manage supplier bills", "AP"),
          new PermissionDef("VIEW_BILLS", "View supplier bills", "AP"),
          // Budgeting
          new PermissionDef("MANAGE_BUDGETS", "Create and manage budgets", "BUDGETING"),
          new PermissionDef("MANAGE_KPIS", "Create and manage KPIs", "BUDGETING"),
          // Automation
          new PermissionDef("MANAGE_RECURRING", "Create and manage recurring templates", "AUTOMATION"));

  // Role definitions with their permissions
  private static final Map<String, Set<String>> ROLE_PERMISSIONS =
      Map.of(
          "BOOKKEEPER",
          Set.of(
              "VIEW_COA",
              "CREATE_TRANSACTION",
              "POST_TRANSACTION",
              "VIEW_TRANSACTION",
              "RECONCILE_BANK",
              "VIEW_REPORTS",
              "MANAGE_CONTACTS",
              "MANAGE_PRODUCTS",
              "MANAGE_INVOICES",
              "VIEW_INVOICES",
              "MANAGE_BILLS",
              "VIEW_BILLS",
              "MANAGE_BUDGETS",
              "MANAGE_KPIS",
              "MANAGE_RECURRING",
              "EXPORT_REPORTS",
              "MANAGE_ALLOCATIONS",
              "MANAGE_STATEMENTS"),
          "READONLY",
          Set.of("VIEW_COA", "VIEW_TRANSACTION", "VIEW_REPORTS", "VIEW_INVOICES", "VIEW_BILLS"));

  private final UserService userService;
  private final CompanyRepository companyRepository;
  private final RoleRepository roleRepository;
  private final CompanyMembershipRepository membershipRepository;
  private final PermissionRepository permissionRepository;

  public DataInitializer(
      UserService userService,
      CompanyRepository companyRepository,
      RoleRepository roleRepository,
      CompanyMembershipRepository membershipRepository,
      PermissionRepository permissionRepository) {
    this.userService = userService;
    this.companyRepository = companyRepository;
    this.roleRepository = roleRepository;
    this.membershipRepository = membershipRepository;
    this.permissionRepository = permissionRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Step 1: Create permissions if they don't exist
    createPermissionsIfNeeded();

    // Step 2: Create system roles if they don't exist
    Role adminRole = getOrCreateRole("ADMIN", "Full administrative access", true);
    getOrCreateRole("BOOKKEEPER", "Standard bookkeeper access", false);
    getOrCreateRole("READONLY", "View-only access", false);

    // Step 3: Create default admin user if it doesn't exist
    if (userService.findByEmail(DEFAULT_EMAIL).isPresent()) {
      log.info("Default test user already exists: {}", DEFAULT_EMAIL);
      return;
    }

    log.info("Creating default test user: {}", DEFAULT_EMAIL);

    // Create default user
    User adminUser = userService.createUser(DEFAULT_EMAIL, DEFAULT_DISPLAY_NAME, DEFAULT_PASSWORD);

    // Create default test company
    Company testCompany =
        new Company("Test Company", "US", "USD", LocalDate.of(LocalDate.now().getYear(), 1, 1));
    testCompany = companyRepository.save(testCompany);

    // Add user to company with ADMIN role
    CompanyMembership membership = new CompanyMembership(adminUser, testCompany, adminRole);
    membershipRepository.save(membership);

    log.info("Default test user created successfully");
    log.info("  Email: {}", DEFAULT_EMAIL);
    log.info("  Password: {}", DEFAULT_PASSWORD);
  }

  private void createPermissionsIfNeeded() {
    long existingCount = permissionRepository.count();
    if (existingCount >= ALL_PERMISSIONS.size()) {
      log.debug("All {} permissions already exist", existingCount);
      return;
    }

    log.info("Creating missing permissions ({} exist, {} expected)", existingCount, ALL_PERMISSIONS.size());

    for (PermissionDef def : ALL_PERMISSIONS) {
      if (permissionRepository.findByName(def.name()).isEmpty()) {
        Permission permission = new Permission(def.name(), def.description(), def.category());
        permissionRepository.save(permission);
        log.debug("Created permission: {}", def.name());
      }
    }
  }

  private Role getOrCreateRole(String name, String description, boolean isAdminRole) {
    Optional<Role> existingRole = roleRepository.findByNameAndSystemTrue(name);
    if (existingRole.isPresent()) {
      Role role = existingRole.get();
      // Ensure the role has proper permissions
      if (role.getPermissions().isEmpty()) {
        log.info("Role {} exists but has no permissions - assigning permissions", name);
        assignPermissionsToRole(role, isAdminRole);
        return roleRepository.save(role);
      }
      return role;
    }

    log.info("Creating system role: {}", name);
    Role role = new Role(name, description);
    role.setSystem(true);
    assignPermissionsToRole(role, isAdminRole);
    return roleRepository.save(role);
  }

  private void assignPermissionsToRole(Role role, boolean isAdminRole) {
    if (isAdminRole) {
      // ADMIN gets all permissions
      List<Permission> allPermissions = permissionRepository.findAll();
      log.info("Assigning all {} permissions to ADMIN role", allPermissions.size());
      allPermissions.forEach(role::addPermission);
    } else {
      // Other roles get specific permissions from the map
      Set<String> permNames = ROLE_PERMISSIONS.getOrDefault(role.getName(), Set.of());
      for (String permName : permNames) {
        permissionRepository.findByName(permName).ifPresent(role::addPermission);
      }
      log.info("Assigned {} permissions to {} role", permNames.size(), role.getName());
    }
  }

  private record PermissionDef(String name, String description, String category) {}
}
