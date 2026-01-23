package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a saved grid view configuration per user per entity type. Allows users to customise
 * and persist grid columns, filters, and sorting. Supports the SavedView(id, companyId, userId,
 * entityType, name, columnsJson, filtersJson, sortJson) domain model.
 */
@Entity
@Table(
    name = "saved_view",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"company_id", "user_id", "entity_type", "name"})
    })
public class SavedView {

  /** Supported entity types for saved views. */
  public enum EntityType {
    TRANSACTION,
    CONTACT,
    PRODUCT,
    ACCOUNT,
    SALES_INVOICE,
    SUPPLIER_BILL,
    RECURRING_TEMPLATE,
    DASHBOARD
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 50)
  private EntityType entityType;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "columns_json", columnDefinition = "TEXT")
  private String columnsJson;

  @Column(name = "filters_json", columnDefinition = "TEXT")
  private String filtersJson;

  @Column(name = "sort_json", columnDefinition = "TEXT")
  private String sortJson;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // Constructors
  public SavedView() {}

  public SavedView(Company company, User user, EntityType entityType, String name) {
    this.company = company;
    this.user = user;
    this.entityType = entityType;
    this.name = name;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public EntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(EntityType entityType) {
    this.entityType = entityType;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getColumnsJson() {
    return columnsJson;
  }

  public void setColumnsJson(String columnsJson) {
    this.columnsJson = columnsJson;
  }

  public String getFiltersJson() {
    return filtersJson;
  }

  public void setFiltersJson(String filtersJson) {
    this.filtersJson = filtersJson;
  }

  public String getSortJson() {
    return sortJson;
  }

  public void setSortJson(String sortJson) {
    this.sortJson = sortJson;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
