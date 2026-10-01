package dealership.storage.core.domain.entity.component;

import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ComponentCategory {
    @EqualsAndHashCode.Include
    private final String id;

    private String name;

    @Setter
    private String description;

    public ComponentCategory(String name) {
        this(null, name, null);
    }

    public ComponentCategory(String id, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("Название категории не может быть пустым");
        }
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
