package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.AssemblyOrderJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.AssemblyOrderPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.AssemblyOrderJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AssemblyOrderRepositoryAdapter implements AssemblyOrderRepository {

    private final AssemblyOrderJpaRepository jpaRepository;
    private final AssemblyOrderPersistenceMapper mapper;

    public AssemblyOrderRepositoryAdapter(AssemblyOrderJpaRepository jpaRepository,
                                          AssemblyOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AssemblyOrder save(AssemblyOrder order) {
        AssemblyOrderJpaEntity entity = mapper.toEntity(order);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public AssemblyOrder findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id))
                .map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException("Заказ на сборку не найден: " + id));
    }

    @Override
    public List<AssemblyOrder> findAll() {
        return jpaRepository.findAll().stream()
                .filter(e -> !e.isRemoved())
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        AssemblyOrderJpaEntity entity = jpaRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Заказ на сборку не найден: " + id));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }

    @Override
    public boolean existsBySourceOrderId(String sourceOrderId) {
        return jpaRepository.existsBySourceOrderIdAndRemovedFalse(sourceOrderId);
    }
}
