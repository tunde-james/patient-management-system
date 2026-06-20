package com.devtunde.patientservice.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.devtunde.patientservice.dto.PatientDobUpdateReqDto;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.dto.PatientUpdateReqDto;
import com.devtunde.patientservice.model.Patient;

public class PatientMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static PatientResDto toDTO(Patient patient) {

        return new PatientResDto(
                patient.getId().toString(),
                patient.getName(),
                patient.getAddress(),
                patient.getEmail(),
                patient.getDateOfBirth().toString());
    }

    public static Patient toModel(PatientReqDto patientReqDto) {

        Patient patient = new Patient();
        patient.setName(patientReqDto.name());
        patient.setEmail(patientReqDto.email());
        patient.setAddress(patientReqDto.address());
        patient.setDateOfBirth(LocalDate.parse(patientReqDto.dateOfBirth(), FORMATTER));
        patient.setRegisteredDate(LocalDate.parse(patientReqDto.registeredDate(), FORMATTER));

        return patient;
    }

    public static Patient updateModel(Patient existingPatient, PatientUpdateReqDto patientUpdateReqDto) {

        existingPatient.setName(patientUpdateReqDto.name());
        existingPatient.setEmail(patientUpdateReqDto.email());
        existingPatient.setAddress(patientUpdateReqDto.address());

        return existingPatient;
    }

    public static Patient updateDob(Patient existingPatient, PatientDobUpdateReqDto patientDobUpdateReqDto) {

        existingPatient.setDateOfBirth(LocalDate.parse(patientDobUpdateReqDto.dateOfBirth(), FORMATTER));

        return existingPatient;
    }
}
