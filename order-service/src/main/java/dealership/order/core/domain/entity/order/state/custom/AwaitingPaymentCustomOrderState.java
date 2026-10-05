package dealership.order.core.domain.entity.order.state.custom;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;

public class AwaitingPaymentCustomOrderState implements CustomOrderState {
    @Override
    public void advance(CustomOrder order) {
        throw new DomainValidationException("Статус оплаты изменяется только через demo payment");
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
        return false;
    }

    @Override
    public CustomOrderStatus getStatus() {
        return CustomOrderStatus.AWAITING_PAYMENT;
    }

}
