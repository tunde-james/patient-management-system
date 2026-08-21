package com.devtunde.patientservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.patientservice.dto.PatientDobUpdateReqDto;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.dto.PatientUpdateReqDto;
import com.devtunde.patientservice.exception.BillingProvisioningException;
import com.devtunde.patientservice.exception.EmailAlreadyExistsException;
import com.devtunde.patientservice.exception.PatientNotFoundException;
import com.devtunde.patientservice.grpc.BillingServiceGrpcClient;
import com.devtunde.patientservice.kafka.KafkaPatientProducer;
import com.devtunde.patientservice.kafka.event_type.PatientEventType;
import com.devtunde.patientservice.mapper.PatientMapper;
import com.devtunde.patientservice.model.BillingProvisioningStatus;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaPatientProducer kafkaPatientProducer;

    public PatientService(
            PatientRepository patientRepository,
            BillingServiceGrpcClient billingServiceGrpcClient,
            KafkaPatientProducer kafkaPatientProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaPatientProducer = kafkaPatientProducer;
    }

    public List<PatientResDto> getPatients() {

        List<Patient> patients = patientRepository.findAll();

        List<PatientResDto> patientResDtos =
                patients.stream().map(PatientMapper::toDTO).toList();

        return patientResDtos;
    }

    public PatientResDto getPatient(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException("Patient not found with ID: " + id);
        }

        return patientRepository
                .findById(id)
                .map(PatientMapper::toDTO)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + id));
    }

    public PatientResDto createPatient(PatientReqDto patientReqDto) {

        if (patientRepository.existsByEmail(patientReqDto.email())) {
            throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientReqDto.email());
        }

        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientReqDto));

        try {
            billing.BillingResponse response = billingServiceGrpcClient.createBillingAccount(
                    newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());

            newPatient.setBillingAccountId(response.getAccountId());
            newPatient.setBillingStatus(BillingProvisioningStatus.PROVISIONED);

            kafkaPatientProducer.sendEvent(newPatient, PatientEventType.PATIENT_CREATED.name());
        } catch (BillingProvisioningException ex) {
            log.warn(
                    "Billing provisioning failed for patient {}: gRPC status={}",
                    newPatient.getId(),
                    ex.getStatusCode());

            newPatient.setBillingAccountId(null);
            newPatient.setBillingStatus(BillingProvisioningStatus.FAILED);

            kafkaPatientProducer.sendEvent(newPatient, PatientEventType.PATIENT_CREATED_BILLING_FAILED.name());
        }

        patientRepository.save(newPatient);

        return PatientMapper.toDTO(newPatient);
    }

    public PatientResDto updatePatient(UUID id, PatientUpdateReqDto patientUpdateReqDto) {

        if (id == null) {
            throw new IllegalArgumentException("Patient ID cannot be null");
        }

        Patient existingPatient = patientRepository
                .findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with ID:" + id));

        if (patientRepository.existsByEmailAndIdNot(patientUpdateReqDto.email(), id)) {
            throw new EmailAlreadyExistsException(
                    "A patient with this email already exists: " + patientUpdateReqDto.email());
        }

        Patient updatedPatient =
                patientRepository.save(PatientMapper.updateModel(existingPatient, patientUpdateReqDto));

        return PatientMapper.toDTO(updatedPatient);
    }

    public PatientResDto updatePatientDob(UUID id, PatientDobUpdateReqDto patientDobUpdateReqDto) {

        if (id == null) {
            throw new IllegalArgumentException("Patient ID cannot be null");
        }

        Patient existingPatient = patientRepository
                .findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with ID:" + id));

        Patient updatedPatient =
                patientRepository.save(PatientMapper.updateDob(existingPatient, patientDobUpdateReqDto));

        return PatientMapper.toDTO(updatedPatient);
    }

    public void deletePatient(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException("Patient ID cannot be null");
        }

        Patient existingPatient = patientRepository
                .findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + id));

        existingPatient.setDeleted(true);
        existingPatient.setDeletedAt(LocalDateTime.now());

        patientRepository.save(existingPatient);
    }
}
