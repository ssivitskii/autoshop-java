package dealership.order.infrastructure.persistence.adapter;

import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.infrastructure.persistence.entity.CustomOrderJpaEntity;
import dealership.order.infrastructure.persistence.mapper.CustomOrderPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.CustomOrderJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CustomOrderRepositoryAdapter implements CustomOrderRepository {
    private final CustomOrderJpaRepository jpaRepository;
    private final CustomOrderPersistenceMapper mapper;

    public CustomOrderRepositoryAdapter(CustomOrderJpaRepository jpaRepository, CustomOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CustomOrder save(CustomOrder order) {
        CustomOrderJpaEntity entity = mapper.toJpa(order);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public CustomOrder findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
    }

    @Override
    public CustomOrder findByIdForUpdate(String id) {
        return jpaRepository.findByIdForUpdate(UUID.fromString(id)).map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
    }

    @Override
    public List<CustomOrder> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CustomOrder> findByClientId(String clientId) {
        return jpaRepository.findByClientIdAndRemovedFalse(clientId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        CustomOrderJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}
