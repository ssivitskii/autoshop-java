package dealership.storage.infrastructure.web.controller;

import dealership.storage.core.application.dto.CarConfigurationRequestDto;
import dealership.storage.core.application.dto.ConfigurationResultDto;
import dealership.storage.core.application.service.CarConfigurationService;
import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.infrastructure.persistence.mapper.CarModelPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.CarModelJpaRepository;
import dealership.storage.infrastructure.persistence.specification.CarModelSpecification;
import dealership.storage.infrastructure.web.dto.request.ConfigureCarRequest;
import dealership.storage.infrastructure.web.dto.response.CarModelResponse;
import dealership.storage.infrastructure.web.dto.response.ConfigurationResultResponse;
import dealership.storage.infrastructure.web.mapper.CarModelWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/configuration")
@Tag(name = "Configuration", description = "Конфигуратор автомобилей")
public class ConfigurationController {

    private final CarConfigurationService configurationService;
    private final CarModelJpaRepository carModelJpaRepository;
    private final CarModelPersistenceMapper carModelPersistenceMapper;
    private final CarModelWebMapper carModelWebMapper;

    public ConfigurationController(
            CarConfigurationService configurationService,
            CarModelJpaRepository carModelJpaRepository,
            CarModelPersistenceMapper carModelPersistenceMapper,
            CarModelWebMapper carModelWebMapper) {
        this.configurationService = configurationService;
        this.carModelJpaRepository = carModelJpaRepository;
        this.carModelPersistenceMapper = carModelPersistenceMapper;
        this.carModelWebMapper = carModelWebMapper;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Собрать конфигурацию автомобиля")
    public ResponseEntity<ConfigurationResultResponse> configure(@RequestBody ConfigureCarRequest request) {
        CarConfigurationRequestDto dto = new CarConfigurationRequestDto(request.getCarModelId());
        if (request.getSelectedVariants() != null) {
            request.getSelectedVariants().forEach(dto::addVariant);
        }
        ConfigurationResultDto result = configurationService.configure(dto);
        ConfigurationResultResponse response = new ConfigurationResultResponse();
        response.setCarModelName(result.getCarModelName());
        response.setTotalPrice(result.getTotalPrice());
        response.setSelectedVariants(result.getConfiguration().getSelectedVariants());
        response.setComponents(result.getSelectedComponents().stream().map(v -> {
            ConfigurationResultResponse.ComponentVariantResponse cvr = new ConfigurationResultResponse.ComponentVariantResponse();
            cvr.setId(v.getId());
            cvr.setName(v.getName());
            cvr.setCategoryId(v.getCategoryId());
            cvr.setPriceAdjustment(v.getPriceAdjustment());
            cvr.setBase(v.isBase());
            return cvr;
        }).collect(Collectors.toList()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/models")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список моделей с фильтрацией по бренду и узлам (Specifications)")
    public ResponseEntity<List<CarModelResponse>> getModels(@RequestParam(required = false) String brand,
                                                            @RequestParam(required = false) Set<String> categoryIds) {
        List<CarModel> models = carModelJpaRepository
                .findAll(CarModelSpecification.byBrandAndCategories(brand, categoryIds))
                .stream()
                .map(carModelPersistenceMapper::toDomain)
                .toList();
        return ResponseEntity.ok(carModelWebMapper.toResponseList(models));
    }

    @GetMapping("/models/{carModelId}/categories/{categoryId}/variants")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Доступные варианты узла для модели")
    public ResponseEntity<List<ConfigurationResultResponse.ComponentVariantResponse>> getVariants(@PathVariable String categoryId,
                                                                                                  @PathVariable String carModelId) {
        List<ComponentVariant> variants = configurationService.getAvailableVariants(categoryId, carModelId);
        List<ConfigurationResultResponse.ComponentVariantResponse> response = variants.stream().map(v -> {
            ConfigurationResultResponse.ComponentVariantResponse cvr = new ConfigurationResultResponse.ComponentVariantResponse();
            cvr.setId(v.getId());
            cvr.setName(v.getName());
            cvr.setCategoryId(v.getCategoryId());
            cvr.setPriceAdjustment(v.getPriceAdjustment());
            cvr.setBase(v.isBase());
            return cvr;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}