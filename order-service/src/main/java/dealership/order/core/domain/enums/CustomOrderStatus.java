package dealership.order.core.domain.enums;

public enum CustomOrderStatus {
    CREATED,
    APPROVED_BY_WAREHOUSE,
    AWAITING_PAYMENT,
    PAID,
    AWAITING_DELIVERY,
    READY_FOR_PICKUP,
    CANCELLED,
    COMPLETED,
}
