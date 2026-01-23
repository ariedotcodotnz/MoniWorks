package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.domain.UserSecurityLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for UserSecurityLevel entities.
 */
@Repository
public interface UserSecurityLevelRepository extends JpaRepository<UserSecurityLevel, Long> {

    /**
     * Finds the security level for a user in a specific company.
     */
    Optional<UserSecurityLevel> findByUserAndCompany(User user, Company company);

    /**
     * Finds the security level for a user and company by their IDs.
     */
    @Query("SELECT usl FROM UserSecurityLevel usl WHERE usl.user.id = :userId AND usl.company.id = :companyId")
    Optional<UserSecurityLevel> findByUserIdAndCompanyId(@Param("userId") Long userId, @Param("companyId") Long companyId);

    /**
     * Gets the max security level for a user in a company, returning 0 if not found.
     */
    @Query("SELECT COALESCE(usl.maxLevel, 0) FROM UserSecurityLevel usl WHERE usl.user.id = :userId AND usl.company.id = :companyId")
    Optional<Integer> getMaxLevelByUserIdAndCompanyId(@Param("userId") Long userId, @Param("companyId") Long companyId);

    /**
     * Checks if a record exists for the given user and company.
     */
    boolean existsByUserAndCompany(User user, Company company);

    /**
     * Deletes all security levels for a user.
     */
    void deleteByUser(User user);

    /**
     * Deletes all security levels for a company.
     */
    void deleteByCompany(Company company);

    /**
     * Finds all security levels for a company.
     */
    @Query("SELECT usl FROM UserSecurityLevel usl WHERE usl.company.id = :companyId")
    java.util.List<UserSecurityLevel> findByCompanyId(@Param("companyId") Long companyId);
}
