package com.apitracker.monitor.repository;

import com.apitracker.monitor.entity.MonitoredApi;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredApiRepository extends JpaRepository<MonitoredApi, UUID> {

    List<MonitoredApi> findByEnabledTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
