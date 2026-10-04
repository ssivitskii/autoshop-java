package dealership.order.core.application.service;

import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

@Service
public class TestDriveService {
    private static final Duration TEST_DRIVE_DURATION = Duration.ofHours(1);

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

    public List<TestDriveRequest> getClientRequests(String clientId) {
        return requestRepository.findByClientId(clientId);
    }

    @Transactional
    public TestDriveRequest approveRequest(String requestId) {
        TestDriveRequest request = requestRepository.findByIdForUpdate(requestId);
        if (request.getStatus() == dealership.order.core.domain.enums.TestDriveStatus.APPROVED) {
            return request;
        }
        if (!LocalDateTime.now(clock).isBefore(request.getRequestedDateTime())) {
            throw new TestDriveConflictException(
                    "Нельзя одобрить тест-драйв после начала запланированного времени");
        }
        request.approve();
        return requestRepository.save(request);
    }

    @Transactional
    public TestDriveRequest completeRequest(String requestId) {
        TestDriveRequest request = requestRepository.findByIdForUpdate(requestId);
        if (request.getStatus() == dealership.order.core.domain.enums.TestDriveStatus.COMPLETED) {
            return request;
        }
        LocalDateTime availableAt = request.getRequestedDateTime().plus(TEST_DRIVE_DURATION);
        if (LocalDateTime.now(clock).isBefore(availableAt)) {
            throw new TestDriveConflictException(
                    "Нельзя завершить тест-драйв до окончания запланированного времени");
        }
        request.complete();
        return requestRepository.save(request);
    }

    @Transactional
    public TestDriveRequest cancelRequest(String requestId, String actorClientId, boolean staff) {
        TestDriveRequest request = requestRepository.findByIdForUpdate(requestId);
        if (!staff && !request.getClientId().equals(actorClientId)) {
            throw new AccessDeniedException("Заявка принадлежит другому клиенту");
        }
        if (request.getStatus() == dealership.order.core.domain.enums.TestDriveStatus.CANCELLED) {
            return request;
        }
        request.cancel();
        return requestRepository.save(request);
    }
}
