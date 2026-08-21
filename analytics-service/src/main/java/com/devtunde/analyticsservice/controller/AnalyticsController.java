package com.devtunde.analyticsservice.controller;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.analyticsservice.dto.PatientBucketsDto;
import com.devtunde.analyticsservice.dto.PatientTotalsDto;
import com.devtunde.analyticsservice.service.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics", description = "Counts-only analytics over patient events")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleIllegalArgument() {
        // empty body → 400 with no payload
    }

    @GetMapping("/patients/total")
    @Operation(summary = "Lifetime patient totals by event type")
    public ResponseEntity<PatientTotalsDto> totals(@RequestParam(required = false) LocalDate since) {
        return ResponseEntity.ok(analyticsQueryService.totals(since));
    }

    @GetMapping("/patients/by-day")
    @Operation(summary = "Per-day patient counts (sparse, default last 30 days)")
    public ResponseEntity<PatientBucketsDto> byDay(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(analyticsQueryService.byDay(from, to));
    }
}
