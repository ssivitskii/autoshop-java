package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.port.out.SparePartRepository;
import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.SparePartJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.SparePartPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.SparePartJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SparePartRepositoryAdapter implements SparePartRepository {
    private final SparePartJpaRepository jpaRepository;
    private final SparePartPersistenceMapper mapper;

    public SparePartRepositoryAdapter(SparePartJpaRepository jpaRepository, SparePartPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SparePart save(SparePart part) {
        SparePartJpaEntity entity = mapper.toJpa(part);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public SparePart findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Запчасть с id '%s' не найдена".formatted(id)));
    }

    @Override
    public List<SparePart> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        SparePartJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Запчасть с id '%s' не найдена".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}