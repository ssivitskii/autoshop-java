package dealership.order.core.domain.entity.order.state.stock;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;

public class CompletedState implements OrderState {
    @Override
    public void advance(StockOrder order) {
        throw new DomainValidationException("Нельзя продвинуть завершённый заказ");
    }

    @Override
    public void cancel(StockOrder order) {
        throw new DomainValidationException("Нельзя отменить завершённый заказ");
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
    public StockOrderStatus getStatus() {
        return StockOrderStatus.COMPLETED;
    }
}