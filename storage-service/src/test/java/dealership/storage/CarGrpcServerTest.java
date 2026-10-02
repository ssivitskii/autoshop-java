package dealership.storage;

import dealership.common.grpc.ReleaseCarRequest;
import dealership.common.grpc.ConfirmCarRequest;
import dealership.common.grpc.ReservationResponse;
import dealership.common.grpc.ReserveCarRequest;
import dealership.common.grpc.QuoteConfigurationRequest;
import dealership.common.grpc.QuoteConfigurationResponse;
import dealership.storage.core.application.dto.ConfigurationResultDto;
import dealership.storage.core.application.service.CarConfigurationService;
import dealership.storage.core.application.service.CarReservationService;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.domain.entity.car.CarConfiguration;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.DomainValidationException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.core.domain.exception.IncompatibleComponentException;
import dealership.storage.infrastructure.grpc.CarGrpcServer;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CarGrpcServerTest {
    private CarReservationService reservationService;
    private CarConfigurationService configurationService;
    private CarGrpcServer server;
    private StreamObserver<ReservationResponse> observer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reservationService = mock(CarReservationService.class);
        configurationService = mock(CarConfigurationService.class);
        server = new CarGrpcServer(
                mock(CarSearchService.class), reservationService, configurationService);
        observer = mock(StreamObserver.class);
    }

    @Test
    void quoteEchoesCanonicalSnapshotAndDecimalPrice() {
        String modelId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        String variantId = UUID.randomUUID().toString();
        CarConfiguration configuration = new CarConfiguration(modelId);
        configuration.selectVariant(categoryId, variantId);
        when(configurationService.configure(any())).thenReturn(new ConfigurationResultDto(
                configuration, new BigDecimal("3500000.00"), List.of(), "BMW.M340i"));
        @SuppressWarnings("unchecked")
        StreamObserver<QuoteConfigurationResponse> quoteObserver = mock(StreamObserver.class);

        server.quoteConfiguration(QuoteConfigurationRequest.newBuilder()
                .setCarModelId(modelId)
                .putAllSelectedVariants(Map.of(categoryId, variantId))
                .build(), quoteObserver);

        ArgumentCaptor<QuoteConfigurationResponse> response =
                ArgumentCaptor.forClass(QuoteConfigurationResponse.class);
        verify(quoteObserver).onNext(response.capture());
        assertEquals(modelId, response.getValue().getCarModelId());
        assertEquals(Map.of(categoryId, variantId), response.getValue().getSelectedVariantsMap());
        assertEquals("3500000.00", response.getValue().getTotalPrice());
        verify(quoteObserver).onCompleted();
    }

    @Test
    void quoteMapsExpectedDomainFailures() {
        @SuppressWarnings("unchecked")
        StreamObserver<QuoteConfigurationResponse> quoteObserver = mock(StreamObserver.class);
        when(configurationService.configure(any()))
                .thenThrow(new DomainValidationException("bad"))
                .thenThrow(new EntityNotFoundException("missing"))
                .thenThrow(new IncompatibleComponentException("incompatible"));
        QuoteConfigurationRequest request = QuoteConfigurationRequest.newBuilder()
                .setCarModelId(UUID.randomUUID().toString()).build();

        server.quoteConfiguration(request, quoteObserver);
        assertStatus(quoteObserver, Status.Code.INVALID_ARGUMENT);
        reset(quoteObserver);
        server.quoteConfiguration(request, quoteObserver);
        assertStatus(quoteObserver, Status.Code.NOT_FOUND);
        reset(quoteObserver);
        server.quoteConfiguration(request, quoteObserver);
        assertStatus(quoteObserver, Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void reserveMapsConflictToAlreadyExists() {
        String carId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        doThrow(new CarReservationConflictException("held"))
                .when(reservationService).reserve(carId, orderId);

        server.reserveCar(reserveRequest(carId, orderId), observer);

        assertStatus(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void reserveMapsMissingCarToNotFound() {
        String carId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        doThrow(new EntityNotFoundException("missing"))
                .when(reservationService).reserve(carId, orderId);

        server.reserveCar(reserveRequest(carId, orderId), observer);

        assertStatus(Status.Code.NOT_FOUND);
    }

    @Test
    void releaseMapsInvalidUuidToInvalidArgument() {
        doThrow(new IllegalArgumentException("invalid"))
                .when(reservationService).release("bad", "bad");

        server.releaseCar(ReleaseCarRequest.newBuilder()
                .setCarId("bad").setOrderId("bad").build(), observer);

        assertStatus(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void confirmReportsDurablyExpiredReservationAsFailedPrecondition() {
        String carId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        when(reservationService.confirm(carId, orderId))
                .thenReturn(CarReservationService.ConfirmationResult.EXPIRED);

        server.confirmCar(ConfirmCarRequest.newBuilder()
                .setCarId(carId).setOrderId(orderId).build(), observer);

        assertStatus(Status.Code.FAILED_PRECONDITION);
    }

    private ReserveCarRequest reserveRequest(String carId, String orderId) {
        return ReserveCarRequest.newBuilder().setCarId(carId).setOrderId(orderId).build();
    }

    private void assertStatus(Status.Code expected) {
        assertStatus(observer, expected);
    }

    private void assertStatus(StreamObserver<?> target, Status.Code expected) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(target).onError(error.capture());
        assertEquals(expected, Status.fromThrowable(error.getValue()).getCode());
    }
}
