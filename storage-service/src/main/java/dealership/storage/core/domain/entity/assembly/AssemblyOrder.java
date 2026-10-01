package dealership.storage.core.domain.entity.assembly;

import dealership.storage.core.domain.enums.AssemblyStatus;
import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class AssemblyOrder {
    @EqualsAndHashCode.Include
    private final String id;

    private final String sourceOrderId;
    private final String orderType;
    private final String traceId;
    private AssemblyStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public AssemblyOrder(String sourceOrderId, String orderType, String traceId) {
        this(null, sourceOrderId, orderType, traceId, AssemblyStatus.CREATED);
    }

    public AssemblyOrder(String id, String sourceOrderId, String orderType, String traceId, AssemblyStatus status) {
        if (sourceOrderId == null || sourceOrderId.isBlank()) {
            throw new DomainValidationException("sourceOrderId не может быть пустым");
        }
        if (orderType == null || orderType.isBlank()) {
            throw new DomainValidationException("orderType не может быть пустым");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.sourceOrderId = sourceOrderId;
        this.orderType = orderType;
        this.traceId = traceId;
        this.status = (status != null) ? status : AssemblyStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markAssembled() {
        this.status = AssemblyStatus.ASSEMBLED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = AssemblyStatus.FAIL;
        this.updatedAt = Instant.now();
    }
}
