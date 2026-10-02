package dealership.storage.core.application.service;

import dealership.storage.core.application.dto.CarConfigurationRequestDto;
import dealership.storage.core.application.dto.ConfigurationResultDto;
import dealership.storage.core.application.port.out.CarModelRepository;
import dealership.storage.core.application.port.out.ComponentCategoryRepository;
import dealership.storage.core.application.port.out.ComponentVariantRepository;
import dealership.storage.core.domain.entity.car.CarConfiguration;
import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.core.domain.exception.DomainValidationException;
import dealership.storage.core.domain.exception.IncompatibleComponentException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CarConfigurationService {
    private final CarModelRepository carModelRepository;
    private final ComponentVariantRepository componentVariantRepository;
    private final ComponentCategoryRepository componentCategoryRepository;

    public CarConfigurationService(CarModelRepository carModelRepository, ComponentVariantRepository componentVariantRepository, ComponentCategoryRepository componentCategoryRepository) {
        this.carModelRepository = carModelRepository;
        this.componentVariantRepository = componentVariantRepository;
        this.componentCategoryRepository = componentCategoryRepository;
    }

    public ConfigurationResultDto configure(CarConfigurationRequestDto request) {
        NormalizedRequest normalized = normalize(request);
        CarModel carModel = carModelRepository.findById(normalized.carModelId());
        validateRequiredCategories(carModel, normalized.selectedVariants());
        List<ComponentVariant> selectedComponents = validateAndCollectComponents(
                carModel, normalized.selectedVariants());
        CarConfiguration configuration = new CarConfiguration(carModel.getId());
        normalized.selectedVariants().forEach(configuration::selectVariant);

        BigDecimal totalPrice = calculateTotalPrice(carModel, selectedComponents);

        return new ConfigurationResultDto(
                configuration, totalPrice, selectedComponents, carModel.getFullModelName()
        );
    }

    public List<ComponentVariant> getAvailableVariants(String categoryId, String carModelId) {
        carModelRepository.findById(carModelId);
        componentCategoryRepository.findById(categoryId);
        return componentVariantRepository.findByCategoryIdAndCarModelId(categoryId, carModelId);
    }


    private void validateRequiredCategories(CarModel carModel, Map<String, String> selectedVariants) {
        Set<String> requiredCategories = new LinkedHashSet<>();
        for (String categoryId : carModel.getComponentCategoryIds()) {
            String canonical = canonicalUuid(categoryId, "categoryId");
            componentCategoryRepository.findById(canonical);
            requiredCategories.add(canonical);
        }
        Set<String> selectedCategories = selectedVariants.keySet();

        Set<String> missing = new LinkedHashSet<>(requiredCategories);
        missing.removeAll(selectedCategories);
        Set<String> extra = new LinkedHashSet<>(selectedCategories);
        extra.removeAll(requiredCategories);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new DomainValidationException(
                    "Набор категорий должен точно соответствовать модели; отсутствуют: %s; лишние: %s"
                            .formatted(missing, extra));
        }
    }

    private List<ComponentVariant> validateAndCollectComponents(CarModel carModel,
                                                                Map<String, String> selectedVariants) {
        List<ComponentVariant> selectedComponents = new ArrayList<>();

        for (var entry : selectedVariants.entrySet()) {
            String categoryId = entry.getKey();
            String variantId = entry.getValue();

            ComponentVariant variant = componentVariantRepository.findById(variantId);
            if (!canonicalUuid(variant.getCategoryId(), "variant.categoryId").equals(categoryId)) {
                throw new DomainValidationException("Вариант " + variant.getName() + " не принадлежит указанной категории");
            }

            boolean compatible = variant.getCompatibleCarModelIds().stream()
                    .map(id -> canonicalUuid(id, "compatibleCarModelId"))
                    .anyMatch(carModel.getId()::equals);
            if (!compatible) {
                String categoryName = componentCategoryRepository.findById(categoryId).getName();
                throw new IncompatibleComponentException(String.format("Выбранный компонент '%s' (категория: %s) недоступен для модели %s", variant.getName(), categoryName, carModel.getFullModelName()));
            }
            selectedComponents.add(variant);
        }
        return selectedComponents;
    }

    private BigDecimal calculateTotalPrice(CarModel carModel, List<ComponentVariant> selectedComponents) {
        BigDecimal totalPrice = carModel.getBasePrice();

        for (ComponentVariant variant : selectedComponents) {
            totalPrice = totalPrice.add(variant.getPriceAdjustment());
        }
        try {
            BigDecimal normalized = totalPrice.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 15) {
                throw new DomainValidationException("Итоговая цена должна быть положительной и помещаться в DECIMAL(15,2)");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new DomainValidationException("Итоговая цена должна иметь не более двух знаков после запятой");
        }
    }

    private NormalizedRequest normalize(CarConfigurationRequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Конфигурация не может быть null");
        }
        String carModelId = canonicalUuid(request.getCarModelId(), "carModelId");
        if (request.getSelectedVariants() == null) {
            throw new DomainValidationException("selectedVariants не может быть null");
        }
        Map<String, String> selectedVariants = new LinkedHashMap<>();
        request.getSelectedVariants().forEach((rawCategory, rawVariant) -> {
            String category = canonicalUuid(rawCategory, "categoryId");
            String variant = canonicalUuid(rawVariant, "variantId");
            if (selectedVariants.putIfAbsent(category, variant) != null) {
                throw new DomainValidationException("Категория указана более одного раза: " + category);
            }
        });
        return new NormalizedRequest(carModelId, Map.copyOf(selectedVariants));
    }

    private String canonicalUuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new DomainValidationException(field + " должен быть UUID");
        }
    }

    private record NormalizedRequest(String carModelId, Map<String, String> selectedVariants) {
    }
}
