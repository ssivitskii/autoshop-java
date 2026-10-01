package dealership.storage.infrastructure.persistence.specification;

import dealership.storage.core.application.dto.CarFilterDto;
import dealership.storage.infrastructure.persistence.entity.CarJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;


public class CarSpecification {

    private CarSpecification() {
    }

    public static Specification<CarJpaEntity> byFilter(CarFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("removed")));
            predicates.add(cb.isTrue(root.get("available")));

            if (filter.getBrand() != null) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), filter.getBrand().toLowerCase()));
            }
            if (filter.getModelName() != null) {
                predicates.add(cb.equal(cb.lower(root.get("modelName")), filter.getModelName().toLowerCase()));
            }
            if (filter.getBodyType() != null) {
                predicates.add(cb.equal(root.get("bodyType"), filter.getBodyType()));
            }
            if (filter.getFuelType() != null) {
                predicates.add(cb.equal(root.get("fuelType"), filter.getFuelType()));
            }
            if (filter.getTransmissionType() != null) {
                predicates.add(cb.equal(root.get("transmissionType"), filter.getTransmissionType()));
            }
            if (filter.getDriveType() != null) {
                predicates.add(cb.equal(root.get("driveType"), filter.getDriveType()));
            }
            if (filter.getColor() != null) {
                predicates.add(cb.equal(root.get("color"), filter.getColor()));
            }
            if (filter.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
            }
            if (filter.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
            }
            if (filter.getMinEnginePowerHp() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("enginePowerHp"), filter.getMinEnginePowerHp()));
            }
            if (filter.getMaxEnginePowerHp() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("enginePowerHp"), filter.getMaxEnginePowerHp()));
            }
            if (filter.getMinEngineVolume() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("engineVolumeLiters"), filter.getMinEngineVolume()));
            }
            if (filter.getMaxEngineVolume() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("engineVolumeLiters"), filter.getMaxEngineVolume()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
