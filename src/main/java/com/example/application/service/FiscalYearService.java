package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.FiscalYear;
import com.example.application.domain.Period;
import com.example.application.repository.FiscalYearRepository;
import com.example.application.repository.PeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing fiscal years and periods.
 * Handles fiscal year creation with automatic period generation,
 * and period status management (open/lock).
 */
@Service
@Transactional
public class FiscalYearService {

    private final FiscalYearRepository fiscalYearRepository;
    private final PeriodRepository periodRepository;

    public FiscalYearService(FiscalYearRepository fiscalYearRepository,
                             PeriodRepository periodRepository) {
        this.fiscalYearRepository = fiscalYearRepository;
        this.periodRepository = periodRepository;
    }

    /**
     * Creates a new fiscal year with 12 monthly periods.
     */
    public FiscalYear createFiscalYear(Company company, LocalDate startDate, String label) {
        LocalDate endDate = startDate.plusYears(1).minusDays(1);

        FiscalYear fiscalYear = new FiscalYear(company, startDate, endDate, label);

        // Create 12 monthly periods
        LocalDate periodStart = startDate;
        for (int i = 1; i <= 12; i++) {
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }
            Period period = new Period(fiscalYear, i, periodStart, periodEnd);
            fiscalYear.addPeriod(period);
            periodStart = periodEnd.plusDays(1);
        }

        return fiscalYearRepository.save(fiscalYear);
    }

    /**
     * Creates the next fiscal year based on the latest one.
     */
    public FiscalYear createNextFiscalYear(Company company) {
        FiscalYear latest = fiscalYearRepository.findLatestByCompany(company)
            .orElseThrow(() -> new IllegalStateException("No existing fiscal year found"));

        LocalDate newStartDate = latest.getEndDate().plusDays(1);
        String newLabel = newStartDate.getYear() + "-" + newStartDate.plusYears(1).getYear();

        return createFiscalYear(company, newStartDate, newLabel);
    }

    /**
     * Finds all fiscal years for a company, ordered by start date.
     */
    @Transactional(readOnly = true)
    public List<FiscalYear> findByCompany(Company company) {
        return fiscalYearRepository.findByCompanyOrderByStartDate(company);
    }

    /**
     * Finds the fiscal year containing the given date.
     */
    @Transactional(readOnly = true)
    public Optional<FiscalYear> findByCompanyAndDate(Company company, LocalDate date) {
        return fiscalYearRepository.findByCompanyAndDate(company, date);
    }

    /**
     * Finds the latest fiscal year for a company.
     */
    @Transactional(readOnly = true)
    public Optional<FiscalYear> findLatestByCompany(Company company) {
        return fiscalYearRepository.findLatestByCompany(company);
    }

    /**
     * Finds a fiscal year by ID.
     */
    @Transactional(readOnly = true)
    public Optional<FiscalYear> findById(Long id) {
        return fiscalYearRepository.findById(id);
    }

    /**
     * Finds all periods for a fiscal year.
     */
    @Transactional(readOnly = true)
    public List<Period> findPeriodsByFiscalYear(FiscalYear fiscalYear) {
        return periodRepository.findByFiscalYearOrderByPeriodIndex(fiscalYear);
    }

    /**
     * Finds all periods for a company.
     */
    @Transactional(readOnly = true)
    public List<Period> findPeriodsByCompany(Company company) {
        return periodRepository.findByCompanyOrderByStartDate(company);
    }

    /**
     * Finds the period containing the given date for a company.
     */
    @Transactional(readOnly = true)
    public Optional<Period> findPeriodByCompanyAndDate(Company company, LocalDate date) {
        return periodRepository.findByCompanyAndDate(company, date);
    }

    /**
     * Finds a period by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Period> findPeriodById(Long id) {
        return periodRepository.findById(id);
    }

    /**
     * Locks a period, preventing any posting.
     * @throws IllegalStateException if period is already locked
     */
    public Period lockPeriod(Period period) {
        if (period.isLocked()) {
            throw new IllegalStateException("Period is already locked");
        }
        period.setStatus(Period.Status.LOCKED);
        return periodRepository.save(period);
    }

    /**
     * Unlocks a period, allowing posting.
     * @throws IllegalStateException if period is already open
     */
    public Period unlockPeriod(Period period) {
        if (period.isOpen()) {
            throw new IllegalStateException("Period is already open");
        }
        period.setStatus(Period.Status.OPEN);
        return periodRepository.save(period);
    }

    /**
     * Checks if a date falls within an open period for the company.
     */
    @Transactional(readOnly = true)
    public boolean isDateInOpenPeriod(Company company, LocalDate date) {
        return periodRepository.findByCompanyAndDate(company, date)
            .map(Period::isOpen)
            .orElse(false);
    }

    /**
     * Saves a fiscal year.
     */
    public FiscalYear save(FiscalYear fiscalYear) {
        return fiscalYearRepository.save(fiscalYear);
    }

    /**
     * Saves a period.
     */
    public Period savePeriod(Period period) {
        return periodRepository.save(period);
    }
}
