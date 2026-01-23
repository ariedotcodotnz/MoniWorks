package com.example.application.config;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
 * Initializes default test data on application startup. Creates a default admin user and test
 * company for development/testing purposes.
 */
@Component
public class DataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  private static final String DEFAULT_EMAIL = "admin@test.com";
  private static final String DEFAULT_PASSWORD = "admin";
  private static final String DEFAULT_DISPLAY_NAME = "Test Admin";

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
    if (userService.findByEmail(DEFAULT_EMAIL).isPresent()) {
      log.info("Default test user already exists: {}", DEFAULT_EMAIL);
      return;
    }

    // Get or create ADMIN role
    Role adminRole = getOrCreateAdminRole();

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

  private Role getOrCreateAdminRole() {
    Optional<Role> existingRole = roleRepository.findByNameAndSystemTrue("ADMIN");
    if (existingRole.isPresent()) {
      Role role = existingRole.get();
      // Ensure the ADMIN role has all permissions (fix for cases where role exists but permissions weren't assigned)
      if (role.getPermissions().isEmpty()) {
        log.info("ADMIN role exists but has no permissions - assigning all permissions");
        assignAllPermissionsToRole(role);
        return roleRepository.save(role);
      }
      return role;
    }

    log.info("Creating ADMIN role (Flyway migrations may not have run for this database)");
    Role adminRole = new Role("ADMIN", "Full administrative access");
    adminRole.setSystem(true);
    assignAllPermissionsToRole(adminRole);
    return roleRepository.save(adminRole);
  }

  private void assignAllPermissionsToRole(Role role) {
    List<Permission> allPermissions = permissionRepository.findAll();
    if (allPermissions.isEmpty()) {
      log.warn("No permissions found in database - ADMIN role will have limited access until permissions are created");
    } else {
      log.info("Assigning {} permissions to ADMIN role", allPermissions.size());
      allPermissions.forEach(role::addPermission);
    }
  }
}
