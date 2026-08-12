package com.apitracker.monitor.repository;

import com.apitracker.monitor.entity.CheckResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    Page<CheckResult> findByMonitoredApiIdOrderByCheckedAtDesc(UUID apiId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CheckResult c where c.checkedAt < :cutoff")
    int deleteByCheckedAtBefore(@Param("cutoff") Instant cutoff);

    @Query("""
            select new com.apitracker.monitor.repository.CheckAggregate(
                   count(c),
                   coalesce(sum(case when c.success = true then 1 else 0 end), 0),
                   avg(c.latencyMs),
                   max(c.latencyMs),
                   min(c.latencyMs)
            )
            from CheckResult c
            where c.monitoredApi.id = :apiId and c.checkedAt >= :from
            """)
    CheckAggregate aggregateForApiSince(@Param("apiId") UUID apiId, @Param("from") Instant from);

    @Query("""
            select new com.apitracker.monitor.repository.ApiCheckAggregate(
                   c.monitoredApi.id,
                   count(c),
                   coalesce(sum(case when c.success = true then 1 else 0 end), 0),
                   avg(c.latencyMs),
                   max(c.latencyMs),
                   min(c.latencyMs)
            )
            from CheckResult c
            where c.checkedAt >= :from
            group by c.monitoredApi.id
            """)
    List<ApiCheckAggregate> aggregateAllSince(@Param("from") Instant from);
}
