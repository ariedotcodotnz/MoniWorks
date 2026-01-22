package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;
import com.example.application.repository.TaxCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tax codes.
 * Tax codes define rates and types for GST/VAT calculations.
 */
@Service
@Transactional
public class TaxCodeService {

    private final TaxCodeRepository taxCodeRepository;

    public TaxCodeService(TaxCodeRepository taxCodeRepository) {
        this.taxCodeRepository = taxCodeRepository;
    }

    /**
     * Creates a new tax code for the given company.
     * @throws IllegalArgumentException if code already exists
     */
    public TaxCode createTaxCode(Company company, String code, String name,
                                  BigDecimal rate, TaxCode.TaxType type) {
        if (taxCodeRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("Tax code already exists: " + code);
        }
        TaxCode taxCode = new TaxCode(company, code, name, rate, type);
        return taxCodeRepository.save(taxCode);
    }

    /**
     * Finds all tax codes for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<TaxCode> findByCompany(Company company) {
        return taxCodeRepository.findByCompanyOrderByCode(company);
    }

    /**
     * Finds active tax codes for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<TaxCode> findActiveByCompany(Company company) {
        return taxCodeRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    /**
     * Finds a tax code by company and code.
     */
    @Transactional(readOnly = true)
    public Optional<TaxCode> findByCompanyAndCode(Company company, String code) {
        return taxCodeRepository.findByCompanyAndCode(company, code);
    }

    /**
     * Finds a tax code by ID.
     */
    @Transactional(readOnly = true)
    public Optional<TaxCode> findById(Long id) {
        return taxCodeRepository.findById(id);
    }

    /**
     * Saves a tax code.
     */
    public TaxCode save(TaxCode taxCode) {
        return taxCodeRepository.save(taxCode);
    }

    /**
     * Deactivates a tax code (soft delete).
     */
    public void deactivate(TaxCode taxCode) {
        taxCode.setActive(false);
        taxCodeRepository.save(taxCode);
    }

    /**
     * Creates default NZ GST tax codes for a new company.
     * Called during company setup to provide sensible defaults.
     */
    public void createDefaultTaxCodes(Company company) {
        // Standard GST 15%
        createTaxCode(company, "GST", "GST 15%",
            new BigDecimal("0.15"), TaxCode.TaxType.STANDARD);

        // Zero-rated
        createTaxCode(company, "ZERO", "Zero Rated",
            BigDecimal.ZERO, TaxCode.TaxType.ZERO_RATED);

        // Exempt
        createTaxCode(company, "EXEMPT", "Exempt",
            BigDecimal.ZERO, TaxCode.TaxType.EXEMPT);

        // Out of scope (e.g., wages, non-business)
        createTaxCode(company, "N/A", "No GST",
            BigDecimal.ZERO, TaxCode.TaxType.OUT_OF_SCOPE);
    }
}
