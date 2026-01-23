package com.example.application.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AllocationRule entity, focusing on amount range matching and memo template
 * functionality per spec 05: "rules can match on description, amount ranges, counterparty, etc."
 */
class AllocationRuleTest {

  private Company company;
  private Account targetAccount;
  private AllocationRule rule;

  @BeforeEach
  void setUp() {
    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    targetAccount = new Account(company, "5000", "Expenses", Account.AccountType.EXPENSE);
    targetAccount.setId(1L);

    rule = new AllocationRule(company, "Test Rule", "ACME Corp", targetAccount);
  }

  // ==================== Description Matching Tests ====================

  @Test
  void matches_SimpleContains_ReturnsTrue() {
    rule.setMatchExpression("ACME");
    assertTrue(rule.matches("Payment to ACME Corp"));
    assertTrue(rule.matches("ACME Corp Payment"));
    assertTrue(rule.matches("acme")); // case insensitive
  }

  @Test
  void matches_SimpleContains_ReturnsFalse() {
    rule.setMatchExpression("ACME");
    assertFalse(rule.matches("Payment to XYZ Corp"));
    assertFalse(rule.matches(null));
  }

  @Test
  void matches_ContainsKeyword_ReturnsTrue() {
    rule.setMatchExpression("CONTAINS 'ACME'");
    assertTrue(rule.matches("Payment to ACME Corp"));
  }

  @Test
  void matches_ContainsWithQuotes_ReturnsTrue() {
    rule.setMatchExpression("CONTAINS 'Electric Bill'");
    assertTrue(rule.matches("Monthly Electric Bill Payment"));
  }

  @Test
  void matches_NullDescription_ReturnsFalse() {
    rule.setMatchExpression("ACME");
    assertFalse(rule.matches(null));
  }

  @Test
  void matches_NullExpression_ReturnsFalse() {
    rule.setMatchExpression(null);
    assertFalse(rule.matches("Some description"));
  }

  // ==================== Amount Range Matching Tests ====================

  @Test
  void matches_NoAmountConstraints_MatchesAnyAmount() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(null);
    rule.setMaxAmount(null);

    assertTrue(rule.matches("ACME Payment", new BigDecimal("100.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("10000.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("-500.00")));
    assertTrue(rule.matches("ACME Payment", null)); // null amount should match
  }

  @Test
  void matches_MinAmountOnly_FiltersSmallAmounts() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(null);

