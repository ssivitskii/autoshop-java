package dealership.storage.core.application.port.out;


import dealership.storage.core.application.dto.CarFilterDto;
import dealership.storage.core.domain.entity.car.Car;

import java.util.List;
import java.util.Optional;

public interface CarRepository {
    Car save(Car car);

    Car findById(String id);

    Optional<Car> findByIdOptional(String id);

    List<Car> findAll();

    List<Car> findAvailable();

    List<Car> findByFilter(CarFilterDto filter);

    void deleteById(String id);

    boolean existsById(String id);

    boolean reserve(String carId, String orderId);

    void release(String carId, String orderId);

    void releaseIfOwned(String carId, String orderId);
}
