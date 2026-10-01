package dealership.storage.core.application.dto;

import dealership.storage.core.domain.enums.*;

import java.math.BigDecimal;

public class CarFilterDto {
    private BigDecimal maxPrice;
    private BigDecimal minPrice;
    private String modelName;
    private String brand;
    private BodyType bodyType;
    private FuelType fuelType;
    private Integer minEnginePowerHp;
    private Integer maxEnginePowerHp;
    private Double minEngineVolume;
    private Double maxEngineVolume;
    private TransmissionType transmissionType;
    private Color color;
    private DriveType driveType;

    public CarFilterDto() {
    }

    public CarFilterDto withModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public CarFilterDto withMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
        return this;
    }

    public CarFilterDto withMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
        return this;
    }

    public CarFilterDto withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public CarFilterDto withBodyType(BodyType bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    public CarFilterDto withFuelType(FuelType fuelType) {
        this.fuelType = fuelType;
        return this;
    }

    public CarFilterDto withMinEnginePowerHp(Integer minEnginePowerHp) {
        this.minEnginePowerHp = minEnginePowerHp;
        return this;
    }

    public CarFilterDto withMaxEnginePowerHp(Integer maxEnginePowerHp) {
        this.maxEnginePowerHp = maxEnginePowerHp;
        return this;
    }

    public CarFilterDto withMinEngineVolume(Double minEngineVolume) {
        this.minEngineVolume = minEngineVolume;
        return this;
    }

    public CarFilterDto withMaxEngineVolume(Double maxEngineVolume) {
        this.maxEngineVolume = maxEngineVolume;
        return this;
    }

    public CarFilterDto withTransmissionType(TransmissionType transmissionType) {
        this.transmissionType = transmissionType;
        return this;
    }

    public CarFilterDto withColor(Color color) {
        this.color = color;
        return this;
    }

    public CarFilterDto withDriveType(DriveType driveType) {
        this.driveType = driveType;
        return this;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public String getModelName() {
        return modelName;
    }

    public String getBrand() {
        return brand;
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public Integer getMinEnginePowerHp() {
        return minEnginePowerHp;
    }

    public Integer getMaxEnginePowerHp() {
        return maxEnginePowerHp;
    }

    public Double getMinEngineVolume() {
        return minEngineVolume;
    }

    public Double getMaxEngineVolume() {
        return maxEngineVolume;
    }

    public TransmissionType getTransmissionType() {
        return transmissionType;
    }

    public Color getColor() {
        return color;
    }

    public DriveType getDriveType() {
        return driveType;
    }
}
