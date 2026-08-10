package com.devtunde.analyticsservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.devtunde.analyticsservice.dto.EventCount;
import com.devtunde.analyticsservice.dto.PatientBucketDto;
import com.devtunde.analyticsservice.dto.PatientBucketRow;
import com.devtunde.analyticsservice.dto.PatientBucketsDto;
import com.devtunde.analyticsservice.dto.PatientTotalsDto;
import com.devtunde.analyticsservice.eventtype.AnalyticsEventType;
import com.devtunde.analyticsservice.repository.PatientEventLogRepository;

@Service
public class AnalyticsQueryService {

    private final PatientEventLogRepository patientEventLogRepository;
    private final Clock clock;

    public AnalyticsQueryService(PatientEventLogRepository patientEventLogRepository, Clock clock) {
        this.patientEventLogRepository = patientEventLogRepository;
        this.clock = clock;
    }

    public PatientTotalsDto totals(LocalDate since) {

        LocalDateTime sinceAtStartOfDay = (since == null) ? null : since.atStartOfDay();
        List<EventCount> rows = sinceAtStartOfDay == null
                ? patientEventLogRepository.countByEventType()
                : patientEventLogRepository.countByEventTypeSince(sinceAtStartOfDay);

        long enrolled = 0;
        long billingFailed = 0;

        for (EventCount row : rows) {
            if (AnalyticsEventType.PATIENT_CREATED.name().equals(row.eventType())) {
                enrolled = row.count();
            } else if (AnalyticsEventType.PATIENT_CREATED_BILLING_FAILED.name().equals(row.eventType())) {
                billingFailed = row.count();
            }
        }

        return new PatientTotalsDto(enrolled, billingFailed);
    }

    public PatientBucketsDto byDay(LocalDate from, LocalDate to) {

        if (from == null || to == null) {
            to = LocalDate.now(clock);
            from = to.minusDays(29);
        }

        LocalDateTime fromAtStartOfDay = from.atStartOfDay();
        LocalDateTime toAtStartOfNextDay = to.plusDays(1).atStartOfDay();
        List<PatientBucketRow> rows = patientEventLogRepository.countByDay(fromAtStartOfDay, toAtStartOfNextDay);

        Map<LocalDate, PatientBucketDto> byDate = new HashMap<>();

        for (PatientBucketRow row : rows) {
            PatientBucketDto existing = byDate.get(row.localDate());
            long enrolled = (AnalyticsEventType.PATIENT_CREATED.name().equals(row.eventType())) ? row.count() : 0L;
            long billingFailed =
                    (AnalyticsEventType.PATIENT_CREATED_BILLING_FAILED.name().equals(row.eventType()))
                            ? row.count()
                            : 0L;

            if (existing == null) {
                byDate.put(row.localDate(), new PatientBucketDto(row.localDate(), enrolled, billingFailed));
            } else {
                byDate.put(
                        row.localDate(),
                        new PatientBucketDto(
                                row.localDate(),
                                existing.enrolled() + enrolled,
                                existing.billingFailed() + billingFailed));
            }
        }

        List<PatientBucketDto> sorted = new ArrayList<>(byDate.values());

        sorted.sort(Comparator.comparing(PatientBucketDto::date));

        return new PatientBucketsDto(from, to, sorted);
    }
}
