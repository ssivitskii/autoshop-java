package dealership.storage.infrastructure.persistence.specification;

import dealership.storage.infrastructure.persistence.entity.CarModelJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class CarModelSpecification {

    private CarModelSpecification() {
    }

    public static Specification<CarModelJpaEntity> byBrandAndCategories(String brand, Set<String> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("removed")));

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.toLowerCase()));
            }

            if (categoryIds != null && !categoryIds.isEmpty()) {
                for (String categoryId : categoryIds) {
                    predicates.add(cb.isMember(categoryId, root.get("componentCategoryIds")));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
