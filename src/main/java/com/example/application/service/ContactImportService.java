package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.Contact.ContactType;
import com.example.application.domain.User;
import com.example.application.repository.ContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for importing contacts from CSV files.
 *
 * Supports flexible column mapping with required fields:
 * - code (required, max 11 chars)
 * - name (required, max 100 chars)
 * - type (optional, default CUSTOMER)
 *
 * Optional fields: category, email, phone, mobile, addressLine1, addressLine2,
 * city, region, postalCode, country, website, paymentTerms, creditLimit,
 * bankName, bankAccountNumber, bankRouting, taxOverrideCode
 */
@Service
@Transactional
public class ContactImportService {

    private final ContactRepository contactRepository;
    private final AuditService auditService;

    public ContactImportService(ContactRepository contactRepository, AuditService auditService) {
        this.contactRepository = contactRepository;
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
     * Returns information about what would be imported/updated.
     */
    public ImportResult previewImport(InputStream csvStream, Company company, boolean updateExisting)
            throws IOException {
        return processImport(csvStream, company, null, updateExisting, true);
    }

    /**
     * Imports contacts from a CSV file.
     *
     * @param csvStream The CSV file input stream
     * @param company The company to import contacts into
     * @param importedBy The user performing the import
     * @param updateExisting If true, updates existing contacts by code; if false, skips duplicates
     * @return ImportResult with counts and any errors/warnings
     */
    public ImportResult importContacts(InputStream csvStream, Company company, User importedBy,
            boolean updateExisting) throws IOException {
        return processImport(csvStream, company, importedBy, updateExisting, false);
    }

    private ImportResult processImport(InputStream csvStream, Company company, User importedBy,
            boolean updateExisting, boolean previewOnly) throws IOException {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        int skipped = 0;

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
            if (!columnMap.containsKey("code")) {
                errors.add("Required column 'code' not found in CSV header");
            }
            if (!columnMap.containsKey("name")) {
                errors.add("Required column 'name' not found in CSV header");
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
                    ProcessRowResult result = processRow(values, columnMap, company, updateExisting,
                        previewOnly, lineNumber);

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
            auditService.logEvent(company, importedBy, "CONTACTS_IMPORTED", "Contact", null,
                "Imported " + imported + " new, updated " + updated + ", skipped " + skipped + " contacts from CSV");
        }

        return ImportResult.success(imported, updated, skipped,
            errors.isEmpty() ? warnings : new ArrayList<>(errors));
    }

    private enum RowAction { IMPORTED, UPDATED, SKIPPED, ERROR }

    private record ProcessRowResult(RowAction action, String message) {}

    private ProcessRowResult processRow(String[] values, Map<String, Integer> columnMap,
            Company company, boolean updateExisting, boolean previewOnly, int lineNumber) {

        String code = getColumnValue(values, columnMap, "code");
        String name = getColumnValue(values, columnMap, "name");

        // Validate required fields
        if (code == null || code.isBlank()) {
            return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing required field 'code'");
        }
        if (name == null || name.isBlank()) {
            return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing required field 'name'");
        }

        // Validate code length
        if (code.length() > 11) {
            return new ProcessRowResult(RowAction.ERROR,
                "Line " + lineNumber + ": Code '" + code + "' exceeds maximum length of 11 characters");
        }

        // Check if contact exists
        Contact existing = contactRepository.findByCompanyAndCode(company, code).orElse(null);

        if (existing != null) {
            if (!updateExisting) {
                return new ProcessRowResult(RowAction.SKIPPED,
                    "Line " + lineNumber + ": Contact with code '" + code + "' already exists");
            }

            if (!previewOnly) {
                populateContact(existing, values, columnMap);
                contactRepository.save(existing);
            }
            return new ProcessRowResult(RowAction.UPDATED, null);
        }

        // Create new contact
        if (!previewOnly) {
            Contact contact = new Contact(company, code, name, ContactType.CUSTOMER);
            populateContact(contact, values, columnMap);
            contactRepository.save(contact);
        }
        return new ProcessRowResult(RowAction.IMPORTED, null);
    }

    private void populateContact(Contact contact, String[] values, Map<String, Integer> columnMap) {
        // Name
        String name = getColumnValue(values, columnMap, "name");
        if (name != null && !name.isBlank()) {
            contact.setName(truncate(name, 100));
        }

        // Type
        String typeStr = getColumnValue(values, columnMap, "type");
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                contact.setType(ContactType.valueOf(typeStr.toUpperCase().trim()));
            } catch (IllegalArgumentException e) {
                // Keep existing type or default
            }
        }

        // Category
        String category = getColumnValue(values, columnMap, "category");
        if (category != null) {
            contact.setCategory(truncate(category.trim(), 50));
        }

        // Contact details
        setIfPresent(contact::setEmail, getColumnValue(values, columnMap, "email"), 100);
        setIfPresent(contact::setPhone, getColumnValue(values, columnMap, "phone"), 50);
        setIfPresent(contact::setMobile, getColumnValue(values, columnMap, "mobile"), 50);
        setIfPresent(contact::setWebsite, getColumnValue(values, columnMap, "website"), 255);

        // Address
        setIfPresent(contact::setAddressLine1, getColumnValue(values, columnMap, "addressline1"), 255);
        setIfPresent(contact::setAddressLine2, getColumnValue(values, columnMap, "addressline2"), 255);
        setIfPresent(contact::setCity, getColumnValue(values, columnMap, "city"), 100);
        setIfPresent(contact::setRegion, getColumnValue(values, columnMap, "region"), 100);
        setIfPresent(contact::setPostalCode, getColumnValue(values, columnMap, "postalcode"), 20);
        setIfPresent(contact::setCountry, getColumnValue(values, columnMap, "country"), 2);

        // Payment terms
        setIfPresent(contact::setPaymentTerms, getColumnValue(values, columnMap, "paymentterms"), 50);

        // Credit limit
        String creditLimitStr = getColumnValue(values, columnMap, "creditlimit");
        if (creditLimitStr != null && !creditLimitStr.isBlank()) {
            try {
                contact.setCreditLimit(new BigDecimal(creditLimitStr.trim().replace(",", "")));
            } catch (NumberFormatException e) {
                // Skip invalid credit limit
            }
        }

        // Bank details
        setIfPresent(contact::setBankName, getColumnValue(values, columnMap, "bankname"), 100);
        setIfPresent(contact::setBankAccountNumber, getColumnValue(values, columnMap, "bankaccountnumber"), 50);
        setIfPresent(contact::setBankRouting, getColumnValue(values, columnMap, "bankrouting"), 50);

        // Tax override
        setIfPresent(contact::setTaxOverrideCode, getColumnValue(values, columnMap, "taxoverridecode"), 10);
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value, int maxLength) {
        if (value != null && !value.isBlank()) {
            setter.accept(truncate(value.trim(), maxLength));
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
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
                    // Escaped quote
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
            code,name,type,email,phone,addressLine1,city,region,postalCode,country,category,paymentTerms
            CUST001,Acme Corp,CUSTOMER,billing@acme.com,555-1234,123 Main St,Auckland,,1010,NZ,Retail,Net 30
            SUPP001,Widget Supplies,SUPPLIER,orders@widgets.com,555-5678,456 Oak Ave,Wellington,,6011,NZ,Manufacturing,Net 14
            BOTH001,Partner Inc,BOTH,contact@partner.com,555-9999,789 Pine Rd,Christchurch,,8011,NZ,Services,Due on Receipt
            """;
    }
}
