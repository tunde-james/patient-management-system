package com.devtunde.analyticsservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.analyticsservice.model.PatientEventLog;
import com.devtunde.analyticsservice.model.PatientEventLogKey;

public interface PatientEventLogRepository extends JpaRepository<PatientEventLog, PatientEventLogKey> {

    @Modifying
    @Query(value = """
        INSERT INTO patient_event_log(patient_id, event_type, received_at)
        VALUES (:patientId, :eventType, now())
        ON CONFLICT (patient_id, event_type)
        DO NOTHING
    """, 
    nativeQuery = true)
    int insertIfAbsent(@Param("patientId") UUID patientId, @Param("eventType") String eventType);
}
