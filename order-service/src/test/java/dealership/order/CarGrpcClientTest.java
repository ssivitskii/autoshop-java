package dealership.order;

import dealership.common.grpc.*;
import dealership.order.infrastructure.grpc.CarGrpcClient;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.IncompatibleComponentException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarGrpcClient - Unit Tests")
class CarGrpcClientTest {

    @Mock
    private CarGrpcServiceGrpc.CarGrpcServiceBlockingStub carStub;

    @InjectMocks
    private CarGrpcClient carGrpcClient;

    @Test
    @DisplayName("getAvailableCars возвращает список")
    void shouldReturnAvailableCars() {
        CarDto car = CarDto.newBuilder()
                .setId("car-1").setBrand("BMW").setModelName("320i")
                .setBodyType("SEDAN").setFuelType("PETROL")
                .setEnginePowerHp(184).setEngineVolumeLiters(2.0)
                .setTransmissionType("AUTOMATIC").setDriveType("REAR")
                .setColor("BLACK").setPrice("3000000")
                .setAvailable(true).setAvailableForTestDrive(false)
                .build();
        GetAvailableCarsResponse response = GetAvailableCarsResponse.newBuilder()
                .addCars(car).build();

        when(carStub.getAvailableCars(any())).thenReturn(response);

        List<CarDto> result = carGrpcClient.getAvailableCars();
        assertEquals(1, result.size());
        assertEquals("BMW", result.get(0).getBrand());
    }

    @Test
    @DisplayName("getCarById возвращает авто")
    void shouldReturnCarById() {
        CarDto car = CarDto.newBuilder()
                .setId("car-1").setBrand("BMW").setModelName("320i")
                .setBodyType("SEDAN").setFuelType("PETROL")
                .setEnginePowerHp(184).setEngineVolumeLiters(2.0)
                .setTransmissionType("AUTOMATIC").setDriveType("REAR")
                .setColor("BLACK").setPrice("3000000")
                .setAvailable(true).setAvailableForTestDrive(false)
                .build();

        when(carStub.getCarById(any())).thenReturn(car);

        CarDto result = carGrpcClient.getCarById("car-1");
        assertEquals("car-1", result.getId());
    }

