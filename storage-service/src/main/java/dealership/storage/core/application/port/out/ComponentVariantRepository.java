package dealership.storage.core.application.port.out;

import dealership.storage.core.domain.entity.component.ComponentVariant;

import java.util.List;

public interface ComponentVariantRepository {
    ComponentVariant save(ComponentVariant variant);

    ComponentVariant findById(String id);

    List<ComponentVariant> findAll();

    List<ComponentVariant> findByCategoryId(String categoryId);

    List<ComponentVariant> findByCategoryIdAndCarModelId(String categoryId, String carModelId);

    void deleteById(String id);
}
