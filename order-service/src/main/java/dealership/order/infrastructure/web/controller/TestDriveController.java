package dealership.order.infrastructure.web.controller;

import dealership.order.core.application.service.TestDriveService;
import dealership.order.infrastructure.web.dto.request.CreateTestDriveRequest;
import dealership.order.infrastructure.web.dto.response.TestDriveResponse;
import dealership.order.infrastructure.web.mapper.TestDriveWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test-drives")
@Tag(name = "Test Drives", description = "Заявки на тест-драйв")
public class TestDriveController {
    private final TestDriveService testDriveService;
    private final TestDriveWebMapper mapper;

    public TestDriveController(TestDriveService testDriveService, TestDriveWebMapper mapper) {
        this.testDriveService = testDriveService;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Записаться на тест-драйв")
    public ResponseEntity<TestDriveResponse> create(@RequestBody CreateTestDriveRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        var testDrive = testDriveService.createRequest(userId,
                request.getCarId(),
                request.getScheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(testDrive));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Все заявки на тест-драйв (MANAGER/ADMIN)")
    public ResponseEntity<List<TestDriveResponse>> getAll() {
        return ResponseEntity.ok(mapper.toResponseList(testDriveService.getAllRequests()));
    }

    @GetMapping("/car/{carId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Заявки на тест-драйв по автомобилю (MANAGER/ADMIN)")
    public ResponseEntity<List<TestDriveResponse>> getByCarId(@PathVariable String carId) {
        return ResponseEntity.ok(mapper.toResponseList(testDriveService.getRequestsByCarId(carId)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Мои заявки на тест-драйв")
    public ResponseEntity<List<TestDriveResponse>> getMine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toResponseList(
                testDriveService.getClientRequests(jwt.getSubject())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Отменить заявку (владелец, MANAGER или ADMIN)")
    public ResponseEntity<TestDriveResponse> cancel(@PathVariable String id,
                                                     @AuthenticationPrincipal Jwt jwt,
                                                     Authentication authentication) {
        return ResponseEntity.ok(mapper.toResponse(testDriveService.cancelRequest(
                id, jwt.getSubject(), isStaff(authentication))));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Одобрить заявку (MANAGER/ADMIN)")
    public ResponseEntity<TestDriveResponse> approve(@PathVariable String id) {
        return ResponseEntity.ok(mapper.toResponse(testDriveService.approveRequest(id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Завершить тест-драйв (MANAGER/ADMIN)")
    public ResponseEntity<TestDriveResponse> complete(@PathVariable String id) {
        return ResponseEntity.ok(mapper.toResponse(testDriveService.completeRequest(id)));
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
