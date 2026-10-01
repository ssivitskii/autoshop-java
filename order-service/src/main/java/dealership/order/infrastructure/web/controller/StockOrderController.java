package dealership.order.infrastructure.web.controller;

import dealership.order.core.application.service.StockOrderService;
import dealership.order.infrastructure.web.dto.request.CreateStockOrderRequest;
import dealership.order.infrastructure.web.dto.response.StockOrderResponse;
import dealership.order.infrastructure.web.mapper.StockOrderWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders/stock")
@Tag(name = "Stock Orders", description = "Заказы на покупку автомобилей в наличии")
public class StockOrderController {
    private final StockOrderService stockOrderService;
    private final StockOrderWebMapper mapper;

    public StockOrderController(StockOrderService stockOrderService, StockOrderWebMapper mapper) {
        this.stockOrderService = stockOrderService;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Создать заказ на автомобиль в наличии")
    public ResponseEntity<StockOrderResponse> create(@RequestBody CreateStockOrderRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        var order = stockOrderService.createOrder(userId, request.getCarId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(order));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Все заказы (MANAGER/ADMIN) или свои (USER)")
    public ResponseEntity<List<StockOrderResponse>> getAll(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean isManagerOrAdmin = jwt.getClaimAsMap("realm_access") != null &&
                ((List<?>) ((java.util.Map<?, ?>) jwt.getClaimAsMap("realm_access")).get("roles"))
                        .stream().anyMatch(r -> "MANAGER".equals(r) || "ADMIN".equals(r));
        if (isManagerOrAdmin) {
            return ResponseEntity.ok(mapper.toResponseList(stockOrderService.getAllOrders()));
        }
        return ResponseEntity.ok(mapper.toResponseList(stockOrderService.getClientOrders(userId)));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Получить заказ по ID")
    public ResponseEntity<StockOrderResponse> getById(@PathVariable String orderId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        var order = stockOrderService.getOrderById(orderId);
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
    public ResponseEntity<StockOrderResponse> cancel(@PathVariable String orderId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        var order = stockOrderService.getOrderById(orderId);
        String userId = jwt.getSubject();
        boolean isAdmin = jwt.getClaimAsMap("realm_access") != null &&
                ((List<?>) ((java.util.Map<?, ?>) jwt.getClaimAsMap("realm_access")).get("roles"))
                        .stream().anyMatch(r -> "ADMIN".equals(r));
        if (!isAdmin && !order.getClientId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var cancelled = stockOrderService.cancelOrder(orderId);
        return ResponseEntity.ok(mapper.toResponse(cancelled));
    }

    @PostMapping("/{orderId}/advance")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Продвинуть статус заказа (MANAGER/ADMIN)")
    public ResponseEntity<StockOrderResponse> advance(@PathVariable String orderId) {
        var advanced = stockOrderService.advanceOrder(orderId);
        return ResponseEntity.ok(mapper.toResponse(advanced));
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Заказы клиента (только MANAGER/ADMIN)")
    public ResponseEntity<List<StockOrderResponse>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(mapper.toResponseList(stockOrderService.getClientOrders(clientId)));
    }
}