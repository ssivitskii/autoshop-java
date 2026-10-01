package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.infrastructure.persistence.entity.CarJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CarPersistenceMapper {

    public Car toDomain(CarJpaEntity entity) {
        return new Car(entity.getId().toString(), entity.getBrand(), entity.getModelName(), entity.getBodyType(), entity.getFuelType(), entity.getEnginePowerHp(), entity.getEngineVolumeLiters(), entity.getTransmissionType(), entity.getDriveType(), entity.getColor(), entity.getPrice(), entity.isAvailable(), entity.isAvailableForTestDrive());
    }

    public CarJpaEntity toJpa(Car domain) {
        CarJpaEntity entity = new CarJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setBrand(domain.getBrand());
        entity.setModelName(domain.getModelName());
        entity.setBodyType(domain.getBodyType());
        entity.setFuelType(domain.getFuelType());
        entity.setEnginePowerHp(domain.getEnginePowerHp());
        entity.setEngineVolumeLiters(domain.getEngineVolumeLiters());
        entity.setTransmissionType(domain.getTransmissionType());
        entity.setDriveType(domain.getDriveType());
        entity.setColor(domain.getColor());
        entity.setPrice(domain.getPrice());
        entity.setAvailable(domain.isAvailable());
        entity.setAvailableForTestDrive(domain.isAvailableForTestDrive());
        return entity;
    }
}
