package com.devtunde.patientservice.mapper;

import com.devtunde.patientservice.dto.PatientDobUpdateReqDto;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.dto.PatientUpdateReqDto;
import com.devtunde.patientservice.model.Patient;

public class PatientMapper {

    public static PatientResDto toDTO(Patient patient) {

        return new PatientResDto(
                patient.getId().toString(),
                patient.getName(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getDateOfBirth(),
                patient.getBillingAccountId(),
                patient.getBillingStatus() == null
                        ? null
                        : patient.getBillingStatus().name());
    }

    public static Patient toModel(PatientReqDto patientReqDto) {

        Patient patient = new Patient();
        patient.setName(patientReqDto.name());
        patient.setEmail(patientReqDto.email());
        patient.setAddress(patientReqDto.address());
        patient.setDateOfBirth(patientReqDto.dateOfBirth());
        patient.setRegisteredDate(patientReqDto.registeredDate());

        return patient;
    }

    public static Patient updateModel(Patient existingPatient, PatientUpdateReqDto patientUpdateReqDto) {

        existingPatient.setName(patientUpdateReqDto.name());
        existingPatient.setEmail(patientUpdateReqDto.email());
        existingPatient.setAddress(patientUpdateReqDto.address());

        return existingPatient;
    }

    public static Patient updateDob(Patient existingPatient, PatientDobUpdateReqDto patientDobUpdateReqDto) {

        existingPatient.setDateOfBirth(patientDobUpdateReqDto.dateOfBirth());

        return existingPatient;
    }
}
