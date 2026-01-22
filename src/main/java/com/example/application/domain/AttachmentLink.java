package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Links an attachment to an entity (transaction, invoice, bill, product, contact).
 * Allows one attachment to be linked to multiple entities and one entity to have
 * multiple attachments.
 */
@Entity
@Table(name = "attachment_link", indexes = {
    @Index(name = "idx_attachment_link_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_attachment_link_attachment", columnList = "attachment_id")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"attachment_id", "entity_type", "entity_id"})
})
public class AttachmentLink {

    /**
     * The type of entity an attachment can be linked to.
     */
    public enum EntityType {
        TRANSACTION,
        INVOICE,
        BILL,
        PRODUCT,
        CONTACT,
        PAYMENT_RUN,
        STATEMENT_RUN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    // Constructors
    public AttachmentLink() {
    }

    public AttachmentLink(Attachment attachment, EntityType entityType, Long entityId) {
        this.attachment = attachment;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }
}
