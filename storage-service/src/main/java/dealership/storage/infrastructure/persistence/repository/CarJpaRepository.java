package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.CarJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarJpaRepository extends JpaRepository<CarJpaEntity, UUID>, JpaSpecificationExecutor<CarJpaEntity> {
    List<CarJpaEntity> findByAvailableTrueAndRemovedFalse();

    List<CarJpaEntity> findByRemovedFalse();

    Optional<CarJpaEntity> findByIdAndRemovedFalse(UUID id);

    boolean existsByIdAndRemovedFalse(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cars
               SET available = FALSE, reserved_by_order_id = :orderId, updated_at = CURRENT_TIMESTAMP
             WHERE id = :carId
               AND removed = FALSE
               AND ((available = TRUE AND reserved_by_order_id IS NULL)
                    OR reserved_by_order_id = :orderId)
            """, nativeQuery = true)
    int reserve(@Param("carId") UUID carId, @Param("orderId") UUID orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cars
               SET available = TRUE, reserved_by_order_id = NULL, updated_at = CURRENT_TIMESTAMP
             WHERE id = :carId
               AND removed = FALSE
               AND reserved_by_order_id = :orderId
            """, nativeQuery = true)
    int release(@Param("carId") UUID carId, @Param("orderId") UUID orderId);

}
