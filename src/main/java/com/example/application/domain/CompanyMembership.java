package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Links users to companies with their assigned role. Users can belong to multiple companies with
 * different roles.
 */
@Entity
@Table(
    name = "company_membership",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "company_id"})})
public class CompanyMembership {

  public enum MembershipStatus {
    ACTIVE,
    INACTIVE,
    PENDING
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MembershipStatus status = MembershipStatus.ACTIVE;

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
  public CompanyMembership() {}

  public CompanyMembership(User user, Company company, Role role) {
    this.user = user;
    this.company = company;
    this.role = role;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public MembershipStatus getStatus() {
    return status;
  }

  public void setStatus(MembershipStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
