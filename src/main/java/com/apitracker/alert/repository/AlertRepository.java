package com.apitracker.alert.repository;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Optional<Alert> findByMonitoredApiIdAndStatus(UUID apiId, AlertStatus status);

    List<Alert> findByStatusOrderByOpenedAtDesc(AlertStatus status);

    List<Alert> findAllByOrderByOpenedAtDesc();
}
