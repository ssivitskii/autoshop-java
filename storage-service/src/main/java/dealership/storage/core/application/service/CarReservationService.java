package dealership.storage.core.application.service;

import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.domain.enums.CarReservationState;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.CarReservationJpaEntity;
import dealership.storage.infrastructure.persistence.repository.CarReservationJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CarReservationService {
    public enum ConfirmationResult { CONFIRMED, EXPIRED, TERMINAL }

    public record ReservationLease(Instant expiresAt) { }

    private final CarRepository carRepository;
    private final CarReservationJpaRepository reservationRepository;
    private final long holdTtlMillis;

    public CarReservationService(CarRepository carRepository,
                                 CarReservationJpaRepository reservationRepository,
                                 @Value("${reservation.hold-ttl:PT15M}") Duration holdTtl) {
        this.carRepository = carRepository;
        this.reservationRepository = reservationRepository;
        this.holdTtlMillis = holdTtl.toMillis();
        if (holdTtlMillis <= 0) {
            throw new IllegalArgumentException("reservation.hold-ttl must be positive");
        }
    }

    @Transactional
    public ReservationLease reserve(String carIdValue, String orderIdValue) {
        UUID carId = validateUuid(carIdValue, "carId");
        UUID orderId = validateUuid(orderIdValue, "orderId");
        try {
            reservationRepository.insertHeld(orderId, carId, holdTtlMillis);
        } catch (DataIntegrityViolationException exception) {
            throw new CarReservationConflictException(
                    "Заказ с id '%s' уже владеет другим автомобилем".formatted(orderIdValue));
        }

        CarReservationJpaEntity reservation = reservationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Reservation ledger row was not created"));
        requireMatchingCar(reservation, carId);

        if (reservation.getState() == CarReservationState.HELD
                && !reservationRepository.databaseNow().isBefore(reservation.getHoldExpiresAt())) {
            throw new CarReservationConflictException(
                    "Срок резерва заказа с id '%s' истёк".formatted(orderIdValue));
        }

        if (reservation.getState() == CarReservationState.HELD
                || reservation.getState() == CarReservationState.CONFIRMED) {
            if (reservation.getState() == CarReservationState.CONFIRMED
                    || carRepository.reserve(carIdValue, orderIdValue)) {
                return new ReservationLease(reservation.getHoldExpiresAt());
            }
            if (!carRepository.existsById(carIdValue)) {
                throw new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(carIdValue));
            }
            throw new CarReservationConflictException(
                    "Автомобиль с id '%s' уже недоступен".formatted(carIdValue));
        }

        throw new CarReservationConflictException(
                "Резерв заказа с id '%s' уже завершён".formatted(orderIdValue));
    }

    @Transactional
    public ConfirmationResult confirm(String carIdValue, String orderIdValue) {
        UUID carId = validateUuid(carIdValue, "carId");
        UUID orderId = validateUuid(orderIdValue, "orderId");
        CarReservationJpaEntity reservation = reservationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Резерв заказа с id '%s' не найден".formatted(orderIdValue)));
        requireMatchingCar(reservation, carId);

        if (reservation.getState() == CarReservationState.CONFIRMED) {
            return ConfirmationResult.CONFIRMED;
        }
        if (reservation.getState() != CarReservationState.HELD) {
            return ConfirmationResult.TERMINAL;
        }

        Instant databaseNow = reservationRepository.databaseNow();
        if (!databaseNow.isBefore(reservation.getHoldExpiresAt())) {
            carRepository.releaseIfOwned(carIdValue, orderIdValue);
            reservation.setState(CarReservationState.EXPIRED);
            reservationRepository.save(reservation);
            return ConfirmationResult.EXPIRED;
        }

        reservation.setState(CarReservationState.CONFIRMED);
        reservationRepository.save(reservation);
        return ConfirmationResult.CONFIRMED;
    }

    @Transactional
    public void release(String carIdValue, String orderIdValue) {
        UUID carId = validateUuid(carIdValue, "carId");
        UUID orderId = validateUuid(orderIdValue, "orderId");
        reservationRepository.insertReleaseTombstone(orderId, carId);
        CarReservationJpaEntity reservation = reservationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Release tombstone was not created"));
        requireMatchingCar(reservation, carId);

        carRepository.releaseIfOwned(carIdValue, orderIdValue);
        if (reservation.getState() == CarReservationState.HELD
                || reservation.getState() == CarReservationState.CONFIRMED) {
            reservation.setState(CarReservationState.RELEASED);
            reservationRepository.save(reservation);
        }
    }

    public List<UUID> findExpiredCandidates(int limit) {
        return reservationRepository.findExpiredCandidateIds(limit);
    }

    @Transactional
    public boolean expireIfDue(UUID orderId) {
        CarReservationJpaEntity reservation = reservationRepository.findByOrderIdForUpdate(orderId)
                .orElse(null);
        if (reservation == null || reservation.getState() != CarReservationState.HELD) {
            return false;
        }
        Instant databaseNow = reservationRepository.databaseNow();
        if (databaseNow.isBefore(reservation.getHoldExpiresAt())) {
            return false;
        }
        carRepository.releaseIfOwned(reservation.getCarId().toString(), orderId.toString());
        reservation.setState(CarReservationState.EXPIRED);
        reservationRepository.save(reservation);
        return true;
    }

    private UUID validateUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("%s должен быть UUID".formatted(field));
        }
    }

    private void requireMatchingCar(CarReservationJpaEntity reservation, UUID carId) {
        if (!reservation.getCarId().equals(carId)) {
            throw new CarReservationConflictException(
                    "Заказ с id '%s' связан с другим автомобилем".formatted(reservation.getOrderId()));
        }
    }
}
