package dealership.order.infrastructure.web.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTestDriveRequest {
    private String clientId;
    private String carId;
    private LocalDateTime scheduledAt;
}