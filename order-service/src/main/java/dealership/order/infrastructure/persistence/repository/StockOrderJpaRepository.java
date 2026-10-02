package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.StockOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockOrderJpaRepository extends JpaRepository<StockOrderJpaEntity, UUID> {
    List<StockOrderJpaEntity> findByRemovedFalse();

    Optional<StockOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<StockOrderJpaEntity> findByClientIdAndRemovedFalse(String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StockOrderJpaEntity o where o.id = :id and o.removed = false")
    Optional<StockOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
