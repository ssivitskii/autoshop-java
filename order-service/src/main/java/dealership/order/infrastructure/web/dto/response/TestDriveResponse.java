package dealership.order.infrastructure.web.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestDriveResponse {
    private String id;
    private String clientId;
    private String carId;
    private LocalDateTime requestedDateTime;
    private String status;
}