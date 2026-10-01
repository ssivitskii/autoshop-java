package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.port.out.ComponentCategoryRepository;
import dealership.storage.core.domain.entity.component.ComponentCategory;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.ComponentCategoryJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.ComponentCategoryPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.ComponentCategoryJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ComponentCategoryRepositoryAdapter implements ComponentCategoryRepository {

    private final ComponentCategoryJpaRepository jpaRepository;
    private final ComponentCategoryPersistenceMapper mapper;

    public ComponentCategoryRepositoryAdapter(ComponentCategoryJpaRepository jpaRepository, ComponentCategoryPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ComponentCategory save(ComponentCategory category) {
        ComponentCategoryJpaEntity entity = mapper.toJpa(category);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public ComponentCategory findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id))
                .map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException("Категория компонента с id '%s' не найдена".formatted(id)));
    }

    @Override
    public List<ComponentCategory> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        ComponentCategoryJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Категория с id '%s' не найдена".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}