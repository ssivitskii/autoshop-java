package dealership.order.infrastructure.grpc;

import dealership.common.grpc.CarDto;
import dealership.common.grpc.CarGrpcServiceGrpc;
import dealership.common.grpc.ConfirmCarRequest;
import dealership.common.grpc.GetAvailableCarsRequest;
import dealership.common.grpc.GetAvailableCarsResponse;
import dealership.common.grpc.GetCarByIdRequest;
import dealership.common.grpc.ReleaseCarRequest;
import dealership.common.grpc.ReservationResponse;
import dealership.common.grpc.ReserveCarRequest;
import dealership.common.grpc.QuoteConfigurationRequest;
import dealership.common.grpc.QuoteConfigurationResponse;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.core.domain.exception.IncompatibleComponentException;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class CarGrpcClient implements StockCarReservationGateway,
        CustomConfigurationGateway, TestDriveCarGateway {

    private static final Logger log = LoggerFactory.getLogger(CarGrpcClient.class);

    @GrpcClient("storage-service")
    private CarGrpcServiceGrpc.CarGrpcServiceBlockingStub carStub;

    private static final long RPC_DEADLINE_SECONDS = 5;
    private static final Pattern MONEY = Pattern.compile("[0-9]{1,13}(?:\\.[0-9]{1,2})?");

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
    public ConfigurationQuote quote(String carModelId, Map<String, String> selectedVariants) {
        String canonicalModelId = canonicalRequestUuid(carModelId, "carModelId");
        Map<String, String> canonicalSelection = canonicalSelection(selectedVariants);
        try {
            QuoteConfigurationResponse response = carStub
                    .withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .quoteConfiguration(QuoteConfigurationRequest.newBuilder()
                            .setCarModelId(canonicalModelId)
                            .putAllSelectedVariants(canonicalSelection)
                            .build());
            String responseModelId = canonicalResponseUuid(response.getCarModelId(), "carModelId");
            Map<String, String> responseSelection = canonicalResponseSelection(
                    response.getSelectedVariantsMap());
            if (!canonicalModelId.equals(responseModelId)
                    || !canonicalSelection.equals(responseSelection)) {
                throw new StorageUnavailableException("Сервис склада вернул другую конфигурацию", null);
            }
            BigDecimal totalPrice = parseResponsePrice(response.getTotalPrice());
            return new ConfigurationQuote(responseModelId, responseSelection, totalPrice);
        } catch (StatusRuntimeException exception) {
            throw translateQuoteFailure(exception, canonicalModelId);
        }
    }

    @Override
    public TestDriveCar get(String carId) {
        String canonicalCarId = canonicalRequestUuid(carId, "carId");
        try {
            CarDto response = carStub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getCarById(GetCarByIdRequest.newBuilder().setId(canonicalCarId).build());
            String responseId = canonicalResponseUuid(response.getId(), "carId");
            if (!canonicalCarId.equals(responseId)) {
                throw new StorageUnavailableException("Сервис склада вернул другой автомобиль", null);
            }
            return new TestDriveCar(
                    responseId, response.getAvailable(), response.getAvailableForTestDrive());
        } catch (StatusRuntimeException exception) {
            throw translateTestDriveFailure(exception, canonicalCarId);
        }
    }

    @Override
    public ReservationLease reserve(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        try {
            ReservationResponse response = carStub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .reserveCar(ReserveCarRequest.newBuilder()
                            .setCarId(carId)
                            .setOrderId(orderId)
                            .build());
            requireSuccessful(response);
            if (response.getExpiresAtEpochMillis() <= 0) {
                throw new StorageUnavailableException(
                        "Сервис склада не вернул срок временного резерва", null);
            }
            return new ReservationLease(Instant.ofEpochMilli(response.getExpiresAtEpochMillis()));
        } catch (StatusRuntimeException exception) {
            throw translateReservationFailure(exception, carId);
        }
    }

    @Override
    public void confirm(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        try {
            ReservationResponse response = carStub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .confirmCar(ConfirmCarRequest.newBuilder()
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
            ReservationResponse response = carStub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
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

    private RuntimeException translateQuoteFailure(StatusRuntimeException exception, String carModelId) {
        return switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT -> new DomainValidationException(
                    descriptionOrDefault(exception, "Некорректная конфигурация"));
            case NOT_FOUND -> new EntityNotFoundException(
                    "Модель или компонент конфигурации не найден");
            case FAILED_PRECONDITION -> new IncompatibleComponentException(
                    descriptionOrDefault(exception, "Выбранная конфигурация несовместима"));
            default -> new StorageUnavailableException("Сервис склада временно недоступен", exception);
        };
    }

    private RuntimeException translateTestDriveFailure(StatusRuntimeException exception, String carId) {
        return switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT -> new DomainValidationException(
                    descriptionOrDefault(exception, "Некорректный автомобиль"));
            case NOT_FOUND -> new EntityNotFoundException(
                    "Автомобиль с id '%s' не найден".formatted(carId));
            default -> new StorageUnavailableException("Сервис склада временно недоступен", exception);
        };
    }

    private Map<String, String> canonicalSelection(Map<String, String> selectedVariants) {
        if (selectedVariants == null) {
            throw new DomainValidationException("selectedVariants не может быть null");
        }
        Map<String, String> canonical = new LinkedHashMap<>();
        selectedVariants.forEach((category, variant) -> {
            String canonicalCategory = canonicalRequestUuid(category, "categoryId");
            String canonicalVariant = canonicalRequestUuid(variant, "variantId");
            if (canonical.putIfAbsent(canonicalCategory, canonicalVariant) != null) {
                throw new DomainValidationException("Категория указана более одного раза: " + canonicalCategory);
            }
        });
        return Map.copyOf(canonical);
    }

    private Map<String, String> canonicalResponseSelection(Map<String, String> selectedVariants) {
        Map<String, String> canonical = new LinkedHashMap<>();
        try {
            selectedVariants.forEach((category, variant) -> {
                String canonicalCategory = UUID.fromString(category).toString();
                String canonicalVariant = UUID.fromString(variant).toString();
                if (canonical.putIfAbsent(canonicalCategory, canonicalVariant) != null) {
                    throw new IllegalArgumentException("duplicate category");
                }
            });
            return Map.copyOf(canonical);
        } catch (RuntimeException exception) {
            throw new StorageUnavailableException("Сервис склада вернул некорректную конфигурацию", exception);
        }
    }

    private BigDecimal parseResponsePrice(String value) {
        if (value == null || !MONEY.matcher(value).matches()) {
            throw new StorageUnavailableException("Сервис склада вернул некорректную цену", null);
        }
        BigDecimal price = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        if (price.signum() <= 0 || price.precision() > 15) {
            throw new StorageUnavailableException("Сервис склада вернул некорректную цену", null);
        }
        return price;
    }

    private String canonicalResponseUuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new StorageUnavailableException(
                    "Сервис склада вернул некорректный " + field, exception);
        }
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
        canonicalRequestUuid(value, field);
    }

    private String canonicalRequestUuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new DomainValidationException("%s должен быть UUID".formatted(field));
        }
    }
}
