package dealership.order.core.application.port.out;

import dealership.order.core.domain.entity.testdrive.TestDriveRequest;

import java.util.List;

public interface TestDriveRequestRepository {
    TestDriveRequest save(TestDriveRequest request);

    TestDriveRequest findById(String id);

    List<TestDriveRequest> findAll();

    List<TestDriveRequest> findByCarId(String carId);

    void deleteById(String id);
}
