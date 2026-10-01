package dealership.order.infrastructure.persistence.mapper;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.order.state.stock.*;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.infrastructure.persistence.entity.StockOrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StockOrderPersistenceMapper {
    public StockOrder toDomain(StockOrderJpaEntity entity) {
        StockOrder order = new StockOrder(entity.getId().toString(), entity.getClientId(), entity.getManagerId(), entity.getCarId());
        order.setState(mapStatusToState(entity.getStatus()));
        return order;
    }

    public StockOrderJpaEntity toJpa(StockOrder domain) {
        StockOrderJpaEntity entity = new StockOrderJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setClientId(domain.getClientId());
        entity.setManagerId(domain.getManagerId());
        entity.setCarId(domain.getCarId());
        entity.setStatus(domain.getStatus());
        return entity;
    }

    private OrderState mapStatusToState(StockOrderStatus status) {
        return switch (status) {
            case CREATED -> new CreatedState();
            case APPROVED_BY_MANAGER -> new ApprovedByManagerState();
            case AWAITING_PAYMENT -> new AwaitingPaymentState();
            case PAID -> new PaidState();
            case READY_FOR_PICKUP -> new ReadyForPickupState();
            case COMPLETED -> new CompletedState();
            case CANCELLED -> new CancelledState();
        };
    }
}
