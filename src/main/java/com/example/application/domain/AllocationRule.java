package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an allocation rule for auto-suggesting coding during bank reconciliation. Rules match
 * bank feed item descriptions to suggest accounts and tax codes.
 */
@Entity
@Table(
    name = "allocation_rule",
    indexes = {
      @Index(name = "idx_allocation_rule_lookup", columnList = "company_id, enabled, priority")
    })
public class AllocationRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(nullable = false)
  private int priority = 0;

  @NotBlank
  @Size(max = 100)
  @Column(name = "rule_name", nullable = false, length = 100)
  private String ruleName;

  @NotBlank
  @Size(max = 500)
  @Column(name = "match_expression", nullable = false, length = 500)
  private String matchExpression;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_account_id", nullable = false)
  private Account targetAccount;

  @Size(max = 10)
  @Column(name = "target_tax_code", length = 10)
  private String targetTaxCode;

  @Size(max = 255)
  @Column(name = "memo_template", length = 255)
  private String memoTemplate;

  /**
   * Minimum amount (inclusive) for this rule to match. If null, no minimum constraint. Per spec 05:
   * "rules can match on description, amount ranges, counterparty, etc."
   */
  @Column(name = "min_amount", precision = 19, scale = 2)
  private BigDecimal minAmount;

  /**
   * Maximum amount (inclusive) for this rule to match. If null, no maximum constraint. Per spec 05:
   * "rules can match on description, amount ranges, counterparty, etc."
   */
  @Column(name = "max_amount", precision = 19, scale = 2)
  private BigDecimal maxAmount;

  @Column(nullable = false)
  private boolean enabled = true;

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
  public AllocationRule() {}

  public AllocationRule(
      Company company, String ruleName, String matchExpression, Account targetAccount) {
    this.company = company;
    this.ruleName = ruleName;
    this.matchExpression = matchExpression;
    this.targetAccount = targetAccount;
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

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public String getRuleName() {
    return ruleName;
  }

  public void setRuleName(String ruleName) {
    this.ruleName = ruleName;
  }

  public String getMatchExpression() {
    return matchExpression;
  }

  public void setMatchExpression(String matchExpression) {
    this.matchExpression = matchExpression;
  }

  public Account getTargetAccount() {
    return targetAccount;
  }

  public void setTargetAccount(Account targetAccount) {
    this.targetAccount = targetAccount;
  }

  public String getTargetTaxCode() {
    return targetTaxCode;
  }

  public void setTargetTaxCode(String targetTaxCode) {
    this.targetTaxCode = targetTaxCode;
  }

  public String getMemoTemplate() {
    return memoTemplate;
  }

  public void setMemoTemplate(String memoTemplate) {
    this.memoTemplate = memoTemplate;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Tests if the given description matches this rule's expression. Currently supports simple
   * CONTAINS matching. Does not check amount constraints - use {@link #matches(String, BigDecimal)}
   * for full matching including amount range.
   */
  public boolean matches(String description) {
    return matches(description, null);
  }

  /**
   * Tests if the given description and amount match this rule. Per spec 05: "rules can match on
   * description, amount ranges, counterparty, etc."
   *
   * <p>Supports:
   *
   * <ul>
   *   <li>Description matching via CONTAINS or simple substring
   *   <li>Amount range matching via minAmount/maxAmount (uses absolute value)
   * </ul>
   *
   * @param description The bank feed item description to match against
   * @param amount The transaction amount (can be positive or negative; absolute value is used)
   * @return true if the rule matches both description and amount constraints
   */
  public boolean matches(String description, BigDecimal amount) {
    // First check description match
    if (!matchesDescription(description)) {
      return false;
    }

    // Then check amount range if specified
    if (!matchesAmount(amount)) {
      return false;
    }

    return true;
  }

  private boolean matchesDescription(String description) {
    if (description == null || matchExpression == null) {
      return false;
    }

    // Simple CONTAINS matching (case-insensitive)
    // Format: "CONTAINS 'text'" or just "text" for simple contains
    String expr = matchExpression.trim();

    if (expr.toUpperCase().startsWith("CONTAINS ")) {
      String pattern = expr.substring(9).trim();
      // Remove quotes if present
      if (pattern.startsWith("'") && pattern.endsWith("'")) {
        pattern = pattern.substring(1, pattern.length() - 1);
      }
      return description.toLowerCase().contains(pattern.toLowerCase());
    }

    // Default: simple contains
    return description.toLowerCase().contains(expr.toLowerCase());
  }

  /**
   * Tests if the given amount falls within this rule's amount range. Uses absolute value to allow
   * matching both inflows (positive) and outflows (negative).
   *
   * @param amount The amount to check (can be null, positive, or negative)
   * @return true if amount is within range or no range constraints are set
   */
  private boolean matchesAmount(BigDecimal amount) {
    // If no amount constraints, always match
    if (minAmount == null && maxAmount == null) {
      return true;
    }

    // If amount is provided, check against constraints using absolute value
    if (amount != null) {
      BigDecimal absAmount = amount.abs();

      if (minAmount != null && absAmount.compareTo(minAmount) < 0) {
        return false;
      }
      if (maxAmount != null && absAmount.compareTo(maxAmount) > 0) {
        return false;
      }
    }

    return true;
  }

  /**
   * Applies the memo template to generate a memo for a transaction. Supports placeholder
   * substitution:
   *
   * <ul>
   *   <li>{description} - replaced with original bank description
   *   <li>{amount} - replaced with transaction amount
   *   <li>{date} - replaced with transaction date (if provided)
   * </ul>
   *
   * @param description The original bank feed item description
   * @param amount The transaction amount
   * @return The processed memo, or the original description if no template is set
   */
  public String applyMemoTemplate(String description, BigDecimal amount) {
    if (memoTemplate == null || memoTemplate.isBlank()) {
      return description;
    }

    String result = memoTemplate;
    result = result.replace("{description}", description != null ? description : "");
    result = result.replace("{amount}", amount != null ? amount.toPlainString() : "");
    return result;
  }
}
