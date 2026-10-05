package dealership.order.infrastructure.persistence.entity;

import dealership.order.core.domain.enums.DemoOrderType;
import dealership.order.core.domain.enums.DemoPaymentOutcome;
import dealership.order.core.domain.enums.DemoPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_payment_attempts")
@Getter
@Setter
@NoArgsConstructor
public class DemoPaymentAttemptJpaEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "stock_order_id")
    private UUID stockOrderId;

    @Column(name = "custom_order_id")
    private UUID customOrderId;

    @Column(nullable = false, updatable = false)
    private String clientId;

    @Column(nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private DemoPaymentOutcome requestedOutcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DemoPaymentStatus status;

    @Column(length = 64)
    private String failureCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public DemoOrderType getOrderType() {
        return stockOrderId != null ? DemoOrderType.STOCK : DemoOrderType.CUSTOM;
    }

    public UUID getOrderId() {
        return stockOrderId != null ? stockOrderId : customOrderId;
    }
}
