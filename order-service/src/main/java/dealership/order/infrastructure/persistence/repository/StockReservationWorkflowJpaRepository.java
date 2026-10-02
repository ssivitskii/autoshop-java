package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.StockReservationWorkflowJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationWorkflowJpaRepository
        extends JpaRepository<StockReservationWorkflowJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from StockReservationWorkflowJpaEntity w where w.orderId = :orderId")
    Optional<StockReservationWorkflowJpaEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(value = "SELECT clock_timestamp()", nativeQuery = true)
    Instant databaseNow();

    @Query(value = """
            SELECT order_id
              FROM stock_reservation_workflows
             WHERE state IN ('CONFIRM_PENDING', 'RELEASE_PENDING')
               AND (next_attempt_at IS NULL OR next_attempt_at <= clock_timestamp())
             ORDER BY next_attempt_at NULLS FIRST, updated_at, order_id
             LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findPendingCandidateIds(@Param("limit") int limit);

    @Query(value = """
            SELECT order_id
              FROM stock_reservation_workflows
             WHERE state = 'HELD'
               AND storage_expires_at <= clock_timestamp()
             ORDER BY storage_expires_at, order_id
             LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findExpiredHoldCandidateIds(@Param("limit") int limit);
}
