package com.example.application.security;

import com.example.application.domain.User;
import com.example.application.repository.UserRepository;
import com.example.application.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Handles logout events by logging them to the audit trail before the session is destroyed.
 * This provides visibility into logout activity for security monitoring and compliance.
 */
@Component
public class AuditLogoutHandler implements LogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogoutHandler.class);

    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuditLogoutHandler(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            log.debug("No authentication present during logout");
            return;
        }

        Object principal = authentication.getPrincipal();
        String email = extractEmail(principal);

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                auditService.logLogout(user);
                log.info("User logged out: {}", email);
            });
        }
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
