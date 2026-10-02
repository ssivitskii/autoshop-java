package dealership.order;

import dealership.common.grpc.*;
import dealership.order.infrastructure.grpc.CarGrpcClient;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.StorageUnavailableException;
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
                ReservationResponse.newBuilder().setSuccessful(true).build());

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
}
