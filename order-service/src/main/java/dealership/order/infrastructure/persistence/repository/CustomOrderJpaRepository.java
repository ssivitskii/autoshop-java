package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.CustomOrderJpaEntity;
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
public interface CustomOrderJpaRepository extends JpaRepository<CustomOrderJpaEntity, UUID> {
    List<CustomOrderJpaEntity> findByRemovedFalse();

    Optional<CustomOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomOrderJpaEntity o where o.id = :id and o.removed = false")
    Optional<CustomOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<CustomOrderJpaEntity> findByClientIdAndRemovedFalse(String clientId);
}
