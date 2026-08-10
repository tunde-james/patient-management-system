package com.devtunde.analyticsservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.analyticsservice.dto.PatientBucketDto;
import com.devtunde.analyticsservice.dto.PatientBucketsDto;
import com.devtunde.analyticsservice.dto.PatientTotalsDto;
import com.devtunde.analyticsservice.service.AnalyticsQueryService;


@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsQueryService analyticsQueryService;

    @Test
    @DisplayName("GET /patients/total (no params) -> 200 + JSON {total_enrolled, total_billing_failed}")
    void totals_noParams_returns200AndSnakeCaseJson() throws Exception {

        when(analyticsQueryService.totals(eq(null))).thenReturn(new PatientTotalsDto(7L, 2L));

        mockMvc.perform(get("/api/v1/analytics/patients/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_enrolled").value(7))
                .andExpect(jsonPath("$.total_billing_failed").value(2));
    }

    @Test
    @DisplayName("GET /patients/total?since= -> passes since to service, returns 200")
    void totals_withSince_paramPlumbedThrough() throws Exception {

        LocalDate since = LocalDate.of(2026, 8, 1);
        when(analyticsQueryService.totals(eq(since))).thenReturn(new PatientTotalsDto(3L, 1L));

        mockMvc.perform(get("/api/v1/analytics/patients/total").param("since", since.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_enrolled").value(3));
    }

    @Test
    @DisplayName("GET /patients/by-day?from=&to= -> 200 + JSON {from, to, buckets[]} with snake_case")
    void byDay_withFromAndTo_returnsBucketsArray() throws Exception {

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 3);

        when(analyticsQueryService.byDay(eq(from), eq(to)))
                .thenReturn(new PatientBucketsDto(
                        from, to, List.of(new PatientBucketDto(LocalDate.of(2026, 8, 2), 5L, 1L))));

        mockMvc.perform(get("/api/v1/analytics/patients/by-day")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-01"))
                .andExpect(jsonPath("$.to").value("2026-08-03"))
                .andExpect(jsonPath("$.buckets[0].date").value("2026-08-02"))
                .andExpect(jsonPath("$.buckets[0].enrolled").value(5))
                .andExpect(jsonPath("$.buckets[0].billing_failed").value(1));
    }

    @Test
    @DisplayName("GET /patients/by-day (no params) -> defaults passed as null to service")
    void byDay_noParams_serviceCalledWithNulls() throws Exception {

        when(analyticsQueryService.byDay(any(), any()))
                .thenReturn(new PatientBucketsDto(LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 10), List.of()));

        mockMvc.perform(get("/api/v1/analytics/patients/by-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets").isArray())
                .andExpect(jsonPath("$.buckets.length()").value(0));
    }

    @Test
    @DisplayName("GET unknown path under /api/analytics -> 404")
    void unknownPath_returns404() throws Exception {

        mockMvc.perform(get("/api/v1/analytics/no-such-endpoint")).andExpect(status().isNotFound());
    }
}
