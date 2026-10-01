package dealership.storage.core.application.service;

import dealership.storage.core.application.dto.CarFilterDto;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.domain.entity.car.Car;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CarSearchService {
    private final CarRepository carRepository;

    public CarSearchService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public Car getById(String carId) {
        return carRepository.findById(carId);
    }

    public List<Car> findAvailableCars() {
        return carRepository.findAvailable();
    }

    public List<Car> searchByFilter(CarFilterDto filterDto) {
        CarFilterDto filter = convertToCarFilter(filterDto);
        return carRepository.findByFilter(filter);
    }

    public List<Car> findCarsForTestDrive() {
        return carRepository.findAll().stream().filter(Car::isAvailableForTestDrive).collect(Collectors.toList());
    }

    private CarFilterDto convertToCarFilter(CarFilterDto dto) {
        return new CarFilterDto()
                .withBrand(dto.getBrand())
                .withModelName(dto.getModelName())
                .withBodyType(dto.getBodyType())
                .withFuelType(dto.getFuelType())
                .withTransmissionType(dto.getTransmissionType())
                .withDriveType(dto.getDriveType())
                .withColor(dto.getColor())
                .withMinPrice(dto.getMinPrice())
                .withMaxPrice(dto.getMaxPrice())
                .withMinEnginePowerHp(dto.getMinEnginePowerHp())
                .withMaxEnginePowerHp(dto.getMaxEnginePowerHp())
                .withMinEngineVolume(dto.getMinEngineVolume())
                .withMaxEngineVolume(dto.getMaxEngineVolume());
    }
}
