package dealership.order.core.application.port.out;

import dealership.order.core.domain.entity.testdrive.TestDriveRequest;

import java.util.List;

public interface TestDriveRequestRepository {
    TestDriveRequest save(TestDriveRequest request);

    TestDriveRequest findById(String id);

    TestDriveRequest findByIdForUpdate(String id);

    List<TestDriveRequest> findAll();

    List<TestDriveRequest> findByCarId(String carId);

    List<TestDriveRequest> findByClientId(String clientId);

    void deleteById(String id);
}
