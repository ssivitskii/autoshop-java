package dealership.order.core.application.port.out;

public interface StockCarReservationGateway {
    void reserve(String carId, String orderId);

    void release(String carId, String orderId);
}
