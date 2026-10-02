package dealership.storage.infrastructure.persistence.entity;

import dealership.storage.core.domain.enums.CarReservationState;
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
@Table(name = "car_reservations")
@Getter
@Setter
@NoArgsConstructor
public class CarReservationJpaEntity {
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "car_id", nullable = false, updatable = false)
    private UUID carId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarReservationState state;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
