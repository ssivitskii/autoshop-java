package dealership.order.infrastructure.persistence.entity;

import dealership.order.core.domain.enums.CustomOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;


@Entity
@Table(name = "custom_orders")
@Getter
@Setter
@NoArgsConstructor
public class CustomOrderJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String managerId;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String carModelId;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomOrderStatus status = CustomOrderStatus.CREATED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "custom_order_variants", joinColumns = @JoinColumn(name = "custom_order_id"))
    @MapKeyColumn(name = "category_id")
    @Column(name = "variant_id")
    private Map<String, String> selectedVariants = new HashMap<>();
}
