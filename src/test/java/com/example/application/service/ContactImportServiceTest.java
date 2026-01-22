package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.Contact.ContactType;
import com.example.application.domain.User;
import com.example.application.repository.ContactRepository;
import com.example.application.service.ContactImportService.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContactImportService CSV import functionality.
 */
@ExtendWith(MockitoExtension.class)
class ContactImportServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private AuditService auditService;

    private ContactImportService importService;

    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        importService = new ContactImportService(contactRepository, auditService);

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        user = new User("admin@test.com", "Admin User");
        user.setId(1L);
    }

    @Test
    void importContacts_ValidCsv_ImportsAllContacts() throws IOException {
        // Given
        String csv = """
            code,name,type,email,phone
            CUST001,Acme Corp,CUSTOMER,billing@acme.com,555-1234
            SUPP001,Widget Inc,SUPPLIER,orders@widget.com,555-5678
            """;

        when(contactRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(2, result.imported());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());

        verify(contactRepository, times(2)).save(any(Contact.class));
        verify(auditService).logEvent(eq(company), eq(user), eq("CONTACTS_IMPORTED"),
            eq("Contact"), isNull(), contains("2 new"));
    }

    @Test
    void importContacts_MissingCodeColumn_ReturnsError() throws IOException {
        // Given - CSV without required 'code' column
        String csv = """
            name,type,email
            Acme Corp,CUSTOMER,billing@acme.com
            """;

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("code")));
        verify(contactRepository, never()).save(any());
    }

    @Test
    void importContacts_MissingNameColumn_ReturnsError() throws IOException {
        // Given - CSV without required 'name' column
        String csv = """
            code,type,email
            CUST001,CUSTOMER,billing@acme.com
            """;

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("name")));
        verify(contactRepository, never()).save(any());
    }

    @Test
    void importContacts_EmptyFile_ReturnsError() throws IOException {
        // Given
        String csv = "";

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty")));
    }

    @Test
    void importContacts_CodeTooLong_ReportsError() throws IOException {
        // Given - Code exceeds 11 characters
        String csv = """
            code,name
            VERYLONGCODE123,Acme Corp
            """;

        // No stubbing needed - the validation fails before repository is called

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        // When all rows fail, the result is a failure with errors
        assertFalse(result.success());
        assertEquals(0, result.imported());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("exceeds maximum length")));
    }

    @Test
    void importContacts_ExistingContact_SkipsWithoutUpdate() throws IOException {
        // Given
        String csv = """
            code,name,email
            CUST001,Acme Corp,new@acme.com
            """;

        Contact existing = new Contact(company, "CUST001", "Old Name", ContactType.CUSTOMER);
        existing.setEmail("old@acme.com");
        when(contactRepository.findByCompanyAndCode(company, "CUST001"))
            .thenReturn(Optional.of(existing));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false); // updateExisting = false

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        verify(contactRepository, never()).save(any());
    }

    @Test
    void importContacts_ExistingContact_UpdatesWhenEnabled() throws IOException {
        // Given
        String csv = """
            code,name,email
            CUST001,Acme Corp,new@acme.com
            """;

        Contact existing = new Contact(company, "CUST001", "Old Name", ContactType.CUSTOMER);
        existing.setEmail("old@acme.com");
        when(contactRepository.findByCompanyAndCode(company, "CUST001"))
            .thenReturn(Optional.of(existing));
        when(contactRepository.save(any(Contact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, true); // updateExisting = true

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        assertEquals("Acme Corp", contactCaptor.getValue().getName());
        assertEquals("new@acme.com", contactCaptor.getValue().getEmail());
    }

    @Test
    void importContacts_AllFields_ParsesCorrectly() throws IOException {
        // Given
        String csv = """
            code,name,type,category,email,phone,mobile,website,addressLine1,addressLine2,city,region,postalCode,country,paymentTerms,creditLimit,bankName,bankAccountNumber,bankRouting,taxOverrideCode
            CUST001,Acme Corp,BOTH,Retail,billing@acme.com,555-1234,555-4321,www.acme.com,123 Main St,Suite 100,Auckland,Auckland,1010,NZ,Net 30,50000,ANZ Bank,12-3456-7890123-00,12-3456,ZERO
            """;

        when(contactRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        Contact saved = contactCaptor.getValue();

        assertEquals("CUST001", saved.getCode());
        assertEquals("Acme Corp", saved.getName());
        assertEquals(ContactType.BOTH, saved.getType());
        assertEquals("Retail", saved.getCategory());
        assertEquals("billing@acme.com", saved.getEmail());
        assertEquals("555-1234", saved.getPhone());
        assertEquals("555-4321", saved.getMobile());
        assertEquals("www.acme.com", saved.getWebsite());
        assertEquals("123 Main St", saved.getAddressLine1());
        assertEquals("Suite 100", saved.getAddressLine2());
        assertEquals("Auckland", saved.getCity());
        assertEquals("Auckland", saved.getRegion());
        assertEquals("1010", saved.getPostalCode());
        assertEquals("NZ", saved.getCountry());
        assertEquals("Net 30", saved.getPaymentTerms());
        assertEquals(new BigDecimal("50000"), saved.getCreditLimit());
        assertEquals("ANZ Bank", saved.getBankName());
        assertEquals("12-3456-7890123-00", saved.getBankAccountNumber());
        assertEquals("12-3456", saved.getBankRouting());
        assertEquals("ZERO", saved.getTaxOverrideCode());
    }

    @Test
    void importContacts_QuotedFieldsWithCommas_ParsesCorrectly() throws IOException {
        // Given - CSV with commas inside quoted fields
        String csv = """
            code,name,addressLine1
            CUST001,"Acme Corp, Inc.","123 Main St, Suite 100"
            """;

        when(contactRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        assertEquals("Acme Corp, Inc.", contactCaptor.getValue().getName());
        assertEquals("123 Main St, Suite 100", contactCaptor.getValue().getAddressLine1());
    }

    @Test
    void importContacts_FlexibleColumnNames_ParsesCorrectly() throws IOException {
        // Given - CSV with different column name formats
        String csv = """
            Code,NAME,Address Line 1,postal_code,payment-terms
            CUST001,Acme Corp,123 Main St,1010,Net 30
            """;

        when(contactRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importContacts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(contactCaptor.capture());
        assertEquals("CUST001", contactCaptor.getValue().getCode());
        assertEquals("Acme Corp", contactCaptor.getValue().getName());
        assertEquals("123 Main St", contactCaptor.getValue().getAddressLine1());
        assertEquals("1010", contactCaptor.getValue().getPostalCode());
        assertEquals("Net 30", contactCaptor.getValue().getPaymentTerms());
    }

    @Test
    void previewImport_DoesNotSaveAnything() throws IOException {
        // Given
        String csv = """
            code,name
            CUST001,Acme Corp
            """;

        when(contactRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.previewImport(
            toInputStream(csv), company, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        // Preview should NOT save anything
        verify(contactRepository, never()).save(any());
        verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getSampleCsvContent_ReturnsValidFormat() {
        // When
        String sample = importService.getSampleCsvContent();

        // Then
        assertNotNull(sample);
        assertTrue(sample.contains("code,name"));
        assertTrue(sample.contains("CUST001"));
        assertTrue(sample.contains("SUPPLIER"));
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
