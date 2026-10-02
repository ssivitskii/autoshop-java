package dealership.order.infrastructure.persistence.entity;

import dealership.order.core.domain.enums.StockReservationWorkflowState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation_workflows")
@Getter
@Setter
@NoArgsConstructor
public class StockReservationWorkflowJpaEntity {
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "car_id", nullable = false, updatable = false)
    private String carId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReservationWorkflowState state;

    @Column(name = "storage_expires_at")
    private Instant storageExpiresAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
