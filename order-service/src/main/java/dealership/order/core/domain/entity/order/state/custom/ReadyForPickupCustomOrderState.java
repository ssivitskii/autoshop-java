package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;


public class ReadyForPickupCustomOrderState implements CustomOrderState {
    @Override
    public void advance(CustomOrder order) {
        order.setState(new CompletedCustomOrderState());
    }

    @Override
    public void cancel(CustomOrder order) {
        throw new DomainValidationException("Нельзя отменить заказ готовый к выдаче");
    }

    @Override
    public boolean canCancel() {
        return false;
    }

    @Override
    public boolean canAdvance() {
        return true;
    }

    @Override
    public CustomOrderStatus getStatus() {
        return CustomOrderStatus.READY_FOR_PICKUP;
    }

}
