package com.example.application.repository;

import com.example.application.domain.RecurrenceExecutionLog;
import com.example.application.domain.RecurrenceExecutionLog.Result;
import com.example.application.domain.RecurringTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurrenceExecutionLogRepository extends JpaRepository<RecurrenceExecutionLog, Long> {

    List<RecurrenceExecutionLog> findByTemplateOrderByRunAtDesc(RecurringTemplate template);

    Page<RecurrenceExecutionLog> findByTemplateOrderByRunAtDesc(RecurringTemplate template, Pageable pageable);

    List<RecurrenceExecutionLog> findByTemplateAndResultOrderByRunAtDesc(RecurringTemplate template, Result result);

    Optional<RecurrenceExecutionLog> findTopByTemplateOrderByRunAtDesc(RecurringTemplate template);

    @Query("SELECT rel FROM RecurrenceExecutionLog rel WHERE rel.template.id = :templateId " +
           "ORDER BY rel.runAt DESC")
    List<RecurrenceExecutionLog> findRecentByTemplateId(@Param("templateId") Long templateId,
                                                         Pageable pageable);

    @Query("SELECT rel FROM RecurrenceExecutionLog rel WHERE rel.template.company.id = :companyId " +
           "AND rel.result = :result AND rel.runAt >= :since ORDER BY rel.runAt DESC")
    List<RecurrenceExecutionLog> findRecentByCompanyAndResult(@Param("companyId") Long companyId,
                                                               @Param("result") Result result,
                                                               @Param("since") Instant since);

    @Query("SELECT COUNT(rel) FROM RecurrenceExecutionLog rel WHERE rel.template.company.id = :companyId " +
           "AND rel.result = 'FAILED' AND rel.runAt >= :since")
    long countRecentFailures(@Param("companyId") Long companyId, @Param("since") Instant since);

    void deleteByTemplate(RecurringTemplate template);
}
