package dealership.storage.infrastructure.grpc;

import dealership.common.grpc.CarDto;
import dealership.common.grpc.CarGrpcServiceGrpc;
import dealership.common.grpc.GetAvailableCarsRequest;
import dealership.common.grpc.GetAvailableCarsResponse;
import dealership.common.grpc.GetCarByIdRequest;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@GrpcService
public class CarGrpcServer extends CarGrpcServiceGrpc.CarGrpcServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CarGrpcServer.class);

    private final CarSearchService carSearchService;

    public CarGrpcServer(CarSearchService carSearchService) {
        this.carSearchService = carSearchService;
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
