package dealership.order.infrastructure.web.controller;

import dealership.order.core.application.service.DemoPaymentService;
import dealership.order.core.domain.enums.DemoPaymentStatus;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.web.dto.request.CreateDemoPaymentRequest;
import dealership.order.infrastructure.web.dto.response.DemoPaymentReceiptResponse;
import dealership.order.infrastructure.web.mapper.DemoPaymentWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Demo Payments", description = "Caller-controlled workflow simulator; never moves real money")
public class DemoPaymentController {
    private final DemoPaymentService paymentService;
    private final DemoPaymentWebMapper mapper;

    public DemoPaymentController(DemoPaymentService paymentService, DemoPaymentWebMapper mapper) {
        this.paymentService = paymentService;
        this.mapper = mapper;
    }

    @PostMapping("/stock/{orderId}/demo-payments")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Simulate a stock-order payment outcome (demo only)")
    public ResponseEntity<DemoPaymentReceiptResponse> startStock(
            @PathVariable UUID orderId,
            @Parameter(required = true, description = "UUID scoped to this order")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody CreateDemoPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return receipt(paymentService.startStockPayment(
                orderId.toString(), jwt.getSubject(), idempotencyKey,
                request == null ? null : request.getOutcome()));
    }

    @PostMapping("/custom/{orderId}/demo-payments")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Simulate a custom-order payment outcome (demo only)")
    public ResponseEntity<DemoPaymentReceiptResponse> startCustom(
            @PathVariable UUID orderId,
            @Parameter(required = true, description = "UUID scoped to this order")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody CreateDemoPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return receipt(paymentService.startCustomPayment(
                orderId.toString(), jwt.getSubject(), idempotencyKey,
                request == null ? null : request.getOutcome()));
    }

    @GetMapping("/stock/{orderId}/demo-payments/{paymentId}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Read a stock-order demo payment receipt")
    public ResponseEntity<DemoPaymentReceiptResponse> getStock(
            @PathVariable UUID orderId, @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return receipt(paymentService.getStockPayment(
                orderId.toString(), paymentId.toString(), jwt.getSubject(),
                canAudit(authentication)));
    }

    @GetMapping("/custom/{orderId}/demo-payments/{paymentId}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Read a custom-order demo payment receipt")
    public ResponseEntity<DemoPaymentReceiptResponse> getCustom(
            @PathVariable UUID orderId, @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return receipt(paymentService.getCustomPayment(
                orderId.toString(), paymentId.toString(), jwt.getSubject(),
                canAudit(authentication)));
    }

    private ResponseEntity<DemoPaymentReceiptResponse> receipt(
            DemoPaymentAttemptJpaEntity payment) {
        DemoPaymentReceiptResponse response = mapper.toResponse(payment);
        return payment.getStatus() == DemoPaymentStatus.PENDING
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    private boolean canAudit(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
