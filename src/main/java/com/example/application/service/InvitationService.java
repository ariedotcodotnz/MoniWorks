package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.UserInvitation.InvitationStatus;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.UserInvitationRepository;
import com.example.application.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user invitations.
 * Handles the complete invitation workflow: create, send, accept, cancel, expire.
 */
@Service
@Transactional
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${moniworks.invitation.expiry-days:7}")
    private int expiryDays;

    @Value("${moniworks.invitation.base-url:http://localhost:8080}")
    private String baseUrl;

    private final UserInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    public InvitationService(UserInvitationRepository invitationRepository,
                             UserRepository userRepository,
                             CompanyMembershipRepository membershipRepository,
                             PasswordEncoder passwordEncoder,
                             EmailService emailService,
                             AuditService auditService) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    /**
     * Result of invitation operations.
     */
    public record InvitationResult(
        boolean success,
        String message,
        UserInvitation invitation,
        User user
    ) {
        public static InvitationResult success(String message, UserInvitation invitation) {
            return new InvitationResult(true, message, invitation, null);
        }

        public static InvitationResult success(String message, UserInvitation invitation, User user) {
            return new InvitationResult(true, message, invitation, user);
        }

        public static InvitationResult failure(String message) {
            return new InvitationResult(false, message, null, null);
        }
    }

    /**
     * Creates and sends an invitation for a user to join a company.
     *
     * @param email The email address to invite
     * @param displayName Optional display name for new users
     * @param company The company to join
     * @param role The role to assign
     * @param invitedBy The user sending the invitation
     * @return InvitationResult with the created invitation
     */
    public InvitationResult createInvitation(String email, String displayName, Company company, Role role, User invitedBy) {
        // Normalize email
        String normalizedEmail = email.toLowerCase().trim();

        // Check if user is already a member of this company
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            if (membershipRepository.existsByUserAndCompany(existingUser.get(), company)) {
                return InvitationResult.failure("User is already a member of this company");
            }
        }

        // Check if there's already a pending invitation
        if (invitationRepository.existsPendingInvitation(normalizedEmail, company, Instant.now())) {
            return InvitationResult.failure("A pending invitation already exists for this email");
        }

        // Generate secure token
        String token = generateSecureToken();

        // Calculate expiry
        Instant expiresAt = Instant.now().plus(Duration.ofDays(expiryDays));

        // Create invitation
        UserInvitation invitation = new UserInvitation(normalizedEmail, token, company, role, expiresAt);
        invitation.setDisplayName(displayName);
        invitation.setInvitedBy(invitedBy);
        invitation = invitationRepository.save(invitation);

        // Send invitation email
        sendInvitationEmail(invitation);

        // Log audit event
        auditService.logEvent(
            company,
            invitedBy,
            "INVITATION_CREATED",
            "USER_INVITATION",
            invitation.getId(),
            String.format("Invitation sent to %s for role %s", normalizedEmail, role.getName())
        );

        log.info("Invitation created for {} to join company {} with role {}",
            normalizedEmail, company.getName(), role.getName());

        return InvitationResult.success("Invitation sent successfully", invitation);
    }

    /**
     * Resends an existing pending invitation.
     */
    public InvitationResult resendInvitation(UserInvitation invitation, User resentBy) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return InvitationResult.failure("Can only resend pending invitations");
        }

        if (invitation.isExpired()) {
            return InvitationResult.failure("Invitation has expired. Please create a new invitation.");
        }

        // Send the email again
        sendInvitationEmail(invitation);

        auditService.logEvent(
            invitation.getCompany(),
            resentBy,
            "INVITATION_RESENT",
            "USER_INVITATION",
            invitation.getId(),
            String.format("Invitation resent to %s", invitation.getEmail())
        );

        return InvitationResult.success("Invitation resent successfully", invitation);
    }

    /**
     * Validates an invitation token and returns the invitation if valid.
     */
    @Transactional(readOnly = true)
    public Optional<UserInvitation> validateToken(String token) {
        Optional<UserInvitation> invitation = invitationRepository.findByToken(token);

        if (invitation.isEmpty()) {
            return Optional.empty();
        }

        UserInvitation inv = invitation.get();
        if (inv.getStatus() != InvitationStatus.PENDING) {
            return Optional.empty();
        }

        if (inv.isExpired()) {
            return Optional.empty();
        }

        return invitation;
    }

    /**
     * Accepts an invitation for a new user (creates account and membership).
     *
     * @param token The invitation token
     * @param displayName The user's display name
     * @param password The user's password
     * @return InvitationResult with the created user and membership
     */
    public InvitationResult acceptInvitationNewUser(String token, String displayName, String password) {
        Optional<UserInvitation> invitationOpt = invitationRepository.findByToken(token);

        if (invitationOpt.isEmpty()) {
            return InvitationResult.failure("Invalid invitation token");
        }

        UserInvitation invitation = invitationOpt.get();

        if (!invitation.canBeAccepted()) {
            if (invitation.isExpired()) {
                return InvitationResult.failure("This invitation has expired");
            }
            return InvitationResult.failure("This invitation is no longer valid");
        }

        // Check if email is already registered
        if (userRepository.findByEmail(invitation.getEmail()).isPresent()) {
            return InvitationResult.failure("An account with this email already exists. Please use 'Accept for Existing User'.");
        }

        // Create the user
        User user = new User(invitation.getEmail(), displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        // Create the membership
        CompanyMembership membership = new CompanyMembership(user, invitation.getCompany(), invitation.getRole());
        membership.setStatus(CompanyMembership.MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        // Mark invitation as accepted
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedUser(user);
        invitationRepository.save(invitation);

        // Log audit event
        auditService.logEvent(
            invitation.getCompany(),
            user,
            "INVITATION_ACCEPTED",
            "USER_INVITATION",
            invitation.getId(),
            String.format("New user %s accepted invitation and joined company", invitation.getEmail())
        );

        log.info("Invitation accepted: new user {} joined company {} with role {}",
            invitation.getEmail(), invitation.getCompany().getName(), invitation.getRole().getName());

        return InvitationResult.success("Account created and invitation accepted", invitation, user);
    }

    /**
     * Accepts an invitation for an existing user (creates membership only).
     *
     * @param token The invitation token
     * @param user The existing user accepting the invitation
     * @return InvitationResult with the updated invitation
     */
    public InvitationResult acceptInvitationExistingUser(String token, User user) {
        Optional<UserInvitation> invitationOpt = invitationRepository.findByToken(token);

        if (invitationOpt.isEmpty()) {
            return InvitationResult.failure("Invalid invitation token");
        }

        UserInvitation invitation = invitationOpt.get();

        if (!invitation.canBeAccepted()) {
            if (invitation.isExpired()) {
                return InvitationResult.failure("This invitation has expired");
            }
            return InvitationResult.failure("This invitation is no longer valid");
        }

        // Verify the email matches
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            return InvitationResult.failure("This invitation was sent to a different email address");
        }

        // Check if already a member
        if (membershipRepository.existsByUserAndCompany(user, invitation.getCompany())) {
            return InvitationResult.failure("You are already a member of this company");
        }

        // Create the membership
        CompanyMembership membership = new CompanyMembership(user, invitation.getCompany(), invitation.getRole());
        membership.setStatus(CompanyMembership.MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        // Mark invitation as accepted
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedUser(user);
        invitationRepository.save(invitation);

        // Log audit event
        auditService.logEvent(
            invitation.getCompany(),
            user,
            "INVITATION_ACCEPTED",
            "USER_INVITATION",
            invitation.getId(),
            String.format("Existing user %s accepted invitation and joined company", user.getEmail())
        );

        log.info("Invitation accepted: existing user {} joined company {} with role {}",
            user.getEmail(), invitation.getCompany().getName(), invitation.getRole().getName());

        return InvitationResult.success("Invitation accepted", invitation, user);
    }

    /**
     * Cancels a pending invitation.
     */
    public InvitationResult cancelInvitation(UserInvitation invitation, User cancelledBy) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return InvitationResult.failure("Can only cancel pending invitations");
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        auditService.logEvent(
            invitation.getCompany(),
            cancelledBy,
            "INVITATION_CANCELLED",
            "USER_INVITATION",
            invitation.getId(),
            String.format("Invitation to %s was cancelled", invitation.getEmail())
        );

        return InvitationResult.success("Invitation cancelled", invitation);
    }

    /**
     * Gets all invitations for a company.
     */
    @Transactional(readOnly = true)
    public List<UserInvitation> getInvitationsByCompany(Company company) {
        return invitationRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    /**
     * Gets pending invitations for a company.
     */
    @Transactional(readOnly = true)
    public List<UserInvitation> getPendingInvitationsByCompany(Company company) {
        return invitationRepository.findByCompanyAndStatusOrderByCreatedAtDesc(company, InvitationStatus.PENDING);
    }

    /**
     * Generates the invitation acceptance URL.
     */
    public String getInvitationUrl(UserInvitation invitation) {
        return baseUrl + "/accept-invitation?token=" + invitation.getToken();
    }

    /**
     * Scheduled task to expire old invitations.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void expireOldInvitations() {
        List<UserInvitation> expired = invitationRepository.findExpiredPendingInvitations(Instant.now());

        for (UserInvitation invitation : expired) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);

            log.debug("Expired invitation {} for {}", invitation.getId(), invitation.getEmail());
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} pending invitations", expired.size());
        }
    }

    // Private helper methods

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendInvitationEmail(UserInvitation invitation) {
        String invitationUrl = getInvitationUrl(invitation);
        Company company = invitation.getCompany();
        String inviterName = invitation.getInvitedBy() != null
            ? invitation.getInvitedBy().getDisplayName()
            : company.getName();

        String subject = String.format("You've been invited to join %s on MoniWorks", company.getName());

        String bodyText = String.format("""
            Hello%s,

            %s has invited you to join %s on MoniWorks.

            You have been assigned the role: %s

            To accept this invitation, please click the link below:
            %s

            This invitation will expire in %d days.

            If you did not expect this invitation, you can safely ignore this email.

            Best regards,
            The MoniWorks Team
            """,
            invitation.getDisplayName() != null ? " " + invitation.getDisplayName() : "",
            inviterName,
            company.getName(),
            invitation.getRole().getName(),
            invitationUrl,
            expiryDays
        );

        EmailService.EmailRequest request = EmailService.EmailRequest.builder()
            .to(invitation.getEmail(), invitation.getDisplayName())
            .subject(subject)
            .bodyText(bodyText)
            .company(company)
            .sender(invitation.getInvitedBy())
            .build();

        EmailService.EmailResult result = emailService.sendEmail(request);

        if (!result.success()) {
            log.warn("Failed to send invitation email to {}: {}", invitation.getEmail(), result.message());
        }
    }
}
