package com.apitracker.monitor.controller;

import com.apitracker.monitor.dto.FleetSummaryResponse;
import com.apitracker.monitor.service.UptimeSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final UptimeSummaryService uptimeSummaryService;

    @GetMapping
    public FleetSummaryResponse fleetSummary(
            @RequestParam(name = "hours", defaultValue = "24") int hours) {
        return uptimeSummaryService.summarizeFleet(hours);
    }
}
