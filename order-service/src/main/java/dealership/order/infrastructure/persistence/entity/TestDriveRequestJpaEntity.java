package dealership.order.infrastructure.persistence.entity;

import dealership.order.core.domain.enums.TestDriveStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_drive_requests")
@Getter
@Setter
@NoArgsConstructor
public class TestDriveRequestJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String carId;

    @Column(nullable = false)
    private LocalDateTime requestedDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestDriveStatus status = TestDriveStatus.PENDING;
}
