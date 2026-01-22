package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.TaxCodeRepository;
import com.example.application.repository.TaxLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaxCalculationService.
 * Tests tax calculation logic for GST/VAT including:
 * - Tax-exclusive calculations (add tax to amount)
 * - Tax-inclusive calculations (extract tax from amount)
 * - Different tax types (standard, zero-rated, exempt, out of scope)
 * - Rounding rules (half-up to 2 decimal places)
 */
@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @Mock
    private TaxCodeRepository taxCodeRepository;

    @Mock
    private TaxLineRepository taxLineRepository;

    private TaxCalculationService taxCalculationService;

    private Company company;
    private TaxCode gstCode;
    private TaxCode zeroCode;
    private TaxCode exemptCode;

    @BeforeEach
    void setUp() {
        taxCalculationService = new TaxCalculationService(taxCodeRepository, taxLineRepository);

        company = new Company("Test Company", "NZ", "NZD", LocalDate.of(2024, 4, 1));
        company.setId(1L);

        // GST 15%
        gstCode = new TaxCode(company, "GST", "GST 15%",
            new BigDecimal("0.15"), TaxCode.TaxType.STANDARD);
        gstCode.setReportBox("5");

        // Zero rated
        zeroCode = new TaxCode(company, "ZERO", "Zero Rated",
            BigDecimal.ZERO, TaxCode.TaxType.ZERO_RATED);

        // Exempt
        exemptCode = new TaxCode(company, "EXEMPT", "Exempt",
            BigDecimal.ZERO, TaxCode.TaxType.EXEMPT);
    }

    @Test
    void calculateTax_standardRate_calculatesCorrectly() {
        // NZ GST: 15% of 100.00 = 15.00
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        BigDecimal tax = taxCalculationService.calculateTax(
            company, "GST", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("15.00"), tax);
    }

    @Test
    void calculateTax_withRounding_roundsHalfUp() {
        // 15% of 33.33 = 4.9995, should round to 5.00
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        BigDecimal tax = taxCalculationService.calculateTax(
            company, "GST", new BigDecimal("33.33"));

        assertEquals(new BigDecimal("5.00"), tax);
    }

    @Test
    void calculateTax_zeroRated_returnsZero() {
        when(taxCodeRepository.findByCompanyAndCode(company, "ZERO"))
            .thenReturn(Optional.of(zeroCode));

        BigDecimal tax = taxCalculationService.calculateTax(
            company, "ZERO", new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO.setScale(2), tax);
    }

    @Test
    void calculateTax_exempt_returnsZero() {
        when(taxCodeRepository.findByCompanyAndCode(company, "EXEMPT"))
            .thenReturn(Optional.of(exemptCode));

        BigDecimal tax = taxCalculationService.calculateTax(
            company, "EXEMPT", new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO, tax);
    }

    @Test
    void calculateTax_nullTaxCode_returnsZero() {
        BigDecimal tax = taxCalculationService.calculateTax(
            company, null, new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO, tax);
    }

    @Test
    void calculateTax_unknownTaxCode_returnsZero() {
        when(taxCodeRepository.findByCompanyAndCode(company, "UNKNOWN"))
            .thenReturn(Optional.empty());

        BigDecimal tax = taxCalculationService.calculateTax(
            company, "UNKNOWN", new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO, tax);
    }

    @Test
    void calculateTaxFromInclusive_standardRate_extractsCorrectly() {
        // NZ GST inclusive: 115.00 total, tax = 115 * 0.15 / 1.15 = 15.00
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        BigDecimal tax = taxCalculationService.calculateTaxFromInclusive(
            company, "GST", new BigDecimal("115.00"));

        assertEquals(new BigDecimal("15.00"), tax);
    }

    @Test
    void calculateTaxFromInclusive_withRounding_roundsHalfUp() {
        // 100.00 GST-inclusive: tax = 100 * 0.15 / 1.15 = 13.043478... -> 13.04
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        BigDecimal tax = taxCalculationService.calculateTaxFromInclusive(
            company, "GST", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("13.04"), tax);
    }

    @Test
    void extractTaxableAmount_standardRate_extractsCorrectly() {
        // NZ GST inclusive: 115.00 total, taxable = 115 / 1.15 = 100.00
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        BigDecimal taxable = taxCalculationService.extractTaxableAmount(
            company, "GST", new BigDecimal("115.00"));

        assertEquals(new BigDecimal("100.00"), taxable);
    }

    @Test
    void extractTaxableAmount_exempt_returnsFullAmount() {
        when(taxCodeRepository.findByCompanyAndCode(company, "EXEMPT"))
            .thenReturn(Optional.of(exemptCode));

        BigDecimal taxable = taxCalculationService.extractTaxableAmount(
            company, "EXEMPT", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), taxable);
    }

    @Test
    void isValidTaxCode_existingActiveCode_returnsTrue() {
        gstCode.setActive(true);
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        assertTrue(taxCalculationService.isValidTaxCode(company, "GST"));
    }

    @Test
    void isValidTaxCode_inactiveCode_returnsFalse() {
        gstCode.setActive(false);
        when(taxCodeRepository.findByCompanyAndCode(company, "GST"))
            .thenReturn(Optional.of(gstCode));

        assertFalse(taxCalculationService.isValidTaxCode(company, "GST"));
    }

    @Test
    void isValidTaxCode_nullOrEmpty_returnsTrue() {
        // No tax code is valid (means no tax)
        assertTrue(taxCalculationService.isValidTaxCode(company, null));
        assertTrue(taxCalculationService.isValidTaxCode(company, ""));
        assertTrue(taxCalculationService.isValidTaxCode(company, "   "));
    }

    @Test
    void isValidTaxCode_unknownCode_returnsFalse() {
        when(taxCodeRepository.findByCompanyAndCode(company, "UNKNOWN"))
            .thenReturn(Optional.empty());

        assertFalse(taxCalculationService.isValidTaxCode(company, "UNKNOWN"));
    }
}
