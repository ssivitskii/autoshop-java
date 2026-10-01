package dealership.storage.infrastructure.web.dto.response;

import lombok.Data;

@Data
public class AssemblyOrderResponse {
    private String id;
    private String sourceOrderId;
    private String orderType;
    private String traceId;
    private String status;
}
