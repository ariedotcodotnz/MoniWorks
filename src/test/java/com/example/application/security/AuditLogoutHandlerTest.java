package com.example.application.security;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.User;
import com.example.application.repository.UserRepository;
import com.example.application.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogoutHandler.
 * Verifies that logout events are properly logged to the audit trail.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogoutHandlerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuditLogoutHandler handler;

    private User testUser;

    @BeforeEach
    void setUp() {
        handler = new AuditLogoutHandler(auditService, userRepository);

        testUser = new User("test@example.com", "Test User");
        testUser.setId(1L);
    }

    @Test
    void logout_withUserDetails_logsLogout() {
        // Given
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            "test@example.com",
            "password",
            Collections.emptyList()
        );
        Authentication authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(auditService.logLogout(testUser)).thenReturn(new AuditEvent());

        // When
        handler.logout(request, response, authentication);

        // Then
        verify(userRepository).findByEmail("test@example.com");
        verify(auditService).logLogout(testUser);
    }

    @Test
    void logout_withStringPrincipal_logsLogout() {
        // Given
        Authentication authentication =
            new UsernamePasswordAuthenticationToken("test@example.com", null, Collections.emptyList());

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(auditService.logLogout(testUser)).thenReturn(new AuditEvent());

        // When
        handler.logout(request, response, authentication);

        // Then
        verify(userRepository).findByEmail("test@example.com");
        verify(auditService).logLogout(testUser);
    }

    @Test
    void logout_nullAuthentication_doesNotLog() {
        // When
        handler.logout(request, response, null);

        // Then
        verify(userRepository, never()).findByEmail(any());
        verify(auditService, never()).logLogout(any());
    }

    @Test
    void logout_userNotFound_doesNotLog() {
        // Given
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            "unknown@example.com",
            "password",
            Collections.emptyList()
        );
        Authentication authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // When
        handler.logout(request, response, authentication);

        // Then
        verify(userRepository).findByEmail("unknown@example.com");
        verify(auditService, never()).logLogout(any());
    }
}
