package com.devtunde.patientservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.devtunde.patientservice.dto.PatientDobUpdateReqDto;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.dto.PatientUpdateReqDto;
import com.devtunde.patientservice.exception.EmailAlreadyExistsException;
import com.devtunde.patientservice.exception.PatientNotFoundException;
import com.devtunde.patientservice.mapper.PatientMapper;
import com.devtunde.patientservice.model.Patient;
import com.devtunde.patientservice.repository.PatientRepository;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<PatientResDto> getPatients() {

        List<Patient> patients = patientRepository.findAll();

        List<PatientResDto> patientResDtos =
                patients.stream().map(PatientMapper::toDTO).toList();

        return patientResDtos;
    }

    public PatientResDto createPatient(PatientReqDto patientReqDto) {

        if (patientRepository.existsByEmail(patientReqDto.email())) {
            throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientReqDto.email());
        }

        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientReqDto));

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
