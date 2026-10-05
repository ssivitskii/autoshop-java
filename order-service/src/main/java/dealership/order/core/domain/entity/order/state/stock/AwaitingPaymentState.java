package dealership.order.core.domain.entity.order.state.stock;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;

public class AwaitingPaymentState implements OrderState {
    @Override
    public void advance(StockOrder order) {
        throw new DomainValidationException("Статус оплаты изменяется только через demo payment");
    }

    @Override
    public void cancel(StockOrder order) {
        order.setState(new CancelledState());
    }

    @Override
    public boolean canCancel() {
        return true;
    }

    @Override
    public boolean canAdvance() {
        return false;
    }

    @Override
    public StockOrderStatus getStatus() {
        return StockOrderStatus.AWAITING_PAYMENT;
    }
}
