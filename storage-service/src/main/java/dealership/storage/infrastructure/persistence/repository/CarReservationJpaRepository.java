package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.CarReservationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarReservationJpaRepository extends JpaRepository<CarReservationJpaEntity, UUID> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO car_reservations(order_id, car_id, state, hold_expires_at)
            VALUES (:orderId, :carId, 'HELD',
                    clock_timestamp() + (:ttlMillis * INTERVAL '1 millisecond'))
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    int insertHeld(@Param("orderId") UUID orderId,
                   @Param("carId") UUID carId,
                   @Param("ttlMillis") long ttlMillis);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO car_reservations(order_id, car_id, state, hold_expires_at)
            VALUES (:orderId, :carId, 'RELEASED', NULL)
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    int insertReleaseTombstone(@Param("orderId") UUID orderId,
                               @Param("carId") UUID carId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CarReservationJpaEntity r where r.orderId = :orderId")
    Optional<CarReservationJpaEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(value = "SELECT clock_timestamp()", nativeQuery = true)
    Instant databaseNow();

    @Query(value = """
            SELECT order_id
              FROM car_reservations
             WHERE state = 'HELD'
               AND hold_expires_at <= clock_timestamp()
             ORDER BY hold_expires_at, order_id
             LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findExpiredCandidateIds(@Param("limit") int limit);
}
