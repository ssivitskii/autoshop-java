package dealership.storage.infrastructure.persistence.entity;


import dealership.storage.core.domain.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "cars")
@Getter
@Setter
@NoArgsConstructor
public class CarJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuelType fuelType;

    @Column(nullable = false)
    private int enginePowerHp;

    @Column(nullable = false)
    private double engineVolumeLiters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmissionType transmissionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriveType driveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Color color;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private boolean availableForTestDrive = false;
}
