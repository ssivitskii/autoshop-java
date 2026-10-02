package dealership.order.core.domain.exception;

public class CarReservationConflictException extends RuntimeException {
    public CarReservationConflictException(String message) {
        super(message);
    }
}
