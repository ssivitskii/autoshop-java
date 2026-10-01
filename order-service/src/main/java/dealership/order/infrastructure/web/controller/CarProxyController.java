package dealership.order.infrastructure.web.controller;

import dealership.common.grpc.CarDto;
import dealership.order.infrastructure.grpc.CarGrpcClient;
import dealership.order.infrastructure.web.dto.response.CarGrpcResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cars")
@Tag(name = "Cars (via gRPC)", description = "Автомобили в наличии (данные из StorageService через gRPC)")
public class CarProxyController {

    private final CarGrpcClient carGrpcClient;

    public CarProxyController(CarGrpcClient carGrpcClient) {
        this.carGrpcClient = carGrpcClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Список автомобилей в наличии (через gRPC)")
    public ResponseEntity<?> getAvailableCars() {
        try {
            List<CarGrpcResponse> cars = carGrpcClient.getAvailableCars().stream()
                    .map(CarGrpcResponse::fromGrpc)
                    .toList();
            return ResponseEntity.ok(cars);
        } catch (StatusRuntimeException e) {
            return handleGrpcError(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Автомобиль по ID (через gRPC)")
    public ResponseEntity<?> getCarById(@PathVariable String id) {
        try {
            CarDto car = carGrpcClient.getCarById(id);
            return ResponseEntity.ok(CarGrpcResponse.fromGrpc(car));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND || (e.getStatus().getCode() == Status.Code.INTERNAL && e.getStatus().getDescription() != null && e.getStatus().getDescription().contains("Invalid UUID"))) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Car not found", "id", id));
            }
            return handleGrpcError(e);
        }
    }

    private ResponseEntity<?> handleGrpcError(StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.UNAVAILABLE ||
            e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "StorageService is unavailable", "details", e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "timeout or connection refused"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal gRPC error", "details", e.getMessage()));
    }
}
