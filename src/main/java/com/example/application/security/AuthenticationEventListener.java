package com.example.application.security;

import com.example.application.domain.User;
import com.example.application.repository.UserRepository;
import com.example.application.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for Spring Security authentication events and logs them to the audit trail.
 * This provides visibility into login activity for security monitoring and compliance.
 */
@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventListener.class);

    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuthenticationEventListener(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    /**
     * Handles successful authentication events.
     * Logs the login to the audit trail with the user's email.
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        String email = extractEmail(principal);

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                auditService.logLogin(user);
                log.info("User logged in: {}", email);
            });
        }
    }

    /**
     * Handles authentication failure events.
     * Logs failed login attempts for security monitoring.
     */
    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        String attemptedEmail = principal != null ? principal.toString() : "unknown";
        String failureReason = event.getException().getMessage();

        // Log failed login attempt - no user entity since authentication failed
        auditService.logEvent(
            null, // no company context for failed login
            null, // no user since authentication failed
            "LOGIN_FAILED",
            "User",
            null,
            "Failed login attempt for: " + attemptedEmail,
            Map.of(
                "attemptedEmail", attemptedEmail,
                "reason", failureReason != null ? failureReason : "Unknown"
            )
        );

        log.warn("Failed login attempt for: {} - Reason: {}", attemptedEmail, failureReason);
    }

    private String extractEmail(Object principal) {
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            return (String) principal;
        }
        return null;
    }
}
