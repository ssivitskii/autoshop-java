package dealership.storage.infrastructure.web.dto.response;

import dealership.storage.core.domain.enums.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CarResponse {
    private String id;
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
}
