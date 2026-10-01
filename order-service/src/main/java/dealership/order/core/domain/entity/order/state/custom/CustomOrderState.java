package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;

public interface CustomOrderState {
    void advance(CustomOrder order);

    void cancel(CustomOrder order);

    boolean canCancel();

    boolean canAdvance();

    CustomOrderStatus getStatus();
}
