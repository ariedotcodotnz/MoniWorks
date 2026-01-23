package com.example.application.ui.components;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.application.domain.Attachment;
import com.example.application.domain.AttachmentLink.EntityType;
import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.service.AttachmentService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;

/**
 * Reusable UI component for managing attachments on entities. Provides file upload, download, and
 * removal functionality. Used by TransactionsView, SalesInvoicesView, SupplierBillsView,
 * ProductsView, and ContactsView.
 */
public class AttachmentSection extends VerticalLayout {

  private final AttachmentService attachmentService;
  private final EntityType entityType;
  private final Long entityId;
  private final Company company;
  private final User currentUser;
  private final boolean isNew;
  private final List<PendingAttachment> pendingAttachments = new ArrayList<>();
  private final VerticalLayout attachmentsContainer;

  /** Holds data for files that have been selected but not yet saved to the entity. */
  public static class PendingAttachment {
    private final String filename;
    private final String mimeType;
    private final byte[] content;

    public PendingAttachment(String filename, String mimeType, byte[] content) {
      this.filename = filename;
      this.mimeType = mimeType;
      this.content = content;
    }

    public String getFilename() {
      return filename;
    }

    public String getMimeType() {
      return mimeType;
    }

    public byte[] getContent() {
      return content;
    }
  }

  /**
   * Creates an attachment section for an entity.
   *
   * @param attachmentService The attachment service for persistence
   * @param entityType The type of entity (INVOICE, BILL, PRODUCT, CONTACT)
   * @param entityId The ID of the entity, or null for new entities
   * @param company The current company context
   * @param currentUser The current user
   */
  public AttachmentSection(
      AttachmentService attachmentService,
      EntityType entityType,
      Long entityId,
      Company company,
      User currentUser) {
    this.attachmentService = attachmentService;
    this.entityType = entityType;
    this.entityId = entityId;
    this.company = company;
    this.currentUser = currentUser;
    this.isNew = entityId == null;

    setPadding(false);
    setSpacing(true);

    // Title
    H3 title = new H3("Attachments");

    // Container for existing and pending attachments
    attachmentsContainer = new VerticalLayout();
    attachmentsContainer.setPadding(false);
    attachmentsContainer.setSpacing(false);

    // Load existing attachments if editing
    if (!isNew) {
      loadExistingAttachments();
    }

    // Upload component
    MemoryBuffer uploadBuffer = new MemoryBuffer();
    Upload upload = new Upload(uploadBuffer);
    upload.setAcceptedFileTypes(".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".tiff", ".bmp");
    upload.setMaxFiles(5);
    upload.setMaxFileSize(10 * 1024 * 1024); // 10 MB
    upload.setDropLabel(new Span("Drop file here or click to upload"));

    upload.addSucceededListener(
        event -> {
          String fileName = event.getFileName();
          String mimeType = event.getMIMEType();
          try {
            byte[] content = uploadBuffer.getInputStream().readAllBytes();
            PendingAttachment pending = new PendingAttachment(fileName, mimeType, content);
            pendingAttachments.add(pending);

            // Add to UI
            HorizontalLayout pendingRow = new HorizontalLayout();
            pendingRow.setAlignItems(FlexComponent.Alignment.CENTER);
            pendingRow.add(
                VaadinIcon.FILE.create(),
                new Span(fileName + " (pending)"),
                createRemovePendingButton(pending, pendingRow));
            attachmentsContainer.add(pendingRow);

            Notification.show(
                    "File ready to upload: " + fileName, 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          } catch (IOException ex) {
            Notification.show(
                    "Error reading file: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    upload.addFailedListener(
        event ->
            Notification.show(
                    "Upload failed: " + event.getReason().getMessage(),
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR));

    add(title, attachmentsContainer, upload);
  }

  /** Loads and displays existing attachments for the entity. */
  private void loadExistingAttachments() {
    List<Attachment> existingAttachments = attachmentService.findByEntity(entityType, entityId);
    for (Attachment att : existingAttachments) {
      attachmentsContainer.add(createExistingAttachmentRow(att));
    }
  }

  /**
   * Creates a row displaying an existing attachment with download and remove buttons.
   *
   * @param attachment The attachment to display
   * @return The layout row
   */
  private HorizontalLayout createExistingAttachmentRow(Attachment attachment) {
    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(FlexComponent.Alignment.CENTER);

    // Create download link
    StreamResource resource =
        new StreamResource(
            attachment.getFilename(),
            () -> {
              try {
                byte[] fileContent = attachmentService.getFileContent(attachment);
                return new ByteArrayInputStream(fileContent);
              } catch (Exception ex) {
                return new ByteArrayInputStream(new byte[0]);
              }
            });

    Anchor downloadLink = new Anchor(resource, attachment.getFilename());
    downloadLink.getElement().setAttribute("download", true);

    Span sizeSpan = new Span(" (" + attachment.getFormattedSize() + ")");
    sizeSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeBtn.addThemeVariants(
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
    removeBtn.getElement().setAttribute("title", "Remove attachment");
    removeBtn.addClickListener(
        e -> {
          attachmentService.unlinkFromEntity(attachment, entityType, entityId);
          attachmentsContainer.remove(row);
          Notification.show("Attachment removed", 2000, Notification.Position.BOTTOM_START);
        });

    row.add(VaadinIcon.FILE.create(), downloadLink, sizeSpan, removeBtn);
    return row;
  }

  /**
   * Creates a button to remove a pending attachment.
   *
   * @param pending The pending attachment
   * @param row The row containing the attachment
   * @return The remove button
   */
  private Button createRemovePendingButton(PendingAttachment pending, HorizontalLayout row) {
    Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeBtn.addThemeVariants(
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
    removeBtn.addClickListener(
        e -> {
          pendingAttachments.remove(pending);
          attachmentsContainer.remove(row);
        });
    return removeBtn;
  }

  /**
   * Saves all pending attachments and links them to the specified entity. Call this after the
   * entity has been saved and has an ID.
   *
   * @param savedEntityId The ID of the saved entity
   */
  public void savePendingAttachments(Long savedEntityId) {
    for (PendingAttachment pending : pendingAttachments) {
      try {
        attachmentService.uploadAndLink(
            company,
            pending.getFilename(),
            pending.getMimeType(),
            pending.getContent(),
            currentUser,
            entityType,
            savedEntityId);
      } catch (Exception ex) {
        Notification.show(
                "Failed to save attachment: " + pending.getFilename() + " - " + ex.getMessage(),
                3000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    }
    pendingAttachments.clear();
  }

  /**
   * Returns the list of pending attachments. Useful if the caller needs to handle the save process
   * differently.
   *
   * @return The list of pending attachments
   */
  public List<PendingAttachment> getPendingAttachments() {
    return new ArrayList<>(pendingAttachments);
  }

  /**
   * Returns true if there are pending attachments that need to be saved.
   *
   * @return True if there are pending attachments
   */
  public boolean hasPendingAttachments() {
    return !pendingAttachments.isEmpty();
  }
}
