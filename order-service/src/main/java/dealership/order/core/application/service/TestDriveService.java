package dealership.order.core.application.service;

import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

@Service
public class TestDriveService {
    private final TestDriveRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final TestDriveCarGateway carGateway;
    private final Clock clock;

    public TestDriveService(TestDriveRequestRepository requestRepository,
                            UserRepository userRepository,
                            TestDriveCarGateway carGateway,
                            Clock clock) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.carGateway = carGateway;
        this.clock = clock;
    }

    public TestDriveRequest createRequest(String clientId, String carId, LocalDateTime scheduledAt) {
        requireFuture(scheduledAt);
        TestDriveCarGateway.TestDriveCar car = carGateway.get(carId);
        if (!car.available() || !car.availableForTestDrive()) {
            throw new TestDriveConflictException("Автомобиль недоступен для тест-драйва");
        }
        requireFuture(scheduledAt);
        TestDriveRequest request = new TestDriveRequest(clientId, car.id(), scheduledAt);
        return requestRepository.save(request);
    }

    private void requireFuture(LocalDateTime scheduledAt) {
        if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now(clock))) {
            throw new DomainValidationException("Время тест-драйва должно быть в будущем");
        }
    }

    public List<TestDriveRequest> getAllRequests() {
        return requestRepository.findAll();
    }

    public List<TestDriveRequest> getRequestsByCarId(String carId) {
        return requestRepository.findByCarId(carId);
    }
}
