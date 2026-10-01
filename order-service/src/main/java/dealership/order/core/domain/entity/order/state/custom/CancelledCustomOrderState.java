package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;

public class CancelledCustomOrderState implements CustomOrderState {
    @Override
    public void advance(CustomOrder order) {
        throw new DomainValidationException("Нельзя продвинуть отмененный заказ");
    }

    @Override
    public void cancel(CustomOrder order) {
        throw new DomainValidationException("Нельзя отменить отмененный заказ");
    }

    @Override
    public boolean canCancel() {
        return false;
    }

    @Override
    public boolean canAdvance() {
        return false;
    }

    @Override
    public CustomOrderStatus getStatus() {
        return CustomOrderStatus.CANCELLED;
    }

}