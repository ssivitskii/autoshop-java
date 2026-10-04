package dealership.order.core.domain.entity.testdrive;

import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class TestDriveRequest {
    @EqualsAndHashCode.Include
    private final String id;

    private String clientId;
    private String carId;
    private LocalDateTime requestedDateTime;
    private TestDriveStatus status;
    private LocalDateTime createdAt;

    public TestDriveRequest(String clientId, String carId, LocalDateTime requestedDateTime) {
        this(null, clientId, carId, requestedDateTime, TestDriveStatus.PENDING);
    }

    public TestDriveRequest(String id, String clientId, String carId, LocalDateTime requestedDateTime, TestDriveStatus status) {
        if (clientId == null) {
            throw new DomainValidationException("ID клиента не может быть пустым");
        }
        if (carId == null) {
            throw new DomainValidationException("ID машины не может быть пустым");
        }
        if (requestedDateTime == null) {
            throw new DomainValidationException("Время не может быть null");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.clientId = clientId;
        this.carId = carId;
        this.requestedDateTime = requestedDateTime;
        this.status = (status != null) ? status : TestDriveStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void approve() {
        if (status == TestDriveStatus.APPROVED) {
            return;
        }
        requireStatus(TestDriveStatus.PENDING, TestDriveStatus.APPROVED);
        this.status = TestDriveStatus.APPROVED;
    }

    public void complete() {
        if (status == TestDriveStatus.COMPLETED) {
            return;
        }
        requireStatus(TestDriveStatus.APPROVED, TestDriveStatus.COMPLETED);
        this.status = TestDriveStatus.COMPLETED;
    }

    public void cancel() {
        if (status == TestDriveStatus.CANCELLED) {
            return;
        }
        if (status != TestDriveStatus.PENDING && status != TestDriveStatus.APPROVED) {
            throw invalidTransition(TestDriveStatus.CANCELLED);
        }
        this.status = TestDriveStatus.CANCELLED;
    }

    private void requireStatus(TestDriveStatus required, TestDriveStatus target) {
        if (status != required) {
            throw invalidTransition(target);
        }
    }

    private TestDriveConflictException invalidTransition(TestDriveStatus target) {
        return new TestDriveConflictException(
                "Нельзя перевести заявку на тест-драйв из %s в %s".formatted(status, target));
    }
}
