package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service for importing budget lines from CSV files.
 *
 * CSV format expects columns:
 * - account_code (required) - Account code to budget
 * - period_date (required) - Date within the period (YYYY-MM-DD) or period start date
 * - amount (required) - Budget amount
 * - department_code (optional) - Department code for departmental budgeting
 *
 * The import targets a specific budget and updates or creates budget lines as needed.
 */
@Service
@Transactional
public class BudgetImportService {

    private final BudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final AccountRepository accountRepository;
    private final PeriodRepository periodRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public BudgetImportService(BudgetRepository budgetRepository,
                               BudgetLineRepository budgetLineRepository,
                               AccountRepository accountRepository,
                               PeriodRepository periodRepository,
                               DepartmentRepository departmentRepository,
                               AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.budgetLineRepository = budgetLineRepository;
        this.accountRepository = accountRepository;
        this.periodRepository = periodRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    /**
     * Result of a CSV import operation.
     */
    public record ImportResult(
        boolean success,
        int imported,
        int updated,
        int skipped,
        List<String> errors,
        List<String> warnings
    ) {
        public static ImportResult success(int imported, int updated, int skipped, List<String> warnings) {
            return new ImportResult(true, imported, updated, skipped, List.of(), warnings);
        }

        public static ImportResult failure(List<String> errors) {
            return new ImportResult(false, 0, 0, 0, errors, List.of());
        }
    }

    /**
     * Previews the CSV import without making changes.
     */
    public ImportResult previewImport(InputStream csvStream, Budget budget, boolean updateExisting)
            throws IOException {
        return processImport(csvStream, budget, null, updateExisting, true);
    }

    /**
     * Imports budget lines from a CSV file.
     *
     * @param csvStream The CSV file input stream
     * @param budget The budget to import lines into
     * @param importedBy The user performing the import
     * @param updateExisting If true, updates existing budget lines; if false, skips duplicates
     * @return ImportResult with counts and any errors/warnings
     */
    public ImportResult importBudgetLines(InputStream csvStream, Budget budget, User importedBy,
            boolean updateExisting) throws IOException {
        return processImport(csvStream, budget, importedBy, updateExisting, false);
    }

    private ImportResult processImport(InputStream csvStream, Budget budget, User importedBy,
            boolean updateExisting, boolean previewOnly) throws IOException {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        int skipped = 0;

        Company company = budget.getCompany();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return ImportResult.failure(List.of("CSV file is empty or has no header"));
            }

            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> columnMap = buildColumnMap(headers);

            // Validate required columns
            if (!columnMap.containsKey("accountcode") && !columnMap.containsKey("account")) {
                errors.add("Required column 'account_code' or 'account' not found in CSV header");
            }
            if (!columnMap.containsKey("perioddate") && !columnMap.containsKey("period") && !columnMap.containsKey("date")) {
                errors.add("Required column 'period_date', 'period', or 'date' not found in CSV header");
            }
            if (!columnMap.containsKey("amount")) {
                errors.add("Required column 'amount' not found in CSV header");
            }
            if (!errors.isEmpty()) {
                return ImportResult.failure(errors);
            }

            // Process data rows
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                try {
                    String[] values = parseCsvLine(line);
                    ProcessRowResult result = processRow(values, columnMap, budget, company,
                        updateExisting, previewOnly, lineNumber);

                    switch (result.action) {
                        case IMPORTED -> imported++;
                        case UPDATED -> updated++;
                        case SKIPPED -> {
                            skipped++;
                            if (result.message != null) {
                                warnings.add(result.message);
                            }
                        }
                        case ERROR -> {
                            skipped++;
                            errors.add(result.message);
                        }
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                    skipped++;
                }
            }
        }

        if (!errors.isEmpty() && imported == 0 && updated == 0) {
            return ImportResult.failure(errors);
        }

        // Log the import
        if (!previewOnly && (imported > 0 || updated > 0)) {
            auditService.logEvent(company, importedBy, "BUDGET_IMPORTED", "Budget", budget.getId(),
                "Imported " + imported + " new, updated " + updated + ", skipped " + skipped +
                " budget lines from CSV into budget '" + budget.getName() + "'");
        }

