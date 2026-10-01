package dealership.order.infrastructure.persistence.mapper;

import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.order.state.custom.*;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.infrastructure.persistence.entity.CustomOrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.UUID;

@Component
public class CustomOrderPersistenceMapper {
    public CustomOrder toDomain(CustomOrderJpaEntity entity) {
        CarConfiguration configuration = new CarConfiguration(entity.getCarModelId());
        entity.getSelectedVariants().forEach(configuration::selectVariant);

        CustomOrder order = new CustomOrder(entity.getId().toString(), entity.getManagerId(), entity.getClientId(), entity.getCarModelId(), configuration, entity.getTotalPrice());
        order.setState(mapStatusToState(entity.getStatus()));
        return order;
    }

    public CustomOrderJpaEntity toJpa(CustomOrder domain) {
        CustomOrderJpaEntity entity = new CustomOrderJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setManagerId(domain.getManagerId());
        entity.setClientId(domain.getClientId());
        entity.setCarModelId(domain.getCarModelId());
        entity.setTotalPrice(domain.getTotalPrice());
        entity.setStatus(domain.getStatus());
        entity.setSelectedVariants(new HashMap<>(domain.getConfiguration().getSelectedVariants()));
        return entity;
    }

    private CustomOrderState mapStatusToState(CustomOrderStatus status) {
        return switch (status) {
            case CREATED -> new CreatedCustomOrderState();
            case APPROVED_BY_WAREHOUSE -> new ApprovedByWarehouseState();
            case AWAITING_PAYMENT -> new AwaitingPaymentCustomOrderState();
            case PAID -> new PaidCustomOrderState();
            case AWAITING_DELIVERY -> new AwaitingDeliveryCustomOrderState();
            case READY_FOR_PICKUP -> new ReadyForPickupCustomOrderState();
            case COMPLETED -> new CompletedCustomOrderState();
            case CANCELLED -> new CancelledCustomOrderState();
        };
    }
}
