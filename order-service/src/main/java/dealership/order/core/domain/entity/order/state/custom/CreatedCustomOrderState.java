package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;

public class CreatedCustomOrderState implements CustomOrderState {
    @Override
    public void advance(CustomOrder order) {
        order.setState(new ApprovedByWarehouseState());
    }

    @Override
    public void cancel(CustomOrder order) {
        order.setState(new CancelledCustomOrderState());
    }

    @Override
    public boolean canCancel() {
        return true;
    }

    @Override
    public boolean canAdvance() {
        return true;
    }

    @Override
    public CustomOrderStatus getStatus() {
        return CustomOrderStatus.CREATED;
    }
}