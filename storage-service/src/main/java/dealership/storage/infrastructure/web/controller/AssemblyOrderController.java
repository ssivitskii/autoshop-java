package dealership.storage.infrastructure.web.controller;

import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.enums.AssemblyStatus;
import dealership.storage.infrastructure.web.dto.request.CreateAssemblyOrderRequest;
import dealership.storage.infrastructure.web.dto.response.AssemblyOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assembly-orders")
@Tag(name = "Assembly Orders", description = "Заказы на сборку (внутренний складской процесс)")
public class AssemblyOrderController {

    private final AssemblyOrderService service;

    public AssemblyOrderController(AssemblyOrderService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Создать заказ на сборку")
    public ResponseEntity<AssemblyOrderResponse> create(@RequestBody CreateAssemblyOrderRequest request) {
        AssemblyOrder order = new AssemblyOrder(
                request.getSourceOrderId(),
                request.getOrderType(),
                request.getTraceId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.create(order)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Список всех заказов на сборку")
    public ResponseEntity<List<AssemblyOrderResponse>> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Получить заказ на сборку по ID")
    public ResponseEntity<AssemblyOrderResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(toResponse(service.getById(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Обновить статус заказа на сборку")
    public ResponseEntity<AssemblyOrderResponse> updateStatus(@PathVariable String id,
                                                              @RequestParam AssemblyStatus status) {
        AssemblyOrder order = service.getById(id);
        if (status == AssemblyStatus.ASSEMBLED) {
            order.markAssembled();
        } else if (status == AssemblyStatus.FAIL) {
            order.markFailed();
        }
        return ResponseEntity.ok(toResponse(service.update(id, order)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Удалить заказ на сборку")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AssemblyOrderResponse toResponse(AssemblyOrder order) {
        AssemblyOrderResponse r = new AssemblyOrderResponse();
        r.setId(order.getId());
        r.setSourceOrderId(order.getSourceOrderId());
        r.setOrderType(order.getOrderType());
        r.setTraceId(order.getTraceId());
        r.setStatus(order.getStatus().name());
        return r;
    }
}
