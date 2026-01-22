package com.example.application.security;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.User;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * Custom permission evaluator that supports company-scoped permission checks.
 *
 * Supports two evaluation modes:
 * 1. Simple permission check: hasPermission(authentication, null, 'PERMISSION_NAME')
 *    - Checks if user has the permission in ANY of their companies
 * 2. Company-scoped check: hasPermission(authentication, companyId, 'PERMISSION_NAME')
 *    - Checks if user has the permission in the SPECIFIC company
 */
@Component
public class CompanyPermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CompanyPermissionEvaluator.class);

    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;

    public CompanyPermissionEvaluator(UserRepository userRepository,
                                       CompanyMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        String permissionName = permission.toString();

        // If targetDomainObject is null, check if user has permission in ANY company
        // This is the simple case used for basic authorization
        if (targetDomainObject == null) {
            return hasGlobalPermission(authentication, permissionName);
        }

        // If targetDomainObject is a Company, check permission for that company
        if (targetDomainObject instanceof Company company) {
            return hasCompanyPermission(authentication, company.getId(), permissionName);
        }

        // If targetDomainObject is a Long (company ID), check permission for that company
        if (targetDomainObject instanceof Long companyId) {
            return hasCompanyPermission(authentication, companyId, permissionName);
        }

        log.warn("Unknown target domain object type: {}", targetDomainObject.getClass());
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                  String targetType, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        String permissionName = permission.toString();

        // If targetType is "Company" and targetId is the company ID
        if ("Company".equals(targetType) && targetId instanceof Long companyId) {
            return hasCompanyPermission(authentication, companyId, permissionName);
        }

        // Fall back to global permission check
        return hasGlobalPermission(authentication, permissionName);
    }

    /**
     * Checks if the user has the given permission in ANY of their companies.
     * This is used for simple @PreAuthorize checks without company context.
     */
    private boolean hasGlobalPermission(Authentication authentication, String permissionName) {
        // Check if authority exists directly (loaded at login time from all memberships)
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority().equals(permissionName) ||
                authority.getAuthority().equals("ADMIN")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the user has the given permission in a SPECIFIC company.
     * This is used for company-scoped @PreAuthorize checks.
     */
    private boolean hasCompanyPermission(Authentication authentication, Long companyId,
                                          String permissionName) {
        String email = authentication.getName();

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("User not found: {}", email);
            return false;
        }

        User user = userOpt.get();

        // Find membership for this specific company
        Optional<CompanyMembership> membershipOpt = membershipRepository
            .findByUserIdAndCompanyId(user.getId(), companyId);

        if (membershipOpt.isEmpty()) {
            log.debug("User {} is not a member of company {}", email, companyId);
            return false;
        }

        CompanyMembership membership = membershipOpt.get();

        // Check membership is active
        if (membership.getStatus() != CompanyMembership.MembershipStatus.ACTIVE) {
            log.debug("Membership for user {} in company {} is not active", email, companyId);
            return false;
        }

        // Check if role has ADMIN permission (full access)
        if (membership.getRole().hasPermission(Permissions.ADMIN)) {
            return true;
        }

        // Check specific permission
        return membership.getRole().hasPermission(permissionName);
    }
}
