package dealership.order.core.application.service;

import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TestDriveService {
    private final TestDriveRequestRepository requestRepository;
    private final UserRepository userRepository;

    public TestDriveService(TestDriveRequestRepository requestRepository,
                            UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    public TestDriveRequest createRequest(String clientId, String carId, LocalDateTime scheduledAt) {
        TestDriveRequest request = new TestDriveRequest(clientId, carId, scheduledAt);
        return requestRepository.save(request);
    }

    public List<TestDriveRequest> getAllRequests() {
        return requestRepository.findAll();
    }

    public List<TestDriveRequest> getRequestsByCarId(String carId) {
        return requestRepository.findByCarId(carId);
    }
}