package dealership.order.core.domain.entity.order.state.stock;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;

public interface OrderState {
    void advance(StockOrder order);

    void cancel(StockOrder order);

    boolean canCancel();

    boolean canAdvance();

    StockOrderStatus getStatus();
}