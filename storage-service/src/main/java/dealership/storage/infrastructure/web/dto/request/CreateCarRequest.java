package dealership.storage.infrastructure.web.dto.request;

import dealership.storage.core.domain.enums.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCarRequest {
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
}