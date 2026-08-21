package com.devtunde.analyticsservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devtunde.analyticsservice.dto.EventCount;
import com.devtunde.analyticsservice.dto.PatientBucketDto;
import com.devtunde.analyticsservice.dto.PatientBucketRow;
import com.devtunde.analyticsservice.dto.PatientBucketsDto;
import com.devtunde.analyticsservice.dto.PatientTotalsDto;
import com.devtunde.analyticsservice.repository.PatientEventLogRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    // Freeze time at 2026-08-10T12:00:00Z so LocalDate.now(clock) is 2026-08-10
    // deterministically — tests don't depend on the wall-clock day.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PatientEventLogRepository patientEventLogRepository;

    private AnalyticsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AnalyticsQueryService(patientEventLogRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("totals -> sums only PATIENT_CREATED and PATIENT_CREATED_BILLING_FAILED rows")
    void totals_sumByEventType() {
        when(patientEventLogRepository.countByEventType())
                .thenReturn(List.of(
                        new EventCount("PATIENT_CREATED", 7L),
                        new EventCount("PATIENT_CREATED_BILLING_FAILED", 2L),
                        new EventCount("SOME_OTHER_TYPE", 999L)));

        PatientTotalsDto totals = queryService.totals(null);

        assertEquals(7L, totals.totalEnrolled());
        assertEquals(2L, totals.totalBillingFailed());
    }

    @Test
    @DisplayName("totals(since) -> converts LocalDate to LocalDateTime at start-of-day")
    void totals_withSincePassesStartOfDay() {
        LocalDate since = LocalDate.of(2026, Month.AUGUST, 1);
        LocalDateTime sinceAtStartOfDay = LocalDateTime.of(2026, Month.AUGUST, 1, 0, 0);

        when(patientEventLogRepository.countByEventTypeSince(eq(sinceAtStartOfDay)))
                .thenReturn(List.of(new EventCount("PATIENT_CREATED", 3L)));

        PatientTotalsDto totals = queryService.totals(since);

        assertEquals(3L, totals.totalEnrolled());
        assertEquals(0L, totals.totalBillingFailed());
    }

    @Test
    @DisplayName("byDay -> collapses per-(day, event_type) rows into one bucket per date")
    void byDay_collapsesRowsPerDate() {
        LocalDate from = LocalDate.of(2026, Month.AUGUST, 1);
        LocalDate to = LocalDate.of(2026, Month.AUGUST, 9);
        LocalDate day = LocalDate.of(2026, Month.AUGUST, 5);

        when(patientEventLogRepository.countByDay(
                        eq(from.atStartOfDay()), eq(to.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(
                        bucketRow(day, "PATIENT_CREATED", 4L), bucketRow(day, "PATIENT_CREATED_BILLING_FAILED", 1L)));

        PatientBucketsDto dto = queryService.byDay(from, to);

        assertEquals(from, dto.from());
        assertEquals(to, dto.to());
        assertEquals(1, dto.buckets().size(), "two event types on the same day collapse to one bucket");

        PatientBucketDto only = dto.buckets().get(0);

        assertEquals(day, only.date());
        assertEquals(4L, only.enrolled());
        assertEquals(1L, only.billingFailed());
    }

    @Test
    @DisplayName("byDay -> sparse: a day with zero events does not appear in buckets")
    void byDay_omitsZeroDays() {
        LocalDate mon = LocalDate.of(2026, Month.AUGUST, 3);
        LocalDate wed = LocalDate.of(2026, Month.AUGUST, 5);

        when(patientEventLogRepository.countByDay(any(), any()))
                .thenReturn(List.of(bucketRow(mon, "PATIENT_CREATED", 1L), bucketRow(wed, "PATIENT_CREATED", 1L)));

        PatientBucketsDto dto = queryService.byDay(mon, wed);

        assertEquals(2, dto.buckets().size());
        assertTrue(
                dto.buckets().stream().noneMatch(b -> b.date().equals(LocalDate.of(2026, Month.AUGUST, 4))),
                "Aug 4 (zero events) must be omitted from buckets");
    }

    @Test
    @DisplayName("byDay -> buckets sorted ascending by date")
    void byDay_sortedAscending() {
        LocalDate day1 = LocalDate.of(2026, Month.AUGUST, 5);
        LocalDate day2 = LocalDate.of(2026, Month.AUGUST, 6);

        when(patientEventLogRepository.countByDay(any(), any()))
                .thenReturn(List.of(bucketRow(day2, "PATIENT_CREATED", 3L), bucketRow(day1, "PATIENT_CREATED", 2L)));

        PatientBucketsDto dto = queryService.byDay(day1, day2);

        assertEquals(
                List.of(day1, day2),
                dto.buckets().stream().map(PatientBucketDto::date).toList(),
                "service must sort buckets ascending by date");
    }

    @Test
    @DisplayName("byDay (defaults) -> 30-day window from the frozen clock's today")
    void byDay_defaultWindowUsesClockToday() {

        // With FIXED_CLOCK, LocalDate.now(clock) = 2026-08-10.
        // So default window: to = 2026-08-10, from = 2026-08-10.minusDays(29) = 2026-07-12
        LocalDate expectedFrom = LocalDate.of(2026, Month.JULY, 12);
        LocalDate expectedTo = LocalDate.of(2026, Month.AUGUST, 10);

        when(patientEventLogRepository.countByDay(
                        eq(expectedFrom.atStartOfDay()),
                        eq(expectedTo.plusDays(1).atStartOfDay())))
                .thenReturn(List.of());

        PatientBucketsDto dto = queryService.byDay(null, null);

        assertEquals(expectedFrom, dto.from());
        assertEquals(expectedTo, dto.to());
        assertEquals(0, dto.buckets().size());
    }

    @Test
    @DisplayName("byDay: only from supplied -> ends at clock today, starts at from")
    void byDay_fromOnly_defaultsTo() {
        LocalDate from = LocalDate.of(2026, Month.AUGUST, 1);
        LocalDate expectedTo = LocalDate.of(2026, Month.AUGUST, 10);

        when(patientEventLogRepository.countByDay(
                        eq(from.atStartOfDay()), eq(expectedTo.plusDays(1).atStartOfDay())))
                .thenReturn(List.of());

        PatientBucketsDto dto = queryService.byDay(from, null);

        assertEquals(from, dto.from());
        assertEquals(expectedTo, dto.to());
    }

    @Test
    @DisplayName("byDay: only to supplied -> starts 29 days before to")
    void byDay_toOnly_defaultsFrom() {
        LocalDate to = LocalDate.of(2026, Month.AUGUST, 10);
        LocalDate expectedFrom = to.minusDays(29); // 2026-07-12

        when(patientEventLogRepository.countByDay(
                        eq(expectedFrom.atStartOfDay()), eq(to.plusDays(1).atStartOfDay())))
                .thenReturn(List.of());

        PatientBucketsDto dto = queryService.byDay(null, to);

        assertEquals(expectedFrom, dto.from());
        assertEquals(to, dto.to());
    }

    @Test
    @DisplayName("byDay: from after to -> throws IllegalArgumentException")
    void byDay_invertedRange_throws() {
        LocalDate from = LocalDate.of(2026, Month.AUGUST, 5);
        LocalDate to = LocalDate.of(2026, Month.AUGUST, 1);

        assertThrows(IllegalArgumentException.class, () -> queryService.byDay(from, to));

        verify(patientEventLogRepository, never()).countByDay(any(), any());
    }

    private static PatientBucketRow bucketRow(LocalDate date, String eventType, long count) {
        return new PatientBucketRow(Date.valueOf(date), eventType, count);
    }
}
