package com.example.application.service;

import com.example.application.domain.Permission;
import com.example.application.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing permissions.
 * Permissions are typically seeded via database migration and rarely changed at runtime.
 */
@Service
@Transactional
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<Permission> findAll() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Permission> findByName(String name) {
        return permissionRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public List<Permission> findByCategory(String category) {
        return permissionRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public Optional<Permission> findById(Long id) {
        return permissionRepository.findById(id);
    }

    /**
     * Creates a new permission if it doesn't already exist.
     * This is used during initialization to ensure all permissions exist.
     */
    public Permission createIfNotExists(String name, String description, String category) {
        return permissionRepository.findByName(name)
            .orElseGet(() -> {
                Permission permission = new Permission(name, description, category);
                return permissionRepository.save(permission);
            });
    }

    public Permission save(Permission permission) {
        return permissionRepository.save(permission);
    }
}
