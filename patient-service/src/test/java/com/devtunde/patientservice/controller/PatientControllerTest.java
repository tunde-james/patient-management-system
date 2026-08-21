package com.devtunde.patientservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.patientservice.dto.PatientResDto;
import com.devtunde.patientservice.exception.EmailAlreadyExistsException;
import com.devtunde.patientservice.exception.PatientNotFoundException;
import com.devtunde.patientservice.service.PatientService;

@WebMvcTest(PatientController.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private static PatientResDto samplePatient(String id) {
        return new PatientResDto(
                id,
                "Slice Test",
                "slice@example.com",
                "12 Slice Test Road",
                LocalDate.of(1990, 1, 1),
                "acct-123",
                "PROVISIONED");
    }

    @Test
    @DisplayName("GET /api/v1/patients -> 200 + camelCase wire shape")
    void getPatients_returns200AndWireShape() throws Exception {

        when(patientService.getPatients())
                .thenReturn(List.of(samplePatient(UUID.randomUUID().toString())));

        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").value("Slice Test"))
                .andExpect(jsonPath("$[0].dateOfBirth").value("1990-01-01"))
                .andExpect(jsonPath("$[0].billingAccountId").value("acct-123"))
                .andExpect(jsonPath("$[0].billingStatus").value("PROVISIONED"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} -> 200 with the patient")
    void getPatient_returns200() throws Exception {

        UUID id = UUID.randomUUID();

        when(patientService.getPatient(id)).thenReturn(samplePatient(id.toString()));

        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Slice Test"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} on missing/deleted id -> 404 via advice")
    void getPatient_missingOrDeleted_returns404() throws Exception {

        UUID id = UUID.randomUUID();

        when(patientService.getPatient(id)).thenThrow(new PatientNotFoundException("Patient not found with ID: " + id));

        mockMvc.perform(get("/api/v1/patients/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/patients -> 201 + Location header + body; DTO mapped from JSON")
    void createPatient_returns201WithLocation() throws Exception {

        String id = UUID.randomUUID().toString();

        when(patientService.createPatient(any())).thenReturn(samplePatient(id));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name":"Slice Test",
                                "email":"slice@example.com",
                                "address":"12 Slice Test Road",
                                "dateOfBirth":"1990-01-01",
                                "registeredDate":"2026-01-01"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/" + id))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("slice@example.com"));

        verify(patientService).createPatient(any());
    }

    @Test
    @DisplayName("POST /api/v1/patients with blank name -> 400 (bean validation)")
    void createPatient_blankName_returns400() throws Exception {

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name":"",
                                "email":"slice@example.com",
                                "address":"12 Slice Test Road",
                                "dateOfBirth":"1990-01-01",
                                "registeredDate":"2026-01-01"
                            }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/patients duplicate email -> 409 via GlobalExceptionHandler")
    void createPatient_duplicateEmail_returns409() throws Exception {

        when(patientService.createPatient(any())).thenThrow(new EmailAlreadyExistsException("dup@example.com"));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name":"Slice Test",
                                "email":"dup@example.com",
                                "address":"12 Slice Test Road",
                                "dateOfBirth":"1990-01-01",
                                "registeredDate":"2026-01-01"
                            }
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/v1/patients/{id} -> 200; id plumbed to service path")
    void updatePatient_returns200AndPlumbsId() throws Exception {

        UUID id = UUID.randomUUID();

        when(patientService.updatePatient(eq(id), any())).thenReturn(samplePatient(id.toString()));

        mockMvc.perform(put("/api/v1/patients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name":"Updated",
                                "email":"updated@example.com",
                                "address":"99 Updated Avenue"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(patientService).updatePatient(eq(id), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/patients/{id}/date-of-birth -> 200")
    void updatePatientDob_returns200() throws Exception {

        UUID id = UUID.randomUUID();

        when(patientService.updatePatientDob(eq(id), any())).thenReturn(samplePatient(id.toString()));

        mockMvc.perform(patch("/api/v1/patients/" + id + "/date-of-birth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "dateOfBirth":"1985-05-05"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("1990-01-01"));
    }

    @Test
    @DisplayName("DELETE /api/v1/patients/{id} -> 204; service called with id")
    void deletePatient_returns204() throws Exception {

        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/patients/" + id)).andExpect(status().isNoContent());

        verify(patientService).deletePatient(id);
    }

    @Test
    @DisplayName("POST with malformed dateOfBirth format -> 400 invalid-format")
    void createPatient_badDateFormat_returns400() throws Exception {

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Okafor","email":"bad-date@example.com",
                                 "address":"12 Marina Road, Lagos","dateOfBirth":"01/06/1990",
                                 "registeredDate":"2024-01-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST when billing failed -> 201 with wire shape FAILED + null account id")
    void createPatient_billingFailedWireShape_rendersOverHttp() throws Exception {

        // The service-level failure matrix lives in PatientServiceCreatePatientTest;
        // this proves only that a FAILED result renders correctly on the wire.
        when(patientService.createPatient(any()))
                .thenReturn(new PatientResDto(
                        UUID.randomUUID().toString(),
                        "Ada Okafor",
                        "failed-billing@example.com",
                        "12 Marina Road, Lagos",
                        LocalDate.of(1990, 6, 1),
                        null,
                        "FAILED"));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Okafor","email":"failed-billing@example.com",
                                 "address":"12 Marina Road, Lagos","dateOfBirth":"1990-06-01",
                                 "registeredDate":"2024-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billingStatus").value("FAILED"))
                .andExpect(jsonPath("$.billingAccountId").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} with a non-UUID segment -> 400 (type mismatch)")
    void getPatient_nonUuidSegment_returns400() throws Exception {

        mockMvc.perform(get("/api/v1/patients/no-such-endpoint")).andExpect(status().isBadRequest());
    }
}
