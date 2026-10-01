package dealership.order.infrastructure.persistence.entity;


import dealership.order.core.domain.enums.StockOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_orders")
@Getter
@Setter
@NoArgsConstructor
public class StockOrderJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String managerId;

    @Column(nullable = false)
    private String carId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockOrderStatus status = StockOrderStatus.CREATED;
}
