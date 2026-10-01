package dealership.storage.core.application.port.out;

import dealership.storage.core.domain.entity.component.ComponentCategory;

import java.util.List;

public interface ComponentCategoryRepository {
    ComponentCategory save(ComponentCategory category);

    ComponentCategory findById(String id);

    List<ComponentCategory> findAll();

    void deleteById(String id);
}
