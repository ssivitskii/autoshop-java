package dealership.order.core.domain.entity.order.state.stock;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;

public class ApprovedByManagerState implements OrderState {
    @Override
    public void advance(StockOrder order) {
        order.setState(new AwaitingPaymentState());
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
        return true;
    }

    @Override
    public StockOrderStatus getStatus() {
        return StockOrderStatus.APPROVED_BY_MANAGER;
    }
}