package dealership.order.infrastructure.persistence.repository;

import dealership.order.core.domain.enums.DemoPaymentStatus;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DemoPaymentAttemptJpaRepository
        extends JpaRepository<DemoPaymentAttemptJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DemoPaymentAttemptJpaEntity p "
            + "where p.stockOrderId = :orderId and p.idempotencyKey = :key")
    Optional<DemoPaymentAttemptJpaEntity> findStockByKeyForUpdate(
            @Param("orderId") UUID orderId, @Param("key") UUID key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DemoPaymentAttemptJpaEntity p "
            + "where p.customOrderId = :orderId and p.idempotencyKey = :key")
    Optional<DemoPaymentAttemptJpaEntity> findCustomByKeyForUpdate(
            @Param("orderId") UUID orderId, @Param("key") UUID key);

    Optional<DemoPaymentAttemptJpaEntity> findFirstByStockOrderIdAndStatusIn(
            UUID stockOrderId, Collection<DemoPaymentStatus> statuses);

    Optional<DemoPaymentAttemptJpaEntity> findFirstByCustomOrderIdAndStatusIn(
            UUID customOrderId, Collection<DemoPaymentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DemoPaymentAttemptJpaEntity p "
            + "where p.stockOrderId = :orderId and p.status = :status")
    Optional<DemoPaymentAttemptJpaEntity> findPendingStockForUpdate(
            @Param("orderId") UUID orderId, @Param("status") DemoPaymentStatus status);

    Optional<DemoPaymentAttemptJpaEntity> findByIdAndStockOrderId(UUID id, UUID stockOrderId);

    Optional<DemoPaymentAttemptJpaEntity> findByIdAndCustomOrderId(UUID id, UUID customOrderId);
}
