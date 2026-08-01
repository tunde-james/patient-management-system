package com.devtunde.patientservice.repository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.patientservice.model.Patient;

@Component
public class PatientMutationGateway {

    private final PatientRepository patientRepository;

    public PatientMutationGateway(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Patient patient) {
        patientRepository.save(patient);
    }
}
