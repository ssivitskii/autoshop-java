package dealership.storage.infrastructure.web.controller;

import dealership.storage.core.application.dto.CarFilterDto;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.domain.enums.*;
import dealership.storage.infrastructure.web.dto.response.CarResponse;
import dealership.storage.infrastructure.web.mapper.CarWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cars")
@Tag(name = "Cars", description = "Поиск и просмотр автомобилей")
public class CarController {
    private final CarSearchService carSearchService;
    private final CarWebMapper mapper;

    public CarController(CarSearchService carSearchService, CarWebMapper mapper) {
        this.carSearchService = carSearchService;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Получить автомобиль по ID")
    public ResponseEntity<CarResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(mapper.toResponse(carSearchService.getById(id)));
    }

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список доступных автомобилей")
    public ResponseEntity<List<CarResponse>> getAvailable() {
        return ResponseEntity.ok(mapper.toResponseList(carSearchService.findAvailableCars()));
    }

    @GetMapping("/test-drive")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список автомобилей для тест-драйва")
    public ResponseEntity<List<CarResponse>> getForTestDrive() {
        return ResponseEntity.ok(mapper.toResponseList(carSearchService.findCarsForTestDrive()));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Поиск автомобилей по фильтрам")
    public ResponseEntity<List<CarResponse>> search(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) BodyType bodyType,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) TransmissionType transmissionType,
            @RequestParam(required = false) DriveType driveType,
            @RequestParam(required = false) Color color,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minEnginePowerHp,
            @RequestParam(required = false) Integer maxEnginePowerHp,
            @RequestParam(required = false) Double minEngineVolume,
            @RequestParam(required = false) Double maxEngineVolume) {
        CarFilterDto filter = new CarFilterDto().withBrand(brand)
                .withModelName(modelName)
                .withBodyType(bodyType)
                .withFuelType(fuelType)
                .withTransmissionType(transmissionType)
                .withDriveType(driveType)
                .withColor(color)
                .withMinPrice(minPrice)
                .withMaxPrice(maxPrice)
                .withMinEnginePowerHp(minEnginePowerHp)
                .withMaxEnginePowerHp(maxEnginePowerHp)
                .withMinEngineVolume(minEngineVolume)
                .withMaxEngineVolume(maxEngineVolume);

        return ResponseEntity.ok(mapper.toResponseList(carSearchService.searchByFilter(filter)));
    }
}