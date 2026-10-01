package dealership.order.infrastructure.web.dto.response;

import dealership.common.grpc.CarDto;
import lombok.Data;

@Data
public class CarGrpcResponse {
    private String id;
    private String brand;
    private String modelName;
    private String bodyType;
    private String fuelType;
    private int enginePowerHp;
    private double engineVolumeLiters;
    private String transmissionType;
    private String driveType;
    private String color;
    private String price;
    private boolean available;
    private boolean availableForTestDrive;

    public static CarGrpcResponse fromGrpc(CarDto dto) {
        CarGrpcResponse r = new CarGrpcResponse();
        r.setId(dto.getId());
        r.setBrand(dto.getBrand());
        r.setModelName(dto.getModelName());
        r.setBodyType(dto.getBodyType());
        r.setFuelType(dto.getFuelType());
        r.setEnginePowerHp(dto.getEnginePowerHp());
        r.setEngineVolumeLiters(dto.getEngineVolumeLiters());
        r.setTransmissionType(dto.getTransmissionType());
        r.setDriveType(dto.getDriveType());
        r.setColor(dto.getColor());
        r.setPrice(dto.getPrice());
        r.setAvailable(dto.getAvailable());
        r.setAvailableForTestDrive(dto.getAvailableForTestDrive());
        return r;
    }
}
