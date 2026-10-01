package dealership.storage.core.domain.entity.part;

import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class SparePart {
    @EqualsAndHashCode.Include
    private final String id;

    private String name;
    private String manufacturer;
    private String partNumber;
    private BigDecimal price;
    private int quantityInStock;
    private Set<String> compatibleModels;

    public SparePart(String name, String manufacturer, String partNumber,
                     BigDecimal price, int quantityInStock, String compatibleModels) {
        this(null, name, manufacturer, partNumber, price, quantityInStock, compatibleModels);
    }

    public SparePart(String id, String name, String manufacturer, String partNumber,
                     BigDecimal price, int quantityInStock, String compatibleModels) {
        if (name == null || name.isEmpty()) {
            throw new DomainValidationException("Название запчасти не может быть пустым");
        }
        if (partNumber == null || partNumber.isEmpty()) {
            throw new DomainValidationException("Артикул запчасти не может быть пустым");
        }
        if (price == null) {
            throw new DomainValidationException("Цена запчасти должна быть положительной");
        }
        if (quantityInStock < 0) {
            throw new DomainValidationException("Количество не может быть отрицательным");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.name = name;
        this.partNumber = partNumber;
        this.price = price;
        this.quantityInStock = quantityInStock;
        this.manufacturer = manufacturer;
        this.compatibleModels = new HashSet<>();
        if (compatibleModels != null && !compatibleModels.isBlank()) {
            Arrays.stream(compatibleModels.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(this.compatibleModels::add);
        }
    }

    public void decreaseQuantity(int amount) {
        if (amount <= 0) {
            throw new DomainValidationException("Количество должно быть положительным");
        }
        if (quantityInStock < amount) {
            throw new DomainValidationException("Недостаточно запчастей на складе");
        }
        this.quantityInStock -= amount;
    }

    public void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new DomainValidationException("Количество должно быть положительным");
        }
        this.quantityInStock += amount;
    }

    public boolean isAvailable() {
        return quantityInStock > 0;
    }
}
