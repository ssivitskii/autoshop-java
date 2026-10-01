package dealership.order.infrastructure.web.controller;

import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.infrastructure.web.dto.request.CreateCustomOrderRequest;
import dealership.order.infrastructure.web.dto.response.CustomOrderResponse;
import dealership.order.infrastructure.web.mapper.CustomOrderWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders/custom")
@Tag(name = "Custom Orders", description = "Заказы на автомобиль с определённой комплектацией")
public class CustomOrderController {

    private final CustomOrderService customOrderService;
    private final CustomOrderWebMapper mapper;

    public CustomOrderController(CustomOrderService customOrderService,
                                 CustomOrderWebMapper mapper) {
        this.customOrderService = customOrderService;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Создать заказ на автомобиль с определённой комплектацией")
    public ResponseEntity<CustomOrderResponse> create(@RequestBody CreateCustomOrderRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        CarConfiguration config = new CarConfiguration(request.getCarModelId());
        if (request.getSelectedVariants() != null) {
            request.getSelectedVariants().forEach(config::selectVariant);
        }
        CustomOrder order = customOrderService.createOrder(userId,
                request.getCarModelId(), config,
                request.getTotalPrice() != null ? request.getTotalPrice() : BigDecimal.ZERO);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(order));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Все заказы (MANAGER/ADMIN) или свои (USER)")
    public ResponseEntity<List<CustomOrderResponse>> getAll(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean isManagerOrAdmin = jwt.getClaimAsMap("realm_access") != null &&
                ((List<?>) ((java.util.Map<?, ?>) jwt.getClaimAsMap("realm_access")).get("roles"))
                        .stream().anyMatch(r -> "MANAGER".equals(r) || "ADMIN".equals(r));
        if (isManagerOrAdmin) {
            return ResponseEntity.ok(mapper.toResponseList(customOrderService.getAllOrders()));
        }
        return ResponseEntity.ok(mapper.toResponseList(customOrderService.getClientOrders(userId)));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Получить заказ по ID")
    public ResponseEntity<CustomOrderResponse> getById(@PathVariable String orderId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        var order = customOrderService.getOrderById(orderId);
        String userId = jwt.getSubject();
        boolean isManagerOrAdmin = jwt.getClaimAsMap("realm_access") != null &&
                ((List<?>) ((java.util.Map<?, ?>) jwt.getClaimAsMap("realm_access")).get("roles"))
                        .stream().anyMatch(r -> "MANAGER".equals(r) || "ADMIN".equals(r));
        if (!isManagerOrAdmin && !order.getClientId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Отменить заказ (только владелец или ADMIN)")
    public ResponseEntity<CustomOrderResponse> cancel(@PathVariable String orderId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        var order = customOrderService.getOrderById(orderId);
        String userId = jwt.getSubject();
        boolean isAdmin = jwt.getClaimAsMap("realm_access") != null &&
                ((List<?>) ((java.util.Map<?, ?>) jwt.getClaimAsMap("realm_access")).get("roles"))
                        .stream().anyMatch(r -> "ADMIN".equals(r));
        if (!isAdmin && !order.getClientId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var cancelled = customOrderService.cancelOrder(orderId);
        return ResponseEntity.ok(mapper.toResponse(cancelled));
    }

    @PostMapping("/{orderId}/advance")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAREHOUSE_ADMIN', 'ADMIN')")
    @Operation(summary = "Продвинуть статус заказа")
    public ResponseEntity<CustomOrderResponse> advance(@PathVariable String orderId) {
        var advanced = customOrderService.advanceOrder(orderId);
        return ResponseEntity.ok(mapper.toResponse(advanced));
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Заказы клиента (только MANAGER/ADMIN)")
    public ResponseEntity<List<CustomOrderResponse>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(mapper.toResponseList(customOrderService.getClientOrders(clientId)));
    }
}