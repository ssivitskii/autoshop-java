package dealership.storage.core.domain.entity.car;

import dealership.storage.core.domain.enums.*;
import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Car {
    private final String id;
    private String brand;
    private String modelName;
    private BodyType bodyType;
    private FuelType fuelType;
    private int enginePowerHp;
    private double engineVolumeLiters;
    private TransmissionType transmissionType;
    private DriveType driveType;
    private Color color;
    private BigDecimal price;
    private boolean available;
    private boolean availableForTestDrive;

    public Car(String brand, String modelName, BodyType bodyType,
               FuelType fuelType, int enginePowerHp, double engineVolume,
               TransmissionType transmissionType, DriveType driveType,
               Color color, BigDecimal price) {
        this(null, brand, modelName, bodyType, fuelType, enginePowerHp,
                engineVolume, transmissionType, driveType, color, price, true, false);
    }

    public Car(String id, String brand, String modelName, BodyType bodyType,
               FuelType fuelType, int enginePowerHp, double engineVolume,
               TransmissionType transmissionType, DriveType driveType,
               Color color, BigDecimal price, boolean available,
               boolean availableForTestDrive) {
        if (brand == null || brand.isBlank()) {
            throw new DomainValidationException("Бренд не может быть пустым");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new DomainValidationException("Модель не может быть пустой");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("Цена должна быть положительной");
        }
        if (enginePowerHp <= 0) {
            throw new DomainValidationException("Мощность двигателя должна быть положительной");
        }

        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.brand = brand;
        this.modelName = modelName;
        this.bodyType = bodyType;
        this.fuelType = fuelType;
        this.enginePowerHp = enginePowerHp;
        this.engineVolumeLiters = engineVolume;
        this.transmissionType = transmissionType;
        this.driveType = driveType;
        this.color = color;
        this.price = price;
        this.available = available;
        this.availableForTestDrive = availableForTestDrive;
    }

    public void markAsUnavailable() {
        this.available = false;
    }

    public void markForTestDrive() {
        this.availableForTestDrive = true;
    }
}
