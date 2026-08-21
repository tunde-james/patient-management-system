package com.devtunde.patientservice.controller;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.patientservice.dto.PatientDobUpdateReqDto;
import com.devtunde.patientservice.dto.PatientReqDto;
import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.dto.PatientUpdateReqDto;
import com.devtunde.patientservice.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "patient", description = "API for managing Patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Get Patients")
    public ResponseEntity<List<PatientResDto>> getPatients() {

        List<PatientResDto> patients = patientService.getPatients();

        return ResponseEntity.ok().body(patients);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Patient by ID")
    public ResponseEntity<PatientResDto> getPatient(@PathVariable UUID id) {

        return ResponseEntity.ok(patientService.getPatient(id));
    }

    @PostMapping
    @Operation(summary = "Create Patient")
    public ResponseEntity<PatientResDto> createPatient(@Valid @RequestBody PatientReqDto patientReqDto) {

        PatientResDto patientResDto = patientService.createPatient(patientReqDto);

        URI location = Objects.requireNonNull(URI.create("/api/v1/patients/" + patientResDto.id()), "");

        return ResponseEntity.created(location).body(patientResDto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Patient")
    public ResponseEntity<PatientResDto> updatePatient(
            @PathVariable UUID id, @Valid @RequestBody PatientUpdateReqDto patientUpdateReqDto) {

        return ResponseEntity.ok(patientService.updatePatient(id, patientUpdateReqDto));
    }

    @PatchMapping("/{id}/date-of-birth")
    @Operation(summary = "Update Patient's Date of Birth")
    public ResponseEntity<PatientResDto> updatePatientDob(
            @PathVariable UUID id, @Valid @RequestBody PatientDobUpdateReqDto patientDobUpdateReqDto) {

        return ResponseEntity.ok(patientService.updatePatientDob(id, patientDobUpdateReqDto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Patient")
    public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {

        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }
}
