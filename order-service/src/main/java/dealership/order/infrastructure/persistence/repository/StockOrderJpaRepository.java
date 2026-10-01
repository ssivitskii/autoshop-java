package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.StockOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockOrderJpaRepository extends JpaRepository<StockOrderJpaEntity, UUID> {
    List<StockOrderJpaEntity> findByRemovedFalse();

    Optional<StockOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<StockOrderJpaEntity> findByClientIdAndRemovedFalse(String clientId);
}
