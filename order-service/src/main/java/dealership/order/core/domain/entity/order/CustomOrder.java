package dealership.order.core.domain.entity.order;

import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.state.custom.CreatedCustomOrderState;
import dealership.order.core.domain.entity.order.state.custom.CustomOrderState;
import dealership.order.core.domain.entity.order.state.custom.PaidCustomOrderState;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CustomOrder {
    @EqualsAndHashCode.Include
    private final String id;

    private final String managerId;
    private final String clientId;
    private final String carModelId;
    private final CarConfiguration configuration;
    private final BigDecimal totalPrice;
    private LocalDateTime createdAt;

    private CustomOrderState state;

    public CustomOrder(
            String clientId,
            String managerId,
            String carModelId,
            CarConfiguration configuration,
            BigDecimal totalPrice) {
        this(null, managerId, clientId, carModelId, configuration, totalPrice);
    }

    public CustomOrder(String id, String managerId, String clientId, String carModelId, CarConfiguration configuration, BigDecimal totalPrice) {
        if (clientId == null || clientId.isBlank()) {
            throw new DomainValidationException("ID клиента не может быть пустым");
        }
        if (managerId == null || managerId.isBlank()) {
            throw new DomainValidationException("ID менеджера не может быть пустым");
        }
        if (carModelId == null || carModelId.isBlank()) {
            throw new DomainValidationException("ID модели автомобиля не может быть пустым");
        }
        if (configuration == null) {
            throw new DomainValidationException("Конфигурация не может быть null");
        }
        if (!carModelId.equals(configuration.getCarModelId())) {
            throw new DomainValidationException("Модель конфигурации не совпадает с моделью заказа");
        }
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("Цена должна быть положительной");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.managerId = managerId;
        this.clientId = clientId;
        this.carModelId = carModelId;
        this.configuration = configuration;
        this.totalPrice = totalPrice;
        this.state = new CreatedCustomOrderState();
        this.createdAt = LocalDateTime.now();
    }

    public void advanceStatus(CustomOrderStatus status) {
        state.advance(this);
    }

    public void markPaid() {
        if (getStatus() != CustomOrderStatus.AWAITING_PAYMENT) {
            throw new DomainValidationException("Оплату можно зафиксировать только для заказа, ожидающего оплату");
        }
        setState(new PaidCustomOrderState());
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

    public CustomOrderStatus getStatus() {
        return state.getStatus();
    }

    public void setState(CustomOrderState newState) {
        if (newState == null) {
            throw new DomainValidationException("Состояние не может быть null");
        }
        this.state = newState;

    }
}
