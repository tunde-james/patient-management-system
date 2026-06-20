package com.devtunde.patientservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.patientservice.model.Patient;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
