package dealership.storage.core.application.port.out;

import dealership.storage.core.domain.entity.car.CarModel;

import java.util.List;

public interface CarModelRepository {
    CarModel save(CarModel model);

    CarModel findById(String id);

    List<CarModel> findAll();

    void deleteById(String id);
}