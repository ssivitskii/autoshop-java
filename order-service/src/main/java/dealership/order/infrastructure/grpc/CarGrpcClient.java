package dealership.order.infrastructure.grpc;

import dealership.common.grpc.CarDto;
import dealership.common.grpc.CarGrpcServiceGrpc;
import dealership.common.grpc.GetAvailableCarsRequest;
import dealership.common.grpc.GetAvailableCarsResponse;
import dealership.common.grpc.GetCarByIdRequest;
import dealership.common.grpc.ReleaseCarRequest;
import dealership.common.grpc.ReservationResponse;
import dealership.common.grpc.ReserveCarRequest;
import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.StorageUnavailableException;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CarGrpcClient implements StockCarReservationGateway {

    private static final Logger log = LoggerFactory.getLogger(CarGrpcClient.class);

    @GrpcClient("storage-service")
    private CarGrpcServiceGrpc.CarGrpcServiceBlockingStub carStub;

    private static final long RESERVATION_DEADLINE_SECONDS = 5;

    public List<CarDto> getAvailableCars() {
        log.info("gRPC Client: requesting available cars from StorageService");
        try {
            GetAvailableCarsResponse response = carStub.getAvailableCars(
                    GetAvailableCarsRequest.newBuilder().build());
            log.info("gRPC Client: received {} cars", response.getCarsCount());
            return response.getCarsList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC Client: failed to get available cars, status={}", e.getStatus(), e);
            throw e;
        }
    }

    public CarDto getCarById(String id) {
        log.info("gRPC Client: requesting car id={} from StorageService", id);
        try {
            CarDto car = carStub.getCarById(
                    GetCarByIdRequest.newBuilder().setId(id).build());
            log.info("gRPC Client: received car id={}", id);
            return car;
        } catch (StatusRuntimeException e) {
            log.error("gRPC Client: failed to get car id={}, status={}", id, e.getStatus(), e);
            throw e;
        }
    }

    @Override
    public void reserve(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        try {
            ReservationResponse response = carStub.withDeadlineAfter(RESERVATION_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .reserveCar(ReserveCarRequest.newBuilder()
                            .setCarId(carId)
                            .setOrderId(orderId)
                            .build());
            requireSuccessful(response);
        } catch (StatusRuntimeException exception) {
            throw translateReservationFailure(exception, carId);
        }
    }

    @Override
    public void release(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        try {
            ReservationResponse response = carStub.withDeadlineAfter(RESERVATION_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .releaseCar(ReleaseCarRequest.newBuilder()
                            .setCarId(carId)
                            .setOrderId(orderId)
                            .build());
            requireSuccessful(response);
        } catch (StatusRuntimeException exception) {
            throw translateReservationFailure(exception, carId);
        }
    }

    private RuntimeException translateReservationFailure(StatusRuntimeException exception, String carId) {
        return switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT -> new DomainValidationException(descriptionOrDefault(exception, "Некорректный запрос резервирования"));
            case NOT_FOUND -> new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(carId));
            case ALREADY_EXISTS, FAILED_PRECONDITION -> new CarReservationConflictException(
                    "Автомобиль с id '%s' уже зарезервирован".formatted(carId));
            default -> new StorageUnavailableException("Сервис склада временно недоступен", exception);
        };
    }

    private void requireSuccessful(ReservationResponse response) {
        if (response == null || !response.getSuccessful()) {
            throw new StorageUnavailableException("Сервис склада не подтвердил операцию резервирования", null);
        }
    }

    private String descriptionOrDefault(StatusRuntimeException exception, String fallback) {
        return exception.getStatus().getDescription() == null
                ? fallback
                : exception.getStatus().getDescription();
    }

    private void validateUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new DomainValidationException("%s должен быть UUID".formatted(field));
        }
    }
}
