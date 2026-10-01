package dealership.storage.infrastructure.web.mapper;

import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.infrastructure.web.dto.request.CreateCarRequest;
import dealership.storage.infrastructure.web.dto.response.CarResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CarWebMapper {
    CarResponse toResponse(Car car);

    List<CarResponse> toResponseList(List<Car> cars);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "available", constant = "true")
    @Mapping(target = "availableForTestDrive", constant = "false")
    default Car toDomain(CreateCarRequest request) {
        return new Car(
                request.getBrand(),
                request.getModelName(),
                request.getBodyType(),
                request.getFuelType(),
                request.getEnginePowerHp(),
                request.getEngineVolumeLiters(),
                request.getTransmissionType(),
                request.getDriveType(),
                request.getColor(),
                request.getPrice()
        );
    }

    ;
}
