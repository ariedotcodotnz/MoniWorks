package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.RoleRepository;
import com.example.application.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       CompanyMembershipRepository membershipRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String email, String displayName, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        User user = new User(email, displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(User.Status.ACTIVE);

        return userRepository.save(user);
    }

    public CompanyMembership addToCompany(User user, Company company, String roleName) {
        Role role = roleRepository.findByNameAndSystemTrue(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        return addToCompany(user, company, role);
    }

    public CompanyMembership addToCompany(User user, Company company, Role role) {
        if (membershipRepository.existsByUserAndCompany(user, company)) {
            throw new IllegalArgumentException("User already belongs to company");
        }

        CompanyMembership membership = new CompanyMembership(user, company, role);
        return membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Company> getCompaniesForUser(User user) {
        return membershipRepository.findCompaniesByActiveUser(user);
    }

    @Transactional(readOnly = true)
    public List<CompanyMembership> getMembershipsForUser(User user) {
        return membershipRepository.findActiveByUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyMembership> getMembership(User user, Company company) {
        return membershipRepository.findByUserAndCompany(user, company);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(User user, Company company, String permissionName) {
        Optional<CompanyMembership> membership = membershipRepository.findByUserAndCompany(user, company);

        if (membership.isEmpty() ||
            membership.get().getStatus() != CompanyMembership.MembershipStatus.ACTIVE) {
            return false;
        }

        return membership.get().getRole().hasPermission(permissionName);
    }

    public void changePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void deactivateUser(User user) {
        user.setStatus(User.Status.INACTIVE);
        userRepository.save(user);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> findByStatus(User.Status status) {
        return userRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<CompanyMembership> getMembershipsByCompany(Company company) {
        return membershipRepository.findActiveByCompany(company);
    }

    /**
     * Updates the role for an existing membership.
     */
    public CompanyMembership updateMembershipRole(CompanyMembership membership, Role newRole) {
        membership.setRole(newRole);
        return membershipRepository.save(membership);
    }

    /**
     * Removes a user from a company (deactivates membership).
     */
    public void removeFromCompany(CompanyMembership membership) {
        membership.setStatus(CompanyMembership.MembershipStatus.INACTIVE);
        membershipRepository.save(membership);
    }

    /**
     * Reactivates a membership.
     */
    public void reactivateMembership(CompanyMembership membership) {
        membership.setStatus(CompanyMembership.MembershipStatus.ACTIVE);
        membershipRepository.save(membership);
    }
}
