package dealership.order.infrastructure.persistence.adapter;

import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.infrastructure.persistence.entity.StockOrderJpaEntity;
import dealership.order.infrastructure.persistence.mapper.StockOrderPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.StockOrderJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class StockOrderRepositoryAdapter implements StockOrderRepository {
    private final StockOrderJpaRepository jpaRepository;
    private final StockOrderPersistenceMapper mapper;

    public StockOrderRepositoryAdapter(StockOrderJpaRepository jpaRepository, StockOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public StockOrder save(StockOrder order) {
        StockOrderJpaEntity entity = mapper.toJpa(order);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public StockOrder findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
    }

    @Override
    public StockOrder findByIdForUpdate(String id) {
        return jpaRepository.findByIdForUpdate(UUID.fromString(id)).map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
    }

    @Override
    public List<StockOrder> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<StockOrder> findByClientId(String clientId) {
        return jpaRepository.findByClientIdAndRemovedFalse(clientId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        StockOrderJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Заказ с id '%s' не найден".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}
