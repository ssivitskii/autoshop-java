package dealership.order.infrastructure.persistence.mapper;

import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestDriveRequestPersistenceMapper {
    public TestDriveRequest toDomain(TestDriveRequestJpaEntity entity) {
        return new TestDriveRequest(entity.getId().toString(), entity.getClientId(), entity.getCarId(), entity.getRequestedDateTime(), entity.getStatus());
    }

    public TestDriveRequestJpaEntity toJpa(TestDriveRequest domain) {
        TestDriveRequestJpaEntity entity = new TestDriveRequestJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setClientId(domain.getClientId());
        entity.setCarId(domain.getCarId());
        entity.setRequestedDateTime(domain.getRequestedDateTime());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
