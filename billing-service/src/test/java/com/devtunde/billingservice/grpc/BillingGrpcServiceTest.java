package com.devtunde.billingservice.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import billing.BillingRequest;
import billing.BillingResponse;
import com.devtunde.billingservice.model.AccountStatus;
import com.devtunde.billingservice.model.BillingAccount;
import com.devtunde.billingservice.model.Currency;
import com.devtunde.billingservice.service.BillingService;
import io.grpc.stub.StreamObserver;

class BillingGrpcServiceTest {

    @Test
    @DisplayName("fresh-create maps the provisioned account into a BillingResponse with created=true")
    void createBillingAccount_mapsFreshCreateIntoResponseWithCreatedTrue() {
        BillingService billingService = mock(BillingService.class);

        String patientId = "11111111-1111-1111-1111-111111111111";
        String name = "Ada Okafor";
        String email = "ada@example.com";

        BillingAccount provisioned = new BillingAccount(
                "AAAAAAAAAA",
                patientId,
                name,
                email,
                BigDecimal.ZERO,
                new BigDecimal("0.00"),
                Currency.NGN,
                AccountStatus.ACTIVE,
                LocalDateTime.now());

        when(billingService.provision(patientId, name, email))
                .thenReturn(new BillingService.ProvisioningResult(true, provisioned));

        BillingGrpcService grpcService = new BillingGrpcService(billingService);

        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        List<BillingResponse> captured = new ArrayList<>();
        TestStreamObserver observer = capturingObserver(captured);

        grpcService.createBillingAccount(request, observer);

        assertThat(observer.completed).as("onCompleted must be called").isTrue();
        assertThat(observer.error).as("onError must not be called").isNull();
        assertThat(captured).hasSize(1);
        BillingResponse response = captured.get(0);
        assertThat(response.getAccountId()).isEqualTo("AAAAAAAAAA");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreated()).isTrue();
    }

    @Test
    @DisplayName("idempotent hit maps the existing account into a BillingResponse with created=false")
    void createBillingAccount_mapsIdempotentHitIntoResponseWithCreatedFalse() {
        BillingService billingService = mock(BillingService.class);

        String patientId = "22222222-2222-2222-2222-222222222222";
        String name = "Chidi Eze";
        String email = "chidi@example.com";

        BillingAccount existing = new BillingAccount(
                "BBBBBBBBBB",
                patientId,
                name,
                email,
                BigDecimal.ZERO,
                new BigDecimal("0.00"),
                Currency.NGN,
                AccountStatus.ACTIVE,
                LocalDateTime.now());

        when(billingService.provision(patientId, name, email))
                .thenReturn(new BillingService.ProvisioningResult(false, existing));

        BillingGrpcService grpcService = new BillingGrpcService(billingService);

        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        List<BillingResponse> captured = new ArrayList<>();
        TestStreamObserver observer = capturingObserver(captured);

        grpcService.createBillingAccount(request, observer);

        assertThat(observer.completed).as("onCompleted must be called").isTrue();
        assertThat(observer.error).as("onError must not be called").isNull();
        assertThat(captured).hasSize(1);
        BillingResponse response = captured.get(0);
        assertThat(response.getAccountId()).isEqualTo("BBBBBBBBBB");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreated()).isFalse();
    }

    @Test
    @DisplayName("collision-regenerate maps the retried account into a BillingResponse with created=true")
    void createBillingAccount_mapsCollisionRegenerateIntoResponseWithCreatedTrue() {
        BillingService billingService = mock(BillingService.class);

        String patientId = "33333333-3333-3333-3333-333333333333";
        String name = "Ngozi Bello";
        String email = "ngozi@example.com";

        BillingAccount retried = new BillingAccount(
                "CCCCCCCCCC",
                patientId,
                name,
                email,
                BigDecimal.ZERO,
                new BigDecimal("0.00"),
                Currency.NGN,
                AccountStatus.ACTIVE,
                LocalDateTime.now());

        when(billingService.provision(patientId, name, email))
                .thenReturn(new BillingService.ProvisioningResult(true, retried));

        BillingGrpcService grpcService = new BillingGrpcService(billingService);

        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        List<BillingResponse> captured = new ArrayList<>();
        TestStreamObserver observer = capturingObserver(captured);

        grpcService.createBillingAccount(request, observer);

        assertThat(observer.completed).as("onCompleted must be called").isTrue();
        assertThat(observer.error).as("onError must not be called").isNull();
        assertThat(captured).hasSize(1);
        BillingResponse response = captured.get(0);
        assertThat(response.getAccountId()).isEqualTo("CCCCCCCCCC");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreated()).isTrue();
    }

    private static TestStreamObserver capturingObserver(List<BillingResponse> sink) {
        TestStreamObserver observer = new TestStreamObserver(sink);
        return observer;
    }

    static final class TestStreamObserver implements StreamObserver<BillingResponse> {
        private final List<BillingResponse> sink;
        boolean completed;
        Throwable error;

        TestStreamObserver(List<BillingResponse> sink) {
            this.sink = sink;
        }

        @Override
        public void onNext(BillingResponse value) {
            sink.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
