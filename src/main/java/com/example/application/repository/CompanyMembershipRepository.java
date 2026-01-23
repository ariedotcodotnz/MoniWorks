package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.User;

@Repository
public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {

  Optional<CompanyMembership> findByUserAndCompany(User user, Company company);

  List<CompanyMembership> findByUser(User user);

  List<CompanyMembership> findByCompany(Company company);

  @Query(
      "SELECT cm FROM CompanyMembership cm WHERE cm.user = :user "
          + "AND cm.status = com.example.application.domain.CompanyMembership.MembershipStatus.ACTIVE")
  List<CompanyMembership> findActiveByUser(@Param("user") User user);

  @Query(
      "SELECT cm.company FROM CompanyMembership cm WHERE cm.user = :user "
          + "AND cm.status = com.example.application.domain.CompanyMembership.MembershipStatus.ACTIVE")
  List<Company> findCompaniesByActiveUser(@Param("user") User user);

  boolean existsByUserAndCompany(User user, Company company);

  @Query(
      "SELECT cm FROM CompanyMembership cm WHERE cm.user.id = :userId AND cm.company.id = :companyId")
  Optional<CompanyMembership> findByUserIdAndCompanyId(
      @Param("userId") Long userId, @Param("companyId") Long companyId);

  @Query(
      "SELECT cm FROM CompanyMembership cm WHERE cm.company.id = :companyId "
          + "AND cm.status = com.example.application.domain.CompanyMembership.MembershipStatus.ACTIVE")
  List<CompanyMembership> findActiveByCompanyId(@Param("companyId") Long companyId);

  @Query(
      "SELECT cm FROM CompanyMembership cm WHERE cm.company = :company "
          + "AND cm.status = com.example.application.domain.CompanyMembership.MembershipStatus.ACTIVE")
  List<CompanyMembership> findActiveByCompany(@Param("company") Company company);

  @Query(
      "SELECT cm FROM CompanyMembership cm JOIN FETCH cm.role WHERE cm.user = :user AND cm.company = :company")
  Optional<CompanyMembership> findByUserAndCompanyWithRole(
      @Param("user") User user, @Param("company") Company company);
}
