package com.devtunde.analyticsservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.analyticsservice.dto.EventCount;
import com.devtunde.analyticsservice.dto.PatientBucketRow;
import com.devtunde.analyticsservice.model.PatientEventLog;
import com.devtunde.analyticsservice.model.PatientEventLogKey;

public interface PatientEventLogRepository extends JpaRepository<PatientEventLog, PatientEventLogKey> {

    @Modifying
    @Query(value = """
        INSERT INTO patient_event_log(patient_id, event_type, received_at)
        VALUES (:patientId, :eventType, now())
        ON CONFLICT (patient_id, event_type)
        DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("patientId") UUID patientId, @Param("eventType") String eventType);

    @Query("""
        SELECT new com.devtunde.analyticsservice.dto.EventCount(e.eventType, COUNT(e))
        FROM PatientEventLog e
        GROUP BY e.eventType
        """)
    List<EventCount> countByEventType();

    @Query("""
        SELECT new com.devtunde.analyticsservice.dto.EventCount(e.eventType, COUNT(e))
        FROM PatientEventLog e
        WHERE e.receivedAt >= :sinceAtStartOfDay
        GROUP BY e.eventType
        """)
    List<EventCount> countByEventTypeSince(@Param("sinceAtStartOfDay") LocalDateTime sinceAtStartOfDay);

    @Query(value = """
        SELECT received_at::date AS date, event_type AS eventType, COUNT(*) AS count
        FROM patient_event_log
        WHERE received_at >= :fromAtStartOfDay
        AND received_at < :toAtStartOfNextDay
        GROUP BY received_at::date, event_type
        ORDER BY received_at::date
        """, nativeQuery = true)
    List<PatientBucketRow> countByDay(
            @Param("fromAtStartOfDay") LocalDateTime fromAtStartOfDay,
            @Param("toAtStartOfNextDay") LocalDateTime toAtStartOfNextDay);
}
