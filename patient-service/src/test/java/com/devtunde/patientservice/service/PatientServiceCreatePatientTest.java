package com.devtunde.patientservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.exception.EmailAlreadyExistsException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.kafka.KafkaPatientProducer;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

import billing.BillingResponse;

@ExtendWith(MockitoExtension.class)
class PatientServiceCreatePatientTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private BillingServiceGrpcClient billingServiceGrpcClient;

    @Mock
    private KafkaPatientProducer kafkaPatientProducer;

    @InjectMocks
    private PatientService patientService;

    @BeforeEach
    void saveReturnsItsArgumentWithGeneratedId() {
        // Real save() assigns the DB-generated id; emulate that so
        // createPatient's getId().toString() calls work. lenient() because the
        // duplicate-email path never reaches save().
        lenient().when(patientRepository.save(any())).thenAnswer(inv -> {
            Patient patient = inv.getArgument(0);
            if (patient.getId() == null) {
                ReflectionTestUtils.setField(patient, "id", UUID.randomUUID());
            }
            return patient;
        });
    }

    private static PatientReqDto request(String email) {
        return new PatientReqDto(
                "Ada Okafor",
                email,
                "12 Marina Road, Lagos",
                LocalDate.of(1990, 6, 1),
                LocalDate.of(2024, 1, 1));
    }

    @Test
    @DisplayName("billing success -> PROVISIONED + account id set + PATIENT_CREATED event")
    void createPatient_billingSuccess_provisioned() {

        when(patientRepository.existsByEmail(eq("ada@example.com"))).thenReturn(false);
        when(billingServiceGrpcClient.createBillingAccount(any(), any(), any()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId("AAAAAAAAAA")
                        .setStatus("ACTIVE")
                        .setCreated(true)
                        .build());

        PatientResDto result = patientService.createPatient(request("ada@example.com"));

        assertEquals("PROVISIONED", result.billingStatus());
        assertEquals("AAAAAAAAAA", result.billingAccountId());
        verify(kafkaPatientProducer).sendEvent(any(), eq("PATIENT_CREATED"));
        verify(kafkaPatientProducer, never()).sendEvent(any(), eq("PATIENT_CREATED_BILLING_FAILED"));
    }

    @Test
    @DisplayName("billing idempotent hit (created=false) -> still PROVISIONED")
    void createPatient_idempotentBillingHit_stillProvisioned() {

        when(patientRepository.existsByEmail(eq("idem@example.com"))).thenReturn(false);
        when(billingServiceGrpcClient.createBillingAccount(any(), any(), any()))
                .thenReturn(BillingResponse.newBuilder()
                        .setAccountId("BBBBBBBBBB")
                        .setStatus("ACTIVE")
                        .setCreated(false)
                        .build());

        PatientResDto result = patientService.createPatient(request("idem@example.com"));

        assertEquals("PROVISIONED", result.billingStatus());
        assertEquals("BBBBBBBBBB", result.billingAccountId());
        verify(kafkaPatientProducer).sendEvent(any(), eq("PATIENT_CREATED"));
    }

    @Test
    @DisplayName("billing UNAVAILABLE -> FAILED, null account id, BILLING_FAILED event")
    void createPatient_billingUnavailable_failed() {

        when(patientRepository.existsByEmail(eq("down@example.com"))).thenReturn(false);
        when(billingServiceGrpcClient.createBillingAccount(any(), any(), any()))
                .thenThrow(BillingProvisioningException.unavailable("billing down"));

        PatientResDto result = patientService.createPatient(request("down@example.com"));

        assertEquals("FAILED", result.billingStatus());
        assertNull(result.billingAccountId());
        verify(kafkaPatientProducer).sendEvent(any(), eq("PATIENT_CREATED_BILLING_FAILED"));
        verify(kafkaPatientProducer, never()).sendEvent(any(), eq("PATIENT_CREATED"));
    }

    @Test
    @DisplayName("billing DEADLINE_EXCEEDED -> FAILED (same failure path)")
    void createPatient_billingDeadlineExceeded_failed() {

        when(patientRepository.existsByEmail(eq("slow@example.com"))).thenReturn(false);
        when(billingServiceGrpcClient.createBillingAccount(any(), any(), any()))
                .thenThrow(BillingProvisioningException.deadlineExceeded("deadline blew"));

        PatientResDto result = patientService.createPatient(request("slow@example.com"));

        assertEquals("FAILED", result.billingStatus());
        assertNull(result.billingAccountId());
    }

    @Test
    @DisplayName("duplicate email -> EmailAlreadyExistsException, no billing call, no event")
    void createPatient_duplicateEmail_throwsAndStops() {

        when(patientRepository.existsByEmail(eq("dup@example.com"))).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> patientService.createPatient(request("dup@example.com")));

        verify(billingServiceGrpcClient, never()).createBillingAccount(any(), any(), any());
        verify(kafkaPatientProducer, never()).sendEvent(any(), eq("PATIENT_CREATED"));
        verify(kafkaPatientProducer, never()).sendEvent(any(), eq("PATIENT_CREATED_BILLING_FAILED"));
    }
}
