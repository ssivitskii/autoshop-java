package dealership.order.core.domain.enums;

public enum StockReservationWorkflowState {
    HELD,
    LEGACY_UNVERIFIED,
    CONFIRM_PENDING,
    CONFIRMED,
    RELEASE_PENDING,
    RELEASED,
    EXPIRED
}
