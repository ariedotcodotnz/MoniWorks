package com.example.application.service;

import com.example.application.domain.Company;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Session-scoped service that manages the current company context.
 * For Release 1 (single company support), this auto-selects or creates the first company.
 * Future releases will support company switching via UI.
 */
@Service
@VaadinSessionScope
public class CompanyContextService {

    private final CompanyService companyService;
    private Company currentCompany;

    public CompanyContextService(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Gets the current company for this session.
     * If no company exists, creates a default one.
     * @return the current company
     */
    public Company getCurrentCompany() {
        if (currentCompany == null) {
            currentCompany = companyService.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultCompany);
        }
        return currentCompany;
    }

    /**
     * Sets the current company for this session.
     * @param company the company to set as current
     */
    public void setCurrentCompany(Company company) {
        this.currentCompany = company;
    }

    /**
     * Returns the ID of the current company.
     * @return company ID
     */
    public Long getCurrentCompanyId() {
        return getCurrentCompany().getId();
    }

    /**
     * Creates a default company for first-time setup.
     * Uses sensible defaults for NZ-based business (per spec).
     */
    private Company createDefaultCompany() {
        // Default to NZ company with April 1 fiscal year start (common in NZ)
        LocalDate fiscalYearStart = LocalDate.of(LocalDate.now().getYear(), 4, 1);
        if (LocalDate.now().isBefore(fiscalYearStart)) {
            fiscalYearStart = fiscalYearStart.minusYears(1);
        }
        return companyService.createCompany(
            "My Company",
            "NZ",
            "NZD",
            fiscalYearStart
        );
    }

    /**
     * Refreshes the current company from the database.
     * Call this after the company has been modified.
     */
    public void refresh() {
        if (currentCompany != null) {
            currentCompany = companyService.findById(currentCompany.getId())
                .orElse(null);
        }
    }
}