    @Test
    @DisplayName("getAvailableCars при недоступности бросает StatusRuntimeException")
    void shouldThrowOnUnavailable() {
        when(carStub.getAvailableCars(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(StatusRuntimeException.class, () -> carGrpcClient.getAvailableCars());
    }

    @Test
    @DisplayName("getCarById NOT_FOUND бросает StatusRuntimeException")
    void shouldThrowOnNotFound() {
        when(carStub.getCarById(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(StatusRuntimeException.class, () -> carGrpcClient.getCarById("nonexistent"));
    }

    @Test
    @DisplayName("getAvailableCars при таймауте бросает DEADLINE_EXCEEDED")
    void shouldThrowOnTimeout() {
        when(carStub.getAvailableCars(any()))
                .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> carGrpcClient.getAvailableCars());
        assertEquals(Status.Code.DEADLINE_EXCEEDED, ex.getStatus().getCode());
    }

    @Test
    @DisplayName("reserve использует deadline и подтверждает резерв")
    void shouldReserveWithDeadline() {
        String carId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.reserveCar(any())).thenReturn(
                ReservationResponse.newBuilder()
                        .setSuccessful(true)
                        .setExpiresAtEpochMillis(Instant.now().plusSeconds(60).toEpochMilli())
                        .build());

        carGrpcClient.reserve(carId, orderId);

        verify(carStub).withDeadlineAfter(5, TimeUnit.SECONDS);
        verify(carStub).reserveCar(argThat(request ->
                request.getCarId().equals(carId) && request.getOrderId().equals(orderId)));
    }

    @Test
    @DisplayName("reserve переводит конфликт склада в доменный 409")
    void shouldTranslateReservationConflict() {
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.reserveCar(any())).thenThrow(Status.ALREADY_EXISTS.asRuntimeException());

        assertThrows(CarReservationConflictException.class,
                () -> carGrpcClient.reserve(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("reserve переводит timeout в недоступность склада")
    void shouldTranslateReservationTimeout() {
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.reserveCar(any())).thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertThrows(StorageUnavailableException.class,
                () -> carGrpcClient.reserve(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("reserve отклоняет невалидный UUID до вызова сети")
    void shouldRejectInvalidReservationUuid() {
        assertThrows(DomainValidationException.class,
                () -> carGrpcClient.reserve("not-a-uuid", UUID.randomUUID().toString()));
        verify(carStub, never()).reserveCar(any());
    }

    @Test
    @DisplayName("confirm использует deadline и подтверждает ledger")
    void shouldConfirmWithDeadline() {
        String carId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.confirmCar(any())).thenReturn(
                ReservationResponse.newBuilder().setSuccessful(true).build());

        carGrpcClient.confirm(carId, orderId);

        verify(carStub).confirmCar(argThat(request ->
                request.getCarId().equals(carId) && request.getOrderId().equals(orderId)));
    }

    @Test
    @DisplayName("quote нормализует UUID и проверяет server snapshot")
    void shouldQuoteCanonicalConfiguration() {
        String modelId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        String variantId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.quoteConfiguration(any())).thenReturn(QuoteConfigurationResponse.newBuilder()
                .setCarModelId(modelId)
                .putSelectedVariants(categoryId, variantId)
                .setTotalPrice("3500000.00")
                .build());

        var quote = carGrpcClient.quote(
                modelId.toUpperCase(), Map.of(categoryId.toUpperCase(), variantId.toUpperCase()));

        assertEquals(modelId, quote.carModelId());
        assertEquals(Map.of(categoryId, variantId), quote.selectedVariants());
        assertEquals(new BigDecimal("3500000.00"), quote.totalPrice());
        verify(carStub).quoteConfiguration(argThat(request ->
                request.getCarModelId().equals(modelId)
                        && request.getSelectedVariantsMap().equals(Map.of(categoryId, variantId))));
    }

    @Test
    @DisplayName("quote отклоняет несовпадающий server snapshot как 503")
    void shouldRejectMismatchedQuoteResponse() {
        String modelId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.quoteConfiguration(any())).thenReturn(QuoteConfigurationResponse.newBuilder()
                .setCarModelId(UUID.randomUUID().toString())
                .setTotalPrice("1.00")
                .build());

        assertThrows(StorageUnavailableException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
    }

    @Test
    @DisplayName("quote отклоняет не-DЕCIMAL(15,2) и неположительные цены как 503")
    void shouldRejectMalformedQuotePrices() {
        String modelId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.quoteConfiguration(any())).thenReturn(
                quote(modelId, "0"),
                quote(modelId, "-1.00"),
                quote(modelId, "1.001"),
                quote(modelId, "10000000000000.00"),
                quote(modelId, "1e9"),
                quote(modelId, ""));

        for (int attempt = 0; attempt < 6; attempt++) {
            assertThrows(StorageUnavailableException.class,
                    () -> carGrpcClient.quote(modelId, Map.of()));
        }
    }

    @Test
    @DisplayName("quote переводит expected gRPC statuses")
    void shouldTranslateQuoteStatuses() {
        String modelId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.quoteConfiguration(any()))
                .thenThrow(Status.INVALID_ARGUMENT.asRuntimeException())
                .thenThrow(Status.NOT_FOUND.asRuntimeException())
                .thenThrow(Status.FAILED_PRECONDITION.asRuntimeException())
                .thenThrow(Status.UNAVAILABLE.asRuntimeException())
                .thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertThrows(DomainValidationException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
        assertThrows(EntityNotFoundException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
        assertThrows(IncompatibleComponentException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
        assertThrows(StorageUnavailableException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
        assertThrows(StorageUnavailableException.class,
                () -> carGrpcClient.quote(modelId, Map.of()));
    }

    @Test
    @DisplayName("test-drive lookup проверяет availability и echoed id")
    void shouldValidateTestDriveCar() {
        String carId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.getCarById(any())).thenReturn(CarDto.newBuilder()
                .setId(carId).setAvailable(true).setAvailableForTestDrive(true).build());

        var car = carGrpcClient.get(carId.toUpperCase());

        assertEquals(carId, car.id());
        verify(carStub).getCarById(argThat(request -> request.getId().equals(carId)));
    }

    @Test
    @DisplayName("test-drive lookup возвращает фактические availability flags")
    void shouldReturnUnavailableTestDriveFlags() {
        String carId = UUID.randomUUID().toString();
        when(carStub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(carStub);
        when(carStub.getCarById(any())).thenReturn(CarDto.newBuilder()
                .setId(carId).setAvailable(false).setAvailableForTestDrive(true).build());

        var car = carGrpcClient.get(carId);

        assertFalse(car.available());
        assertTrue(car.availableForTestDrive());
    }

    @Test
    @DisplayName("test-drive malformed UUID не вызывает сеть")
    void shouldRejectMalformedTestDriveUuidBeforeNetwork() {
        assertThrows(DomainValidationException.class, () -> carGrpcClient.get("not-a-uuid"));
        verify(carStub, never()).getCarById(any());
    }

    private QuoteConfigurationResponse quote(String modelId, String totalPrice) {
        return QuoteConfigurationResponse.newBuilder()
                .setCarModelId(modelId)
                .setTotalPrice(totalPrice)
                .build();
    }
}
