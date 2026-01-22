package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.Permission;
import com.example.application.domain.Role;
import com.example.application.repository.PermissionRepository;
import com.example.application.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing roles and their permissions.
 */
@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Role> findSystemRoles() {
        return roleRepository.findBySystemTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Role> findSystemRoleByName(String name) {
        return roleRepository.findByNameAndSystemTrue(name);
    }

    @Transactional(readOnly = true)
    public List<Role> findByCompany(Company company) {
        return roleRepository.findByCompany(company);
    }

    @Transactional(readOnly = true)
    public List<Role> findAvailableRolesForCompany(Company company) {
        return roleRepository.findAvailableRolesForCompany(company);
    }

    @Transactional(readOnly = true)
    public Optional<Role> findById(Long id) {
        return roleRepository.findById(id);
    }

    /**
     * Creates a company-specific role.
     */
    public Role createCompanyRole(Company company, String name, String description, Set<String> permissionNames) {
        Role role = new Role(name, description);
        role.setCompany(company);
        role.setSystem(false);

        // Add permissions
        for (String permName : permissionNames) {
            permissionRepository.findByName(permName)
                .ifPresent(role::addPermission);
        }

        Role saved = roleRepository.save(role);

        auditService.logEvent(company, null, "ROLE_CREATED", "Role", saved.getId(),
            "Created role: " + name);

        return saved;
    }

    /**
     * Updates the permissions for a role.
     */
    public Role updatePermissions(Role role, Set<String> permissionNames) {
        // Clear existing permissions
        role.getPermissions().clear();

        // Add new permissions
        for (String permName : permissionNames) {
            permissionRepository.findByName(permName)
                .ifPresent(role::addPermission);
        }

        Role saved = roleRepository.save(role);

        auditService.logEvent(role.getCompany(), null, "ROLE_UPDATED", "Role", role.getId(),
            "Updated permissions for role: " + role.getName());

        return saved;
    }

    /**
     * Adds a permission to a role.
     */
    public void addPermission(Role role, Permission permission) {
        role.addPermission(permission);
        roleRepository.save(role);

        auditService.logEvent(role.getCompany(), null, "ROLE_PERMISSION_ADDED", "Role", role.getId(),
            "Added permission " + permission.getName() + " to role " + role.getName());
    }

    /**
     * Removes a permission from a role.
     */
    public void removePermission(Role role, Permission permission) {
        role.removePermission(permission);
        roleRepository.save(role);

        auditService.logEvent(role.getCompany(), null, "ROLE_PERMISSION_REMOVED", "Role", role.getId(),
            "Removed permission " + permission.getName() + " from role " + role.getName());
    }

    /**
     * Deletes a company-specific role.
     * System roles cannot be deleted.
     */
    public void delete(Role role) {
        if (role.isSystem()) {
            throw new IllegalStateException("Cannot delete system roles");
        }

        auditService.logEvent(role.getCompany(), null, "ROLE_DELETED", "Role", role.getId(),
            "Deleted role: " + role.getName());

        roleRepository.delete(role);
    }

    public Role save(Role role) {
        return roleRepository.save(role);
    }
}
