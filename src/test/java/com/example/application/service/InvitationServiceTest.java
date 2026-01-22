package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.UserInvitation.InvitationStatus;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.UserInvitationRepository;
import com.example.application.repository.UserRepository;
import com.example.application.service.InvitationService.InvitationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvitationService.
 * Tests invitation creation, acceptance, cancellation, and expiration workflows.
 */
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private UserInvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CompanyMembershipRepository membershipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditService auditService;

    private InvitationService invitationService;

    private Company company;
    private Role role;
    private User inviter;
    private User existingUser;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
            invitationRepository,
            userRepository,
            membershipRepository,
            passwordEncoder,
            emailService,
            auditService
        );

        ReflectionTestUtils.setField(invitationService, "expiryDays", 7);
        ReflectionTestUtils.setField(invitationService, "baseUrl", "http://localhost:8080");

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        role = new Role("BOOKKEEPER", "Bookkeeper role");
        role.setId(1L);

        inviter = new User("admin@test.com", "Admin User");
        inviter.setId(1L);

        existingUser = new User("existing@test.com", "Existing User");
        existingUser.setId(2L);
    }

    @Test
    void createInvitation_NewEmail_Success() {
        // Given
        String email = "newuser@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(invitationRepository.existsPendingInvitation(eq(email), eq(company), any(Instant.class)))
            .thenReturn(false);
        when(invitationRepository.save(any(UserInvitation.class)))
            .thenAnswer(invocation -> {
                UserInvitation inv = invocation.getArgument(0);
                inv.setId(1L);
                return inv;
            });
        when(emailService.sendEmail(any())).thenReturn(EmailService.EmailResult.queued("msg-123"));

        // When
        InvitationResult result = invitationService.createInvitation(email, "New User", company, role, inviter);

        // Then
        assertTrue(result.success());
        assertEquals("Invitation sent successfully", result.message());
        assertNotNull(result.invitation());
        assertEquals(email, result.invitation().getEmail());
        assertEquals("New User", result.invitation().getDisplayName());
        assertEquals(InvitationStatus.PENDING, result.invitation().getStatus());

        verify(invitationRepository).save(any(UserInvitation.class));
        verify(emailService).sendEmail(any());
        verify(auditService).logEvent(eq(company), eq(inviter), eq("INVITATION_CREATED"),
            eq("USER_INVITATION"), anyLong(), anyString());
    }

    @Test
    void createInvitation_UserAlreadyMember_Fails() {
        // Given
        String email = "existing@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(membershipRepository.existsByUserAndCompany(existingUser, company)).thenReturn(true);

        // When
        InvitationResult result = invitationService.createInvitation(email, null, company, role, inviter);

        // Then
        assertFalse(result.success());
        assertEquals("User is already a member of this company", result.message());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void createInvitation_PendingInvitationExists_Fails() {
        // Given
        String email = "newuser@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(invitationRepository.existsPendingInvitation(eq(email), eq(company), any(Instant.class)))
            .thenReturn(true);

        // When
        InvitationResult result = invitationService.createInvitation(email, null, company, role, inviter);

        // Then
        assertFalse(result.success());
        assertEquals("A pending invitation already exists for this email", result.message());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void validateToken_ValidPendingToken_ReturnsInvitation() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));

        // When
        Optional<UserInvitation> result = invitationService.validateToken("valid-token");

        // Then
        assertTrue(result.isPresent());
        assertEquals(invitation, result.get());
    }

    @Test
    void validateToken_ExpiredToken_ReturnsEmpty() {
        // Given
        UserInvitation invitation = createExpiredInvitation();
        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(invitation));

        // When
        Optional<UserInvitation> result = invitationService.validateToken("expired-token");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void validateToken_AcceptedToken_ReturnsEmpty() {
        // Given
        UserInvitation invitation = createAcceptedInvitation();
        when(invitationRepository.findByToken("accepted-token")).thenReturn(Optional.of(invitation));

        // When
        Optional<UserInvitation> result = invitationService.validateToken("accepted-token");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void validateToken_NonexistentToken_ReturnsEmpty() {
        // Given
        when(invitationRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        // When
        Optional<UserInvitation> result = invitationService.validateToken("nonexistent");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void acceptInvitationNewUser_ValidInvitation_CreatesUserAndMembership() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail(invitation.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(99L);
            return u;
        });
        when(membershipRepository.save(any(CompanyMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        InvitationResult result = invitationService.acceptInvitationNewUser("valid-token", "New User", "password123");

        // Then
        assertTrue(result.success());
        assertEquals("Account created and invitation accepted", result.message());
        assertNotNull(result.user());
        assertEquals(invitation.getEmail(), result.user().getEmail());
        assertEquals("New User", result.user().getDisplayName());

        // Verify user created with encoded password
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("encoded-password", userCaptor.getValue().getPasswordHash());
        assertEquals(User.Status.ACTIVE, userCaptor.getValue().getStatus());

        // Verify membership created
        ArgumentCaptor<CompanyMembership> membershipCaptor = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertEquals(company, membershipCaptor.getValue().getCompany());
        assertEquals(role, membershipCaptor.getValue().getRole());

        // Verify invitation marked as accepted
        verify(invitationRepository).save(argThat(inv ->
            inv.getStatus() == InvitationStatus.ACCEPTED &&
            inv.getAcceptedAt() != null &&
            inv.getAcceptedUser() != null
        ));
    }

    @Test
    void acceptInvitationNewUser_EmailAlreadyRegistered_Fails() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail(invitation.getEmail())).thenReturn(Optional.of(existingUser));

        // When
        InvitationResult result = invitationService.acceptInvitationNewUser("valid-token", "Name", "password");

        // Then
        assertFalse(result.success());
        assertTrue(result.message().contains("account with this email already exists"));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void acceptInvitationNewUser_ExpiredInvitation_Fails() {
        // Given
        UserInvitation invitation = createExpiredInvitation();
        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(invitation));

        // When
        InvitationResult result = invitationService.acceptInvitationNewUser("expired-token", "Name", "password");

        // Then
        assertFalse(result.success());
        assertEquals("This invitation has expired", result.message());
    }

    @Test
    void acceptInvitationExistingUser_ValidInvitation_CreatesMembership() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        invitation.setEmail(existingUser.getEmail()); // Make email match
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(membershipRepository.existsByUserAndCompany(existingUser, company)).thenReturn(false);
        when(membershipRepository.save(any(CompanyMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        InvitationResult result = invitationService.acceptInvitationExistingUser("valid-token", existingUser);

        // Then
        assertTrue(result.success());
        assertEquals("Invitation accepted", result.message());
        assertEquals(existingUser, result.user());

        verify(membershipRepository).save(any(CompanyMembership.class));
        verify(userRepository, never()).save(any()); // User not created, just membership
    }

    @Test
    void acceptInvitationExistingUser_EmailMismatch_Fails() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        invitation.setEmail("different@test.com"); // Different from existingUser.getEmail()
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));

        // When
        InvitationResult result = invitationService.acceptInvitationExistingUser("valid-token", existingUser);

        // Then
        assertFalse(result.success());
        assertEquals("This invitation was sent to a different email address", result.message());
    }

    @Test
    void acceptInvitationExistingUser_AlreadyMember_Fails() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        invitation.setEmail(existingUser.getEmail());
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(membershipRepository.existsByUserAndCompany(existingUser, company)).thenReturn(true);

        // When
        InvitationResult result = invitationService.acceptInvitationExistingUser("valid-token", existingUser);

        // Then
        assertFalse(result.success());
        assertEquals("You are already a member of this company", result.message());
    }

    @Test
    void cancelInvitation_PendingInvitation_Success() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        InvitationResult result = invitationService.cancelInvitation(invitation, inviter);

        // Then
        assertTrue(result.success());
        assertEquals("Invitation cancelled", result.message());
        verify(invitationRepository).save(argThat(inv -> inv.getStatus() == InvitationStatus.CANCELLED));
        verify(auditService).logEvent(eq(company), eq(inviter), eq("INVITATION_CANCELLED"),
            eq("USER_INVITATION"), anyLong(), anyString());
    }

    @Test
    void cancelInvitation_AlreadyAccepted_Fails() {
        // Given
        UserInvitation invitation = createAcceptedInvitation();

        // When
        InvitationResult result = invitationService.cancelInvitation(invitation, inviter);

        // Then
        assertFalse(result.success());
        assertEquals("Can only cancel pending invitations", result.message());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void resendInvitation_PendingInvitation_SendsEmail() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        when(emailService.sendEmail(any())).thenReturn(EmailService.EmailResult.queued("msg-456"));

        // When
        InvitationResult result = invitationService.resendInvitation(invitation, inviter);

        // Then
        assertTrue(result.success());
        assertEquals("Invitation resent successfully", result.message());
        verify(emailService).sendEmail(any());
        verify(auditService).logEvent(eq(company), eq(inviter), eq("INVITATION_RESENT"),
            eq("USER_INVITATION"), anyLong(), anyString());
    }

    @Test
    void resendInvitation_ExpiredInvitation_Fails() {
        // Given
        UserInvitation invitation = createExpiredInvitation();

        // When
        InvitationResult result = invitationService.resendInvitation(invitation, inviter);

        // Then
        assertFalse(result.success());
        assertTrue(result.message().contains("expired"));
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void getInvitationUrl_GeneratesCorrectUrl() {
        // Given
        UserInvitation invitation = createPendingInvitation();
        invitation.setToken("test-token-123");

        // When
        String url = invitationService.getInvitationUrl(invitation);

        // Then
        assertEquals("http://localhost:8080/accept-invitation?token=test-token-123", url);
    }

    // Helper methods

    private UserInvitation createPendingInvitation() {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(1L);
        invitation.setEmail("newuser@test.com");
        invitation.setToken("valid-token");
        invitation.setCompany(company);
        invitation.setRole(role);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setInvitedBy(inviter);
        return invitation;
    }

    private UserInvitation createExpiredInvitation() {
        UserInvitation invitation = createPendingInvitation();
        invitation.setToken("expired-token");
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return invitation;
    }

    private UserInvitation createAcceptedInvitation() {
        UserInvitation invitation = createPendingInvitation();
        invitation.setToken("accepted-token");
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedUser(existingUser);
        return invitation;
    }
}
