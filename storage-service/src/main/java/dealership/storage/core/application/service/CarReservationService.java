package dealership.storage.core.application.service;

import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

@Service
public class CarReservationService {
    private final CarRepository carRepository;

    public CarReservationService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public void reserve(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        try {
            if (carRepository.reserve(carId, orderId)) {
                return;
            }
        } catch (DataIntegrityViolationException exception) {
            throw new CarReservationConflictException(
                    "Заказ с id '%s' уже владеет другим автомобилем".formatted(orderId));
        }
        if (!carRepository.existsById(carId)) {
            throw new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(carId));
        }
        throw new CarReservationConflictException("Автомобиль с id '%s' уже недоступен".formatted(carId));
    }

    public void release(String carId, String orderId) {
        validateUuid(carId, "carId");
        validateUuid(orderId, "orderId");
        carRepository.release(carId, orderId);
    }

    private void validateUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("%s должен быть UUID".formatted(field));
        }
    }
}
