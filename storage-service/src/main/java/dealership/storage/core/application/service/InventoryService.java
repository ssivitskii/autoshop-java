package dealership.storage.core.application.service;

import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.application.port.out.SparePartRepository;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.entity.part.SparePart;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final CarRepository carRepository;
    private final SparePartRepository sparePartRepository;

    public InventoryService(CarRepository carRepository, SparePartRepository sparePartRepository) {
        this.carRepository = carRepository;
        this.sparePartRepository = sparePartRepository;
    }

    public Car addCar(Car car) {
        return carRepository.save(car);
    }

    public Car updateCar(String carId, Car car) {
        carRepository.findById(carId);
        return carRepository.save(car);
    }

    public void markForTestDrive(String carId) {
        Car car = carRepository.findById(carId);
        car.setAvailableForTestDrive(true);
        carRepository.save(car);
    }

    public void removeFromTestDrive(String carId) {
        Car car = carRepository.findById(carId);
        car.setAvailableForTestDrive(false);
        carRepository.save(car);
    }

    public SparePart addPart(SparePart part) {
        return sparePartRepository.save(part);
    }

    public SparePart updatePart(String partId, SparePart part) {
        sparePartRepository.findById(partId);
        return sparePartRepository.save(part);
    }

    public List<SparePart> getAllParts() {
        return sparePartRepository.findAll();
    }

    public void deletePart(String partId) {
        sparePartRepository.findById(partId);
        sparePartRepository.deleteById(partId);
    }
}
