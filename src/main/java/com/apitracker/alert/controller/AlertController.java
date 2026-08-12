package com.apitracker.alert.controller;

import com.apitracker.alert.dto.AlertResponse;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.service.AlertService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<AlertResponse> findAll(@RequestParam(required = false) AlertStatus status) {
        return alertService.findAll(status);
    }

    @GetMapping("/{id}")
    public AlertResponse findById(@PathVariable UUID id) {
        return alertService.findById(id);
    }
}
