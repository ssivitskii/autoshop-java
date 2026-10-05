package dealership.order.core.domain.entity.order;

import dealership.order.core.domain.entity.order.state.stock.CreatedState;
import dealership.order.core.domain.entity.order.state.stock.OrderState;
import dealership.order.core.domain.entity.order.state.stock.PaidState;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class StockOrder {
    @EqualsAndHashCode.Include
    private final String id;

    private final String clientId;
    private final String managerId;
    private final String carId;
    private LocalDateTime createdAt;

    private OrderState state;

    public StockOrder(String clientId, String managerId, String carId) {
        this(null, clientId, managerId, carId);
    }

    public StockOrder(String id, String clientId, String managerId, String carId) {
        if (clientId == null) {
            throw new DomainValidationException("ID клиента не может быть пустым");
        }
        if (managerId == null) {
            throw new DomainValidationException("ID менеджера не может быть пустым");
        }
        if (carId == null) {
            throw new DomainValidationException("ID машины не может быть пустым");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.clientId = clientId;
        this.managerId = managerId;
        this.carId = carId;
        this.createdAt = LocalDateTime.now();
        this.state = new CreatedState();
    }

    public void advanceStatus() {
        state.advance(this);
    }

    public void markPaid() {
        if (getStatus() != StockOrderStatus.AWAITING_PAYMENT) {
            throw new DomainValidationException("Оплату можно зафиксировать только для заказа, ожидающего оплату");
        }
        setState(new PaidState());
    }

    public void cancel() {
        state.cancel(this);
    }

    public boolean canCancel() {

        return state.canCancel();
    }

    public boolean canAdvance() {
        return state.canAdvance();
    }

    public StockOrderStatus getStatus() {
        return state.getStatus();
    }

    public void setState(OrderState newState) {
        if (newState == null) {
            throw new DomainValidationException("Состояние не может быть null");
        }
        this.state = newState;
    }
}
