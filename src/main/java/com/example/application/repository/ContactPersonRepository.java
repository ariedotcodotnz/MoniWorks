package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.ContactPerson;

@Repository
public interface ContactPersonRepository extends JpaRepository<ContactPerson, Long> {

  List<ContactPerson> findByContactOrderByPrimaryDescNameAsc(Contact contact);

  Optional<ContactPerson> findByContactAndPrimaryTrue(Contact contact);

  void deleteByContact(Contact contact);

  /**
   * Find all distinct role labels used across all contacts in a company. Per spec 07: "Bulk email:
   * select by role" - enables filtering contacts by the roles of their associated people.
   */
  @Query(
      """
      SELECT DISTINCT cp.roleLabel
      FROM ContactPerson cp
      WHERE cp.contact.company = :company
        AND cp.roleLabel IS NOT NULL
        AND cp.roleLabel <> ''
      ORDER BY cp.roleLabel
      """)
  List<String> findDistinctRoleLabelsByCompany(@Param("company") Company company);

  /**
   * Find contacts that have at least one person with the specified role label. Used for bulk email
   * filtering by role per spec 07.
   */
  @Query(
      """
      SELECT DISTINCT cp.contact
      FROM ContactPerson cp
      WHERE cp.contact.company = :company
        AND LOWER(cp.roleLabel) = LOWER(:roleLabel)
        AND cp.contact.active = true
      """)
  List<Contact> findContactsByPersonRole(
      @Param("company") Company company, @Param("roleLabel") String roleLabel);
}
