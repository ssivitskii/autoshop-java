package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;

public class ApprovedByWarehouseState implements CustomOrderState {
    @Override
    public void advance(CustomOrder order) {
        order.setState(new AwaitingPaymentCustomOrderState());
    }

    @Override
    public void cancel(CustomOrder order) {
        order.setState(new CancelledCustomOrderState());
    }

    @Override
    public CustomOrderStatus getStatus() {
        return CustomOrderStatus.APPROVED_BY_WAREHOUSE;
    }

    @Override
    public boolean canCancel() {
        return true;
    }

    @Override
    public boolean canAdvance() {
        return true;
    }
}
