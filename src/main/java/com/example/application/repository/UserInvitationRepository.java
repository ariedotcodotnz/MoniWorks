package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.UserInvitation;
import com.example.application.domain.UserInvitation.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    /**
     * Find an invitation by its unique token.
     */
    Optional<UserInvitation> findByToken(String token);

    /**
     * Find all pending invitations for a given email address.
     */
    @Query("SELECT i FROM UserInvitation i WHERE i.email = :email AND i.status = 'PENDING' AND i.expiresAt > :now")
    List<UserInvitation> findPendingByEmail(@Param("email") String email, @Param("now") Instant now);

    /**
     * Find all invitations for a company.
     */
    List<UserInvitation> findByCompanyOrderByCreatedAtDesc(Company company);

    /**
     * Find invitations for a company with a specific status.
     */
    List<UserInvitation> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, InvitationStatus status);

    /**
     * Check if there's already a pending invitation for this email and company.
     */
    @Query("SELECT COUNT(i) > 0 FROM UserInvitation i WHERE i.email = :email AND i.company = :company AND i.status = 'PENDING' AND i.expiresAt > :now")
    boolean existsPendingInvitation(@Param("email") String email, @Param("company") Company company, @Param("now") Instant now);

    /**
     * Find expired pending invitations that need to be marked as expired.
     */
    @Query("SELECT i FROM UserInvitation i WHERE i.status = 'PENDING' AND i.expiresAt < :now")
    List<UserInvitation> findExpiredPendingInvitations(@Param("now") Instant now);

    /**
     * Count pending invitations for a company.
     */
    @Query("SELECT COUNT(i) FROM UserInvitation i WHERE i.company = :company AND i.status = 'PENDING' AND i.expiresAt > :now")
    long countPendingByCompany(@Param("company") Company company, @Param("now") Instant now);
}