        return ImportResult.success(imported, updated, skipped,
            errors.isEmpty() ? warnings : new ArrayList<>(errors));
    }

    private enum RowAction { IMPORTED, UPDATED, SKIPPED, ERROR }

    private record ProcessRowResult(RowAction action, String message) {}

    private ProcessRowResult processRow(String[] values, Map<String, Integer> columnMap,
            Budget budget, Company company, boolean updateExisting, boolean previewOnly, int lineNumber) {

        // Get account code
        String accountCode = getColumnValue(values, columnMap, "accountcode");
        if (accountCode == null) {
            accountCode = getColumnValue(values, columnMap, "account");
        }
        if (accountCode == null || accountCode.isBlank()) {
            return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing account code");
        }

        // Find account
        Account account = accountRepository.findByCompanyAndCode(company, accountCode).orElse(null);
        if (account == null) {
            return new ProcessRowResult(RowAction.ERROR,
                "Line " + lineNumber + ": Account '" + accountCode + "' not found");
        }

        // Get period date
        String periodDateStr = getColumnValue(values, columnMap, "perioddate");
        if (periodDateStr == null) {
            periodDateStr = getColumnValue(values, columnMap, "period");
        }
        if (periodDateStr == null) {
            periodDateStr = getColumnValue(values, columnMap, "date");
        }
        if (periodDateStr == null || periodDateStr.isBlank()) {
            return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing period date");
        }

        // Parse date
        LocalDate periodDate;
        try {
            periodDate = parseDate(periodDateStr);
        } catch (DateTimeParseException e) {
            return new ProcessRowResult(RowAction.ERROR,
                "Line " + lineNumber + ": Invalid date format '" + periodDateStr + "'");
        }

        // Find period
        Period period = periodRepository.findByCompanyAndDate(company, periodDate).orElse(null);
        if (period == null) {
            return new ProcessRowResult(RowAction.ERROR,
                "Line " + lineNumber + ": No period found for date " + periodDate);
        }

        // Get amount
        String amountStr = getColumnValue(values, columnMap, "amount");
        if (amountStr == null || amountStr.isBlank()) {
            return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing amount");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr.trim().replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            return new ProcessRowResult(RowAction.ERROR,
                "Line " + lineNumber + ": Invalid amount '" + amountStr + "'");
        }

        // Get optional department
        String deptCode = getColumnValue(values, columnMap, "departmentcode");
        if (deptCode == null) {
            deptCode = getColumnValue(values, columnMap, "department");
        }
        if (deptCode == null) {
            deptCode = getColumnValue(values, columnMap, "dept");
        }

        Department department = null;
        if (deptCode != null && !deptCode.isBlank()) {
            department = departmentRepository.findByCompanyAndCode(company, deptCode).orElse(null);
            if (department == null) {
                return new ProcessRowResult(RowAction.ERROR,
                    "Line " + lineNumber + ": Department '" + deptCode + "' not found");
            }
        }

        // Check if budget line exists
        Optional<BudgetLine> existing = budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(
            budget, period, account, department);

        if (existing.isPresent()) {
            if (!updateExisting) {
                return new ProcessRowResult(RowAction.SKIPPED,
                    "Line " + lineNumber + ": Budget line already exists for account " + accountCode +
                    " in period " + period.getStartDate());
            }

            if (!previewOnly) {
                BudgetLine budgetLine = existing.get();
                budgetLine.setAmount(amount);
                budgetLineRepository.save(budgetLine);
            }
            return new ProcessRowResult(RowAction.UPDATED, null);
        }

        // Create new budget line
        if (!previewOnly) {
            BudgetLine budgetLine = new BudgetLine(budget, period, account, department, amount);
            budgetLineRepository.save(budgetLine);
        }
        return new ProcessRowResult(RowAction.IMPORTED, null);
    }

    private LocalDate parseDate(String dateStr) {
        // Try common date formats
        String[] patterns = {
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "dd-MM-yyyy",
            "yyyy/MM/dd"
        };

        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }

        // Throw if no pattern matched
        throw new DateTimeParseException("Unable to parse date", dateStr, 0);
    }

    private Map<String, Integer> buildColumnMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
            map.put(header, i);
        }
        return map;
    }

    private String getColumnValue(String[] values, Map<String, Integer> columnMap, String column) {
        Integer index = columnMap.get(column);
        if (index == null || index >= values.length) {
            return null;
        }
        String value = values[index];
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Parses a CSV line, handling quoted fields with commas.
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    /**
     * Returns sample CSV format for download.
     */
    public String getSampleCsvContent() {
        return """
            account_code,period_date,amount,department_code
            4000,2024-07-01,50000,
            4000,2024-08-01,55000,
            4100,2024-07-01,15000,SALES
            5000,2024-07-01,30000,
            5100,2024-07-01,10000,ADMIN
            """;
    }
}