    assertFalse(rule.matches("ACME Payment", new BigDecimal("50.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("100.00"))); // Exact match
    assertTrue(rule.matches("ACME Payment", new BigDecimal("500.00")));
  }

  @Test
  void matches_MaxAmountOnly_FiltersLargeAmounts() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(null);
    rule.setMaxAmount(new BigDecimal("500.00"));

    assertTrue(rule.matches("ACME Payment", new BigDecimal("100.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("500.00"))); // Exact match
    assertFalse(rule.matches("ACME Payment", new BigDecimal("501.00")));
  }

  @Test
  void matches_AmountRange_FiltersOutsideRange() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(new BigDecimal("500.00"));

    assertFalse(rule.matches("ACME Payment", new BigDecimal("50.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("100.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("300.00")));
    assertTrue(rule.matches("ACME Payment", new BigDecimal("500.00")));
    assertFalse(rule.matches("ACME Payment", new BigDecimal("501.00")));
  }

  @Test
  void matches_NegativeAmount_UsesAbsoluteValue() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(new BigDecimal("500.00"));

    // Negative amounts (payments) should use absolute value for matching
    assertTrue(rule.matches("ACME Payment", new BigDecimal("-300.00")));
    assertFalse(rule.matches("ACME Payment", new BigDecimal("-50.00")));
    assertFalse(rule.matches("ACME Payment", new BigDecimal("-600.00")));
  }

  @Test
  void matches_DescriptionFailsButAmountInRange_ReturnsFalse() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(new BigDecimal("500.00"));

    // Description doesn't match, so should fail regardless of amount
    assertFalse(rule.matches("XYZ Corp Payment", new BigDecimal("300.00")));
  }

  @Test
  void matches_DescriptionMatchesButAmountOutOfRange_ReturnsFalse() {
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(new BigDecimal("500.00"));

    // Description matches but amount is out of range
    assertFalse(rule.matches("ACME Payment", new BigDecimal("50.00")));
  }

  @Test
  void matches_WithAmountConstraints_NullAmountStillMatches() {
    // When amount constraints are set but no amount is provided,
    // we should still match (for backward compatibility with description-only matching)
    rule.setMatchExpression("ACME");
    rule.setMinAmount(new BigDecimal("100.00"));
    rule.setMaxAmount(new BigDecimal("500.00"));

    assertTrue(rule.matches("ACME Payment", null));
  }

  // ==================== Memo Template Tests ====================

  @Test
  void applyMemoTemplate_NoTemplate_ReturnsOriginalDescription() {
    rule.setMemoTemplate(null);
    String result = rule.applyMemoTemplate("ACME Corp Payment", new BigDecimal("100.00"));
    assertEquals("ACME Corp Payment", result);
  }

  @Test
  void applyMemoTemplate_EmptyTemplate_ReturnsOriginalDescription() {
    rule.setMemoTemplate("   ");
    String result = rule.applyMemoTemplate("ACME Corp Payment", new BigDecimal("100.00"));
    assertEquals("ACME Corp Payment", result);
  }

  @Test
  void applyMemoTemplate_StaticTemplate_ReturnsTemplate() {
    rule.setMemoTemplate("Office Supplies");
    String result = rule.applyMemoTemplate("ACME Corp Payment", new BigDecimal("100.00"));
    assertEquals("Office Supplies", result);
  }

  @Test
  void applyMemoTemplate_WithDescriptionPlaceholder_ReplacesPlaceholder() {
    rule.setMemoTemplate("Payment: {description}");
    String result = rule.applyMemoTemplate("ACME Corp", new BigDecimal("100.00"));
    assertEquals("Payment: ACME Corp", result);
  }

  @Test
  void applyMemoTemplate_WithAmountPlaceholder_ReplacesPlaceholder() {
    rule.setMemoTemplate("Amount: ${amount}");
    String result = rule.applyMemoTemplate("ACME Corp", new BigDecimal("100.00"));
    assertEquals("Amount: $100.00", result);
  }

  @Test
  void applyMemoTemplate_WithBothPlaceholders_ReplacesBoth() {
    rule.setMemoTemplate("{description} - ${amount}");
    String result = rule.applyMemoTemplate("Office Supplies", new BigDecimal("250.50"));
    assertEquals("Office Supplies - $250.50", result);
  }

  @Test
  void applyMemoTemplate_NullDescription_HandlesGracefully() {
    rule.setMemoTemplate("Payment: {description}");
    String result = rule.applyMemoTemplate(null, new BigDecimal("100.00"));
    assertEquals("Payment: ", result);
  }

  @Test
  void applyMemoTemplate_NullAmount_HandlesGracefully() {
    rule.setMemoTemplate("Amount: {amount}");
    String result = rule.applyMemoTemplate("ACME Corp", null);
    assertEquals("Amount: ", result);
  }

  // ==================== Combined Matching Tests ====================

  @Test
  void matches_AllCriteriaMatch_ReturnsTrue() {
    rule.setMatchExpression("Electric");
    rule.setMinAmount(new BigDecimal("50.00"));
    rule.setMaxAmount(new BigDecimal("200.00"));

    // All criteria match
    assertTrue(rule.matches("Electric Company Bill", new BigDecimal("150.00")));
  }

  @Test
  void matches_TypicalBankReconciliationScenario_WorksCorrectly() {
    // Scenario: Rule for small office supply purchases
    rule.setRuleName("Office Supplies < $100");
    rule.setMatchExpression("Staples");
    rule.setMinAmount(null);
    rule.setMaxAmount(new BigDecimal("100.00"));
    rule.setMemoTemplate("Office Supplies: {description}");

    // Should match small Staples purchases
    assertTrue(rule.matches("STAPLES #1234", new BigDecimal("-45.99")));
    assertTrue(rule.matches("Staples Online", new BigDecimal("-99.00")));

    // Should NOT match large Staples purchases (maybe furniture)
    assertFalse(rule.matches("Staples Office Chair", new BigDecimal("-299.00")));

    // Verify memo template
    String memo = rule.applyMemoTemplate("STAPLES #1234", new BigDecimal("-45.99"));
    assertEquals("Office Supplies: STAPLES #1234", memo);
  }
}
