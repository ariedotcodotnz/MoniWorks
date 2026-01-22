package com.example.application.ui.views;

import com.example.application.domain.User;
import com.example.application.domain.UserInvitation;
import com.example.application.service.InvitationService;
import com.example.application.service.InvitationService.InvitationResult;
import com.example.application.service.UserService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Public view for accepting user invitations.
 * Allows new users to create an account or existing logged-in users to accept directly.
 */
@Route("accept-invitation")
@PageTitle("Accept Invitation | MoniWorks")
@AnonymousAllowed
public class AcceptInvitationView extends VerticalLayout implements BeforeEnterObserver {

    private final InvitationService invitationService;
    private final UserService userService;

    private UserInvitation invitation;
    private String token;

    public AcceptInvitationView(InvitationService invitationService, UserService userService) {
        this.invitationService = invitationService;
        this.userService = userService;

        addClassName("accept-invitation-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setMaxWidth("500px");
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Get token from query parameters
        List<String> tokenParams = event.getLocation().getQueryParameters().getParameters().get("token");
        if (tokenParams == null || tokenParams.isEmpty()) {
            showError("No invitation token provided");
            return;
        }

        token = tokenParams.get(0);

        // Validate the token
        Optional<UserInvitation> invitationOpt = invitationService.validateToken(token);
        if (invitationOpt.isEmpty()) {
            showError("This invitation link is invalid or has expired");
            return;
        }

        invitation = invitationOpt.get();

        // Check if user is already logged in
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser");

        if (isLoggedIn) {
            // Show accept for existing user flow
            Optional<User> currentUser = userService.findByEmail(auth.getName());
            if (currentUser.isPresent()) {
                showExistingUserFlow(currentUser.get());
            } else {
                showNewUserFlow();
            }
        } else {
            // Check if there's an existing user with this email
            Optional<User> existingUser = userService.findByEmail(invitation.getEmail());
            if (existingUser.isPresent()) {
                showLoginPrompt();
            } else {
                showNewUserFlow();
            }
        }
    }

    private void showError(String message) {
        removeAll();

        H1 title = new H1("MoniWorks");
        title.getStyle().set("margin-bottom", "var(--lumo-space-l)");

        H2 errorTitle = new H2("Invalid Invitation");
        errorTitle.getStyle().set("color", "var(--lumo-error-color)");

        Paragraph errorMessage = new Paragraph(message);

        Button loginButton = new Button("Go to Login", e -> UI.getCurrent().navigate("login"));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(title, errorTitle, errorMessage, loginButton);
    }

    private void showLoginPrompt() {
        removeAll();

        H1 title = new H1("MoniWorks");
        title.getStyle().set("margin-bottom", "var(--lumo-space-l)");

        H2 subtitle = new H2("Accept Invitation");

        Paragraph info = new Paragraph(String.format(
            "You've been invited to join %s. An account already exists for %s. " +
            "Please log in to accept this invitation.",
            invitation.getCompany().getName(),
            invitation.getEmail()
        ));

        Button loginButton = new Button("Log In to Accept", e -> {
            // Navigate to login with return URL
            UI.getCurrent().navigate("login");
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(title, subtitle, info, loginButton);
    }

    private void showExistingUserFlow(User user) {
        removeAll();

        H1 title = new H1("MoniWorks");
        title.getStyle().set("margin-bottom", "var(--lumo-space-l)");

        H2 subtitle = new H2("Accept Invitation");

        // Check if email matches
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            Paragraph mismatch = new Paragraph(String.format(
                "This invitation was sent to %s, but you are logged in as %s. " +
                "Please log out and log in with the correct account to accept this invitation.",
                invitation.getEmail(),
                user.getEmail()
            ));
            mismatch.getStyle().set("color", "var(--lumo-error-color)");

            Button logoutButton = new Button("Log Out", e -> UI.getCurrent().getPage().setLocation("/logout"));
            logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

            add(title, subtitle, mismatch, logoutButton);
            return;
        }

        add(title, subtitle, createInvitationDetails());

        Paragraph info = new Paragraph("Click the button below to join this company.");

        Button acceptButton = new Button("Accept Invitation", e -> acceptAsExistingUser(user));
        acceptButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Decline", e -> UI.getCurrent().navigate(""));
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout buttons = new VerticalLayout(acceptButton, cancelButton);
        buttons.setAlignItems(Alignment.CENTER);
        buttons.setSpacing(true);
        buttons.setPadding(false);

        add(info, buttons);
    }

    private void showNewUserFlow() {
        removeAll();

        H1 title = new H1("MoniWorks");
        title.getStyle().set("margin-bottom", "var(--lumo-space-l)");

        H2 subtitle = new H2("Create Your Account");

        add(title, subtitle, createInvitationDetails());

        Paragraph info = new Paragraph("Complete the form below to create your account and join the company.");

        // Display name field (pre-filled if provided in invitation)
        TextField displayNameField = new TextField("Your Name");
        displayNameField.setRequired(true);
        displayNameField.setWidthFull();
        displayNameField.setPlaceholder("Enter your name");
        if (invitation.getDisplayName() != null && !invitation.getDisplayName().isBlank()) {
            displayNameField.setValue(invitation.getDisplayName());
        }

        // Email display (read-only)
        TextField emailField = new TextField("Email");
        emailField.setValue(invitation.getEmail());
        emailField.setReadOnly(true);
        emailField.setWidthFull();

        // Password fields
        PasswordField passwordField = new PasswordField("Password");
        passwordField.setRequired(true);
        passwordField.setWidthFull();
        passwordField.setMinLength(8);
        passwordField.setHelperText("At least 8 characters");

        PasswordField confirmPasswordField = new PasswordField("Confirm Password");
        confirmPasswordField.setRequired(true);
        confirmPasswordField.setWidthFull();

        // Accept button
        Button acceptButton = new Button("Create Account & Join", e -> {
            // Validate
            String displayName = displayNameField.getValue().trim();
            String password = passwordField.getValue();
            String confirmPassword = confirmPasswordField.getValue();

            if (displayName.isEmpty()) {
                displayNameField.setInvalid(true);
                displayNameField.setErrorMessage("Name is required");
                return;
            }

            if (password.length() < 8) {
                passwordField.setInvalid(true);
                passwordField.setErrorMessage("Password must be at least 8 characters");
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordField.setInvalid(true);
                confirmPasswordField.setErrorMessage("Passwords do not match");
                return;
            }

            acceptAsNewUser(displayName, password);
        });
        acceptButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        acceptButton.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> UI.getCurrent().navigate("login"));
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.setWidthFull();

        add(info, displayNameField, emailField, passwordField, confirmPasswordField, acceptButton, cancelButton);
    }

    private Component createInvitationDetails() {
        VerticalLayout details = new VerticalLayout();
        details.setPadding(true);
        details.setSpacing(false);
        details.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        Span companyLabel = new Span("Company: ");
        companyLabel.getStyle().set("font-weight", "bold");
        Span companyName = new Span(invitation.getCompany().getName());
        Paragraph companyLine = new Paragraph();
        companyLine.add(companyLabel, companyName);
        companyLine.getStyle().set("margin", "0");

        Span roleLabel = new Span("Role: ");
        roleLabel.getStyle().set("font-weight", "bold");
        Span roleName = new Span(invitation.getRole().getName());
        Paragraph roleLine = new Paragraph();
        roleLine.add(roleLabel, roleName);
        roleLine.getStyle().set("margin", "0");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault());
        Span expiresLabel = new Span("Expires: ");
        expiresLabel.getStyle().set("font-weight", "bold");
        Span expiresDate = new Span(formatter.format(invitation.getExpiresAt()));
        Paragraph expiresLine = new Paragraph();
        expiresLine.add(expiresLabel, expiresDate);
        expiresLine.getStyle().set("margin", "0");

        if (invitation.getInvitedBy() != null) {
            Span invitedByLabel = new Span("Invited by: ");
            invitedByLabel.getStyle().set("font-weight", "bold");
            Span invitedByName = new Span(invitation.getInvitedBy().getDisplayName());
            Paragraph invitedByLine = new Paragraph();
            invitedByLine.add(invitedByLabel, invitedByName);
            invitedByLine.getStyle().set("margin", "0");
            details.add(companyLine, roleLine, invitedByLine, expiresLine);
        } else {
            details.add(companyLine, roleLine, expiresLine);
        }

        return details;
    }

    private void acceptAsNewUser(String displayName, String password) {
        InvitationResult result = invitationService.acceptInvitationNewUser(token, displayName, password);

        if (result.success()) {
            Notification.show("Account created successfully! Please log in.", 5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().navigate("login");
        } else {
            Notification.show(result.message(), 5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void acceptAsExistingUser(User user) {
        InvitationResult result = invitationService.acceptInvitationExistingUser(token, user);

        if (result.success()) {
            Notification.show("You have joined " + invitation.getCompany().getName() + "!", 5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().navigate("");
        } else {
            Notification.show(result.message(), 5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
