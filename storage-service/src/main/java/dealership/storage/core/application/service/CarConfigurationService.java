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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        CarModel carModel = carModelRepository.findById(request.getCarModelId());
        validateRequiredCategories(carModel, request);
        List<ComponentVariant> selectedComponents = validateAndCollectComponents(carModel, request);
        CarConfiguration configuration = new CarConfiguration(carModel.getId());
        request.getSelectedVariants().forEach(configuration::selectVariant);

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


    private void validateRequiredCategories(CarModel carModel, CarConfigurationRequestDto request) {
        Set<String> requiredCategories = carModel.getComponentCategoryIds();
        Set<String> selectedCategories = request.getSelectedVariants().keySet();

        List<String> missingCategories = requiredCategories.stream().filter(categoryId -> !selectedCategories.contains(categoryId)).map(categoryId -> {
            try {
                return componentCategoryRepository.findById(categoryId).getName();
            } catch (Exception e) {
                return categoryId;
            }
        }).toList();

        if (!missingCategories.isEmpty()) {
            throw new DomainValidationException("Отсутствуют обязательные узлы: " + String.join(", ", missingCategories));
        }
    }

    private List<ComponentVariant> validateAndCollectComponents(CarModel carModel, CarConfigurationRequestDto request) {
        List<ComponentVariant> selectedComponents = new ArrayList<>();

        for (var entry : request.getSelectedVariants().entrySet()) {
            String categoryId = entry.getKey();
            String variantId = entry.getValue();

            ComponentVariant variant = componentVariantRepository.findById(variantId);
            if (!variant.getCategoryId().equals(categoryId)) {
                throw new DomainValidationException("Вариант " + variant.getName() + " не принадлежит указанной категории");
            }

            if (!variant.isCompatibleWith(carModel.getId())) {
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
        return totalPrice;
    }


}
