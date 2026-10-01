package dealership.storage.infrastructure.persistence.entity;

import dealership.storage.core.domain.enums.AssemblyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assembly_orders")
@Getter
@Setter
@NoArgsConstructor
public class AssemblyOrderJpaEntity extends BaseJpaEntity {

    @Column(name = "source_order_id", nullable = false)
    private String sourceOrderId;

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @Column(name = "trace_id")
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssemblyStatus status = AssemblyStatus.CREATED;
}
