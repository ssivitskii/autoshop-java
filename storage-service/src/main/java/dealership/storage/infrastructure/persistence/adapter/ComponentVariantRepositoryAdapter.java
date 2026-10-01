package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.port.out.ComponentVariantRepository;
import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.ComponentVariantJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.ComponentVariantPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.ComponentVariantJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ComponentVariantRepositoryAdapter implements ComponentVariantRepository {
    private final ComponentVariantJpaRepository jpaRepository;
    private final ComponentVariantPersistenceMapper mapper;

    public ComponentVariantRepositoryAdapter(ComponentVariantJpaRepository jpaRepository, ComponentVariantPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ComponentVariant save(ComponentVariant variant) {
        ComponentVariantJpaEntity entity = mapper.toJpa(variant);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public ComponentVariant findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Вариант компонента с id '%s' не найден".formatted(id)));
    }

    @Override
    public List<ComponentVariant> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ComponentVariant> findByCategoryId(String categoryId) {
        return jpaRepository.findByCategoryIdAndRemovedFalse(categoryId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ComponentVariant> findByCategoryIdAndCarModelId(String categoryId, String carModelId) {
        return jpaRepository.findByCategoryIdAndRemovedFalse(categoryId).stream().map(mapper::toDomain).filter(v -> v.isCompatibleWith(carModelId)).toList();
    }

    @Override
    public void deleteById(String id) {
        ComponentVariantJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Вариант компонента с id '%s' не найден".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}