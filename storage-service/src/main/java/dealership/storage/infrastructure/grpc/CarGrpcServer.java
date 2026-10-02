package dealership.storage.infrastructure.grpc;

import dealership.common.grpc.CarDto;
import dealership.common.grpc.CarGrpcServiceGrpc;
import dealership.common.grpc.GetAvailableCarsRequest;
import dealership.common.grpc.GetAvailableCarsResponse;
import dealership.common.grpc.GetCarByIdRequest;
import dealership.common.grpc.ConfirmCarRequest;
import dealership.common.grpc.ReleaseCarRequest;
import dealership.common.grpc.ReservationResponse;
import dealership.common.grpc.ReserveCarRequest;
import dealership.common.grpc.QuoteConfigurationRequest;
import dealership.common.grpc.QuoteConfigurationResponse;
import dealership.storage.core.application.dto.CarConfigurationRequestDto;
import dealership.storage.core.application.dto.ConfigurationResultDto;
import dealership.storage.core.application.service.CarConfigurationService;
import dealership.storage.core.application.service.CarReservationService;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.core.domain.exception.DomainValidationException;
import dealership.storage.core.domain.exception.IncompatibleComponentException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

@GrpcService
public class CarGrpcServer extends CarGrpcServiceGrpc.CarGrpcServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CarGrpcServer.class);

    private final CarSearchService carSearchService;
    private final CarReservationService carReservationService;
    private final CarConfigurationService carConfigurationService;

    public CarGrpcServer(CarSearchService carSearchService,
                         CarReservationService carReservationService,
                         CarConfigurationService carConfigurationService) {
        this.carSearchService = carSearchService;
        this.carReservationService = carReservationService;
        this.carConfigurationService = carConfigurationService;
    }

    @Override
    public void quoteConfiguration(QuoteConfigurationRequest request,
                                   StreamObserver<QuoteConfigurationResponse> responseObserver) {
        try {
            CarConfigurationRequestDto configurationRequest =
                    new CarConfigurationRequestDto(request.getCarModelId());
            configurationRequest.setSelectedVariants(request.getSelectedVariantsMap());
            ConfigurationResultDto quote = carConfigurationService.configure(configurationRequest);
            responseObserver.onNext(QuoteConfigurationResponse.newBuilder()
                    .setCarModelId(quote.getConfiguration().getCarModelId())
                    .putAllSelectedVariants(quote.getConfiguration().getSelectedVariants())
                    .setTotalPrice(quote.getTotalPrice().toPlainString())
                    .build());
            responseObserver.onCompleted();
        } catch (DomainValidationException | IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage()).asRuntimeException());
        } catch (EntityNotFoundException exception) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(exception.getMessage()).asRuntimeException());
        } catch (IncompatibleComponentException exception) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription(exception.getMessage()).asRuntimeException());
        } catch (Exception exception) {
            log.error("gRPC: Failed to quote configuration for model id={}",
                    request.getCarModelId(), exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to quote configuration").asRuntimeException());
        }
    }

    @Override
    public void reserveCar(ReserveCarRequest request,
                           StreamObserver<ReservationResponse> responseObserver) {
        try {
            CarReservationService.ReservationLease lease =
                    carReservationService.reserve(request.getCarId(), request.getOrderId());
            ReservationResponse.Builder response = ReservationResponse.newBuilder().setSuccessful(true);
            if (lease.expiresAt() != null) {
                response.setExpiresAtEpochMillis(lease.expiresAt().toEpochMilli());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (EntityNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (CarReservationConflictException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        } catch (DataIntegrityViolationException e) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription("Order already owns another car reservation")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Failed to reserve car id={} for order id={}",
                    request.getCarId(), request.getOrderId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to reserve car").asRuntimeException());
        }
    }

    @Override
    public void confirmCar(ConfirmCarRequest request,
                           StreamObserver<ReservationResponse> responseObserver) {
        try {
            CarReservationService.ConfirmationResult result =
                    carReservationService.confirm(request.getCarId(), request.getOrderId());
            if (result != CarReservationService.ConfirmationResult.CONFIRMED) {
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("Reservation is no longer confirmable")
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ReservationResponse.newBuilder().setSuccessful(true).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (EntityNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (CarReservationConflictException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Failed to confirm car id={} for order id={}",
                    request.getCarId(), request.getOrderId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to confirm car").asRuntimeException());
        }
    }

    @Override
    public void releaseCar(ReleaseCarRequest request,
                           StreamObserver<ReservationResponse> responseObserver) {
        try {
            carReservationService.release(request.getCarId(), request.getOrderId());
            responseObserver.onNext(ReservationResponse.newBuilder().setSuccessful(true).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (EntityNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (CarReservationConflictException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Failed to release car id={} for order id={}",
                    request.getCarId(), request.getOrderId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to release car").asRuntimeException());
        }
    }

    @Override
    public void getAvailableCars(GetAvailableCarsRequest request,
                                  StreamObserver<GetAvailableCarsResponse> responseObserver) {
        log.info("gRPC: GetAvailableCars request received");
        try {
            List<Car> cars = carSearchService.findAvailableCars();
            GetAvailableCarsResponse response = GetAvailableCarsResponse.newBuilder()
                    .addAllCars(cars.stream().map(this::toDto).toList())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC: Returned {} available cars", cars.size());
        } catch (Exception e) {
            log.error("gRPC: Error getting available cars", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get available cars: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getCarById(GetCarByIdRequest request,
                            StreamObserver<CarDto> responseObserver) {
        log.info("gRPC: GetCarById request received, id={}", request.getId());
        try {
            Car car = carSearchService.getById(request.getId());
            responseObserver.onNext(toDto(car));
            responseObserver.onCompleted();
            log.info("gRPC: Returned car id={}", request.getId());
        } catch (EntityNotFoundException | IllegalArgumentException e) {
            log.warn("gRPC: Car not found, id={}", request.getId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Car not found: " + request.getId())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("gRPC: Error getting car by id", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get car: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private CarDto toDto(Car car) {
        return CarDto.newBuilder()
                .setId(car.getId())
                .setBrand(car.getBrand())
                .setModelName(car.getModelName())
                .setBodyType(car.getBodyType().name())
                .setFuelType(car.getFuelType().name())
                .setEnginePowerHp(car.getEnginePowerHp())
                .setEngineVolumeLiters(car.getEngineVolumeLiters())
                .setTransmissionType(car.getTransmissionType().name())
                .setDriveType(car.getDriveType().name())
                .setColor(car.getColor().name())
                .setPrice(car.getPrice().toPlainString())
                .setAvailable(car.isAvailable())
                .setAvailableForTestDrive(car.isAvailableForTestDrive())
                .build();
    }
}
