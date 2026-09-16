package com.apitracker.alert.repository;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Optional<Alert> findByMonitoredApiIdAndStatus(UUID apiId, AlertStatus status);

    List<Alert> findByStatusOrderByOpenedAtDesc(AlertStatus status);

    List<Alert> findAllByOrderByOpenedAtDesc();

    long countByStatus(AlertStatus status);

    long countByStatusAndResolvedAtGreaterThanEqual(AlertStatus status, Instant resolvedAt);

    @Query("""
            SELECT AVG(a.durationSeconds)
            FROM Alert a
            WHERE a.status = com.apitracker.alert.entity.AlertStatus.RESOLVED
              AND a.resolvedAt >= :since
              AND a.durationSeconds IS NOT NULL
            """)
    Double averageDurationSecondsSince(@Param("since") Instant since);
}
