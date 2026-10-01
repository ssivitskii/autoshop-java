package dealership.storage.infrastructure.web.controller;

import dealership.storage.core.application.service.InventoryService;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.infrastructure.web.dto.request.CreateCarRequest;
import dealership.storage.infrastructure.web.dto.request.CreateSparePartRequest;
import dealership.storage.infrastructure.web.dto.response.CarResponse;
import dealership.storage.infrastructure.web.dto.response.SparePartResponse;
import dealership.storage.infrastructure.web.mapper.CarWebMapper;
import dealership.storage.infrastructure.web.mapper.SparePartWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Управление складом: автомобили и запчасти")
public class InventoryController {
    private final InventoryService inventoryService;
    private final CarWebMapper carMapper;
    private final SparePartWebMapper sparePartMapper;

    public InventoryController(InventoryService inventoryService, CarWebMapper carMapper, SparePartWebMapper sparePartMapper) {
        this.inventoryService = inventoryService;
        this.carMapper = carMapper;
        this.sparePartMapper = sparePartMapper;
    }

    @PostMapping("/cars")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Добавить автомобиль")
    public ResponseEntity<CarResponse> addCar(@RequestBody CreateCarRequest request) {
        Car car = carMapper.toDomain(request);
        Car saved = inventoryService.addCar(car);
        return ResponseEntity.status(HttpStatus.CREATED).body(carMapper.toResponse(saved));
    }

    @PutMapping("/cars/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Обновить автомобиль")
    public ResponseEntity<CarResponse> updateCar(@PathVariable String id, @RequestBody CreateCarRequest request) {
        Car car = carMapper.toDomain(request);
        Car updated = inventoryService.updateCar(id, car);
        return ResponseEntity.ok(carMapper.toResponse(updated));
    }

    @PatchMapping("/cars/{id}/test-drive/enable")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Добавить автомобиль в список тест-драйва")
    public ResponseEntity<Void> markForTestDrive(@PathVariable String id) {
        inventoryService.markForTestDrive(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/cars/{id}/test-drive/disable")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Убрать автомобиль из списка тест-драйва")
    public ResponseEntity<Void> removeFromTestDrive(@PathVariable String id) {
        inventoryService.removeFromTestDrive(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/parts")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Добавить запчасть")
    public ResponseEntity<SparePartResponse> addPart(@RequestBody CreateSparePartRequest request) {
        SparePart part = new SparePart(
                request.getName(),
                request.getManufacturer(),
                request.getPartNumber(),
                request.getPrice(),
                request.getQuantityInStock(),
                request.getCompatibleModels()
        );
        SparePart saved = inventoryService.addPart(part);
        return ResponseEntity.status(HttpStatus.CREATED).body(sparePartMapper.toResponse(saved));
    }

    @PutMapping("/parts/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Обновить запчасть")
    public ResponseEntity<SparePartResponse> updatePart(@PathVariable String id, @RequestBody CreateSparePartRequest request) {
        SparePart part = new SparePart(
                id,
                request.getName(),
                request.getManufacturer(),
                request.getPartNumber(),
                request.getPrice(),
                request.getQuantityInStock(),
                request.getCompatibleModels()
        );
        SparePart updated = inventoryService.updatePart(id, part);
        return ResponseEntity.ok(sparePartMapper.toResponse(updated));
    }

    @GetMapping("/parts")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Список всех запчастей")
    public ResponseEntity<List<SparePartResponse>> getAllParts() {
        return ResponseEntity.ok(sparePartMapper.toResponseList(inventoryService.getAllParts()));
    }

    @DeleteMapping("/parts/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Удалить запчасть")
    public ResponseEntity<Void> deletePart(@PathVariable String id) {
        inventoryService.deletePart(id);
        return ResponseEntity.noContent().build();
    }
}