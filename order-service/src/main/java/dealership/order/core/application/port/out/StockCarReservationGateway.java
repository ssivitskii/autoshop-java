package dealership.order.core.application.port.out;

public interface StockCarReservationGateway {
    record ReservationLease(java.time.Instant expiresAt) { }

    ReservationLease reserve(String carId, String orderId);

    void confirm(String carId, String orderId);

    void release(String carId, String orderId);
}
