package dealership.order.core.domain.enums;

public enum StockOrderStatus {
    CREATED,
    APPROVED_BY_MANAGER,
    AWAITING_PAYMENT,
    PAID,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED
}