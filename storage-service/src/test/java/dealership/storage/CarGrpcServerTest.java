package dealership.storage;

import dealership.common.grpc.ReleaseCarRequest;
import dealership.common.grpc.ReservationResponse;
import dealership.common.grpc.ReserveCarRequest;
import dealership.storage.core.application.service.CarReservationService;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.grpc.CarGrpcServer;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CarGrpcServerTest {
    private CarReservationService reservationService;
    private CarGrpcServer server;
    private StreamObserver<ReservationResponse> observer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reservationService = mock(CarReservationService.class);
        server = new CarGrpcServer(mock(CarSearchService.class), reservationService);
        observer = mock(StreamObserver.class);
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

    private ReserveCarRequest reserveRequest(String carId, String orderId) {
        return ReserveCarRequest.newBuilder().setCarId(carId).setOrderId(orderId).build();
    }

    private void assertStatus(Status.Code expected) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(expected, Status.fromThrowable(error.getValue()).getCode());
    }
}
