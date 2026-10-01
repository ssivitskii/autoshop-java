package dealership.order.core.domain.entity.order.state.stock;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;


public class ReadyForPickupState implements OrderState {
    @Override
    public void advance(StockOrder order) {
        order.setState(new CompletedState());
    }

    @Override
    public void cancel(StockOrder order) {
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
    public StockOrderStatus getStatus() {
        return StockOrderStatus.READY_FOR_PICKUP;
    }

}
