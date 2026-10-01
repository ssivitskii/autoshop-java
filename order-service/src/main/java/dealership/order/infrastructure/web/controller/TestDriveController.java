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
}