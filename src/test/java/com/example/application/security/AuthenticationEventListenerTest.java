package com.example.application.security;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.User;
import com.example.application.repository.UserRepository;
import com.example.application.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationEventListener.
 * Verifies that login success and failure events are properly logged to the audit trail.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationEventListenerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    private AuthenticationEventListener listener;

    private User testUser;

    @BeforeEach
    void setUp() {
        listener = new AuthenticationEventListener(auditService, userRepository);

        testUser = new User("test@example.com", "Test User");
        testUser.setId(1L);
    }

    @Test
    void onAuthenticationSuccess_withUserDetails_logsLogin() {
        // Given
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            "test@example.com",
            "password",
            Collections.emptyList()
        );
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(auditService.logLogin(testUser)).thenReturn(new AuditEvent());

        // When
        listener.onAuthenticationSuccess(event);

        // Then
        verify(userRepository).findByEmail("test@example.com");
        verify(auditService).logLogin(testUser);
    }

    @Test
    void onAuthenticationSuccess_withStringPrincipal_logsLogin() {
        // Given
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("test@example.com", null, Collections.emptyList());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(auditService.logLogin(testUser)).thenReturn(new AuditEvent());

        // When
        listener.onAuthenticationSuccess(event);

        // Then
        verify(userRepository).findByEmail("test@example.com");
        verify(auditService).logLogin(testUser);
    }

    @Test
    void onAuthenticationSuccess_userNotFound_doesNotLog() {
        // Given
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            "unknown@example.com",
            "password",
            Collections.emptyList()
        );
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // When
        listener.onAuthenticationSuccess(event);

        // Then
        verify(userRepository).findByEmail("unknown@example.com");
        verify(auditService, never()).logLogin(any());
    }

    @Test
    void onAuthenticationFailure_logsFailedAttempt() {
        // Given
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("attacker@example.com", "wrongpassword");
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");
        AuthenticationFailureBadCredentialsEvent event =
            new AuthenticationFailureBadCredentialsEvent(authentication, exception);

        when(auditService.logEvent(any(), any(), eq("LOGIN_FAILED"), eq("User"), any(), anyString(), anyMap()))
            .thenReturn(new AuditEvent());

        // When
        listener.onAuthenticationFailure(event);

        // Then
        verify(auditService).logEvent(
            isNull(),
            isNull(),
            eq("LOGIN_FAILED"),
            eq("User"),
            isNull(),
            contains("attacker@example.com"),
            argThat(map -> map.containsKey("attemptedEmail") && map.containsKey("reason"))
        );
    }

    @Test
    void onAuthenticationFailure_withNullPrincipal_logsUnknown() {
        // Given
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(null, "wrongpassword");
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");
        AuthenticationFailureBadCredentialsEvent event =
            new AuthenticationFailureBadCredentialsEvent(authentication, exception);

        when(auditService.logEvent(any(), any(), eq("LOGIN_FAILED"), eq("User"), any(), anyString(), anyMap()))
            .thenReturn(new AuditEvent());

        // When
        listener.onAuthenticationFailure(event);

        // Then
        verify(auditService).logEvent(
            isNull(),
            isNull(),
            eq("LOGIN_FAILED"),
            eq("User"),
            isNull(),
            contains("unknown"),
            anyMap()
        );
    }
}
