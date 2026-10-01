package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.infrastructure.persistence.entity.AssemblyOrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssemblyOrderPersistenceMapper {

    public AssemblyOrder toDomain(AssemblyOrderJpaEntity entity) {
        return new AssemblyOrder(
                entity.getId().toString(),
                entity.getSourceOrderId(),
                entity.getOrderType(),
                entity.getTraceId(),
                entity.getStatus()
        );
    }

    public AssemblyOrderJpaEntity toEntity(AssemblyOrder domain) {
        AssemblyOrderJpaEntity entity = new AssemblyOrderJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setSourceOrderId(domain.getSourceOrderId());
        entity.setOrderType(domain.getOrderType());
        entity.setTraceId(domain.getTraceId());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
