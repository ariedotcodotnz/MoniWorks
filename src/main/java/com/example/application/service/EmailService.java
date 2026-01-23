package com.example.application.service;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.User;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

/**
 * Service for sending emails with PDF attachments via SMTP.
 *
 * <p>When email is enabled and SMTP is configured, emails are sent via JavaMailSender. When
 * disabled or SMTP is not configured, the service logs email requests for audit/debugging but does
 * not send.
 *
 * <p>Configuration: - moniworks.email.enabled: Set to true to enable sending - spring.mail.*:
 * Standard Spring Boot mail properties for SMTP configuration - moniworks.email.from-address:
 * Sender email address - moniworks.email.from-name: Sender display name
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  @Value("${moniworks.email.enabled:false}")
  private boolean emailEnabled;

  @Value("${moniworks.email.from-address:noreply@moniworks.local}")
  private String fromAddress;

  @Value("${moniworks.email.from-name:MoniWorks}")
  private String fromName;

  private final AuditService auditService;
  private final JavaMailSender mailSender;

  @Autowired
  public EmailService(
      AuditService auditService, @Autowired(required = false) JavaMailSender mailSender) {
    this.auditService = auditService;
    this.mailSender = mailSender;
  }

  /** Result of an email send attempt. */
  public record EmailResult(boolean success, String status, String message, String messageId) {
    public static EmailResult queued(String messageId) {
      return new EmailResult(true, "QUEUED", "Email queued for delivery", messageId);
    }

    public static EmailResult sent(String messageId) {
      return new EmailResult(true, "SENT", "Email sent successfully", messageId);
    }

    public static EmailResult disabled() {
      return new EmailResult(false, "DISABLED", "Email sending is not enabled", null);
    }

    public static EmailResult failed(String reason) {
      return new EmailResult(false, "FAILED", reason, null);
    }

    public static EmailResult invalidRecipient(String reason) {
      return new EmailResult(false, "INVALID_RECIPIENT", reason, null);
    }
  }

  /** Email request builder for constructing email messages. */
  public record EmailRequest(
      String toAddress,
      String toName,
      String subject,
      String bodyText,
      String bodyHtml,
      List<EmailAttachment> attachments,
      Company company,
      User sender) {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private String toAddress;
      private String toName;
      private String subject;
      private String bodyText;
      private String bodyHtml;
      private List<EmailAttachment> attachments = List.of();
      private Company company;
      private User sender;

      public Builder to(String address, String name) {
        this.toAddress = address;
        this.toName = name;
        return this;
      }

      public Builder to(String address) {
        this.toAddress = address;
        return this;
      }

      public Builder toContact(Contact contact) {
        this.toAddress = contact.getEmail();
        this.toName = contact.getName();
        return this;
      }

      public Builder subject(String subject) {
        this.subject = subject;
        return this;
      }

      public Builder bodyText(String text) {
        this.bodyText = text;
        return this;
      }

      public Builder bodyHtml(String html) {
        this.bodyHtml = html;
        return this;
      }

      public Builder attachments(List<EmailAttachment> attachments) {
        this.attachments = attachments != null ? attachments : List.of();
        return this;
      }

      public Builder attachment(EmailAttachment attachment) {
        this.attachments = List.of(attachment);
        return this;
      }

      public Builder company(Company company) {
        this.company = company;
        return this;
      }

      public Builder sender(User sender) {
        this.sender = sender;
        return this;
      }

      public EmailRequest build() {
        return new EmailRequest(
            toAddress, toName, subject, bodyText, bodyHtml, attachments, company, sender);
      }
    }
  }

  /** Email attachment record. */
  public record EmailAttachment(String filename, String mimeType, byte[] content) {}

  /**
   * Send an email with the given request parameters.
   *
   * @param request The email request containing recipient, subject, body, and attachments
   * @return EmailResult indicating success or failure
   */
  public EmailResult sendEmail(EmailRequest request) {
    // Validate request
    if (request.toAddress() == null || request.toAddress().isBlank()) {
      return EmailResult.invalidRecipient("Recipient email address is required");
    }

    if (!isValidEmail(request.toAddress())) {
      return EmailResult.invalidRecipient("Invalid email address: " + request.toAddress());
    }

    if (request.subject() == null || request.subject().isBlank()) {
      return EmailResult.failed("Email subject is required");
    }

    if ((request.bodyText() == null || request.bodyText().isBlank())
        && (request.bodyHtml() == null || request.bodyHtml().isBlank())) {
      return EmailResult.failed("Email body is required");
    }

    // Check if email is enabled
    if (!emailEnabled) {
      log.info(
          "Email sending disabled. Would send to: {} subject: {}",
          request.toAddress(),
          request.subject());

      // Log the audit event even for disabled emails
      if (request.company() != null) {
        auditService.logEvent(
            request.company(),
            request.sender(),
            "EMAIL_QUEUED",
            "EMAIL",
            null,
            String.format(
                "Email queued (sending disabled) to: %s, subject: %s",
                request.toAddress(), request.subject()));
      }

      return EmailResult.disabled();
    }

    // Check if mail sender is configured
    if (mailSender == null) {
      log.warn(
          "Email enabled but JavaMailSender not configured. Would send to: {} subject: {}",
          request.toAddress(),
          request.subject());

      // Log and return queued status (for backwards compatibility with stub behavior)
      if (request.company() != null) {
        auditService.logEvent(
            request.company(),
            request.sender(),
            "EMAIL_QUEUED",
            "EMAIL",
            null,
            String.format(
                "Email queued (SMTP not configured) to: %s, subject: %s",
                request.toAddress(), request.subject()));
      }

      return EmailResult.queued(generateMessageId());
    }

    // Generate a message ID for tracking
    String messageId = generateMessageId();

    try {
      // Create and send the email via SMTP
      MimeMessage mimeMessage = mailSender.createMimeMessage();

      // Set From address
      mimeMessage.setFrom(new InternetAddress(fromAddress, fromName));

      // Set To address
      if (request.toName() != null && !request.toName().isBlank()) {
        mimeMessage.setRecipient(
            MimeMessage.RecipientType.TO,
            new InternetAddress(request.toAddress(), request.toName()));
      } else {
        mimeMessage.setRecipient(
            MimeMessage.RecipientType.TO, new InternetAddress(request.toAddress()));
      }

      // Set Subject
      mimeMessage.setSubject(request.subject());

      // Build message content
      if (request.attachments() != null && !request.attachments().isEmpty()) {
        // Multipart message with attachments
        MimeMultipart multipart = new MimeMultipart();

        // Add body part
        MimeBodyPart textPart = new MimeBodyPart();
        if (request.bodyHtml() != null && !request.bodyHtml().isBlank()) {
          textPart.setContent(request.bodyHtml(), "text/html; charset=UTF-8");
        } else {
          textPart.setText(request.bodyText(), "UTF-8");
        }
        multipart.addBodyPart(textPart);

        // Add attachments
        for (EmailAttachment attachment : request.attachments()) {
          MimeBodyPart attachmentPart = new MimeBodyPart();
          ByteArrayDataSource dataSource =
              new ByteArrayDataSource(attachment.content(), attachment.mimeType());
          attachmentPart.setDataHandler(new DataHandler(dataSource));
          attachmentPart.setFileName(attachment.filename());
          multipart.addBodyPart(attachmentPart);
        }

        mimeMessage.setContent(multipart);
      } else {
        // Simple message without attachments
        if (request.bodyHtml() != null && !request.bodyHtml().isBlank()) {
          mimeMessage.setContent(request.bodyHtml(), "text/html; charset=UTF-8");
        } else {
          mimeMessage.setText(request.bodyText(), "UTF-8");
        }
      }

      // Send the email
      mailSender.send(mimeMessage);

      log.info(
          "Email sent [{}]: to={}, subject={}, attachments={}",
          messageId,
          request.toAddress(),
          request.subject(),
          request.attachments() != null ? request.attachments().size() : 0);

      // Log audit event
      if (request.company() != null) {
        auditService.logEvent(
            request.company(),
            request.sender(),
            "EMAIL_SENT",
            "EMAIL",
            null,
            String.format(
                "Email sent to: %s, subject: %s, messageId: %s",
                request.toAddress(), request.subject(), messageId));
      }

      return EmailResult.sent(messageId);

    } catch (MessagingException | UnsupportedEncodingException e) {
      log.error("Failed to compose email to {}: {}", request.toAddress(), e.getMessage(), e);
      return EmailResult.failed("Failed to compose email: " + e.getMessage());
    } catch (MailException e) {
      log.error("Failed to send email to {}: {}", request.toAddress(), e.getMessage(), e);
      return EmailResult.failed("Failed to send email: " + e.getMessage());
    }
  }

  /**
   * Send an invoice PDF to a customer.
   *
   * @param invoice The invoice to send
   * @param pdfContent The PDF content bytes
   * @param sender The user sending the email
   * @return EmailResult indicating success or failure
   */
  public EmailResult sendInvoice(SalesInvoice invoice, byte[] pdfContent, User sender) {
    Contact customer = invoice.getContact();

    if (customer == null) {
      return EmailResult.failed("Invoice has no associated customer");
    }

    if (customer.getEmail() == null || customer.getEmail().isBlank()) {
      return EmailResult.invalidRecipient("Customer has no email address");
    }

    String subject =
        String.format(
            "Invoice %s from %s", invoice.getInvoiceNumber(), invoice.getCompany().getName());

    String bodyText = buildInvoiceEmailBody(invoice);

    EmailAttachment attachment =
        new EmailAttachment(
            "Invoice-" + invoice.getInvoiceNumber() + ".pdf", "application/pdf", pdfContent);

    EmailRequest request =
        EmailRequest.builder()
            .toContact(customer)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(invoice.getCompany())
            .sender(sender)
            .build();

    return sendEmail(request);
  }

  /**
   * Send a statement to a customer.
   *
   * @param contact The customer
   * @param company The company sending the statement
   * @param pdfContent The statement PDF content
   * @param sender The user sending the email
   * @return EmailResult indicating success or failure
   */
  public EmailResult sendStatement(
      Contact contact, Company company, byte[] pdfContent, User sender) {
    if (contact.getEmail() == null || contact.getEmail().isBlank()) {
      return EmailResult.invalidRecipient("Customer has no email address");
    }

    String subject = String.format("Statement from %s", company.getName());

    String bodyText =
        String.format(
            """
            Dear %s,

            Please find attached your statement from %s.

            If you have any questions about this statement, please contact us.

            Best regards,
            %s
            """,
            contact.getName(), company.getName(), company.getName());

    EmailAttachment attachment =
        new EmailAttachment(
            "Statement-" + contact.getCode() + ".pdf", "application/pdf", pdfContent);

    EmailRequest request =
        EmailRequest.builder()
            .toContact(contact)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

    return sendEmail(request);
  }

  /**
   * Send a remittance advice to a supplier.
   *
   * @param contact The supplier
   * @param company The company sending the remittance
   * @param pdfContent The remittance advice PDF content
   * @param sender The user sending the email
   * @return EmailResult indicating success or failure
   */
  public EmailResult sendRemittanceAdvice(
      Contact contact, Company company, byte[] pdfContent, User sender) {
    if (contact.getEmail() == null || contact.getEmail().isBlank()) {
      return EmailResult.invalidRecipient("Supplier has no email address");
    }

    String subject = String.format("Remittance Advice from %s", company.getName());

    String bodyText =
        String.format(
            """
            Dear %s,

            Please find attached remittance advice for a payment from %s.

            If you have any questions, please contact us.

            Best regards,
            %s
            """,
            contact.getName(), company.getName(), company.getName());

    EmailAttachment attachment =
        new EmailAttachment("Remittance-Advice.pdf", "application/pdf", pdfContent);

    EmailRequest request =
        EmailRequest.builder()
            .toContact(contact)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

    return sendEmail(request);
  }

  /**
   * Send a report PDF to the specified email address.
   *
   * @param toAddress Recipient email address
   * @param reportName The name of the report
   * @param pdfContent The report PDF content
   * @param company The company context
   * @param sender The user sending the email
   * @return EmailResult indicating success or failure
   */
  public EmailResult sendReport(
      String toAddress, String reportName, byte[] pdfContent, Company company, User sender) {
    String subject = String.format("%s - %s", reportName, company.getName());

    String bodyText =
        String.format(
            """
            Please find attached the %s report from %s.

            This report was generated on %s.

            Best regards,
            %s
            """,
            reportName, company.getName(), java.time.LocalDate.now().toString(), company.getName());

    EmailAttachment attachment =
        new EmailAttachment(
            reportName.replaceAll("\\s+", "-") + ".pdf", "application/pdf", pdfContent);

    EmailRequest request =
        EmailRequest.builder()
            .to(toAddress)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

    return sendEmail(request);
  }

  /** Check if email sending is enabled. */
  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  /** Get the configured from address. */
  public String getFromAddress() {
    return fromAddress;
  }

  /** Get the configured from name. */
  public String getFromName() {
    return fromName;
  }

  // Private helper methods

  private String buildInvoiceEmailBody(SalesInvoice invoice) {
    return String.format(
        """
            Dear %s,

            Please find attached invoice %s for the amount of %s.

            Due date: %s

            If you have any questions about this invoice, please contact us.

            Best regards,
            %s
            """,
        invoice.getContact().getName(),
        invoice.getInvoiceNumber(),
        formatAmount(invoice.getTotal()),
        invoice.getDueDate() != null ? invoice.getDueDate().toString() : "Upon receipt",
        invoice.getCompany().getName());
  }

  private String formatAmount(java.math.BigDecimal amount) {
    if (amount == null) {
      return "$0.00";
    }
    return String.format("$%,.2f", amount);
  }

  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    // Basic email validation regex
    String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    return email.matches(emailRegex);
  }

  private String generateMessageId() {
    return String.format(
        "%s-%d",
        java.util.UUID.randomUUID().toString().substring(0, 8), System.currentTimeMillis());
  }
}
