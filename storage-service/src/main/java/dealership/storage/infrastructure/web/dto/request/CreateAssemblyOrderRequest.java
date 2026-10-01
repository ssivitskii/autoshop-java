package dealership.storage.infrastructure.web.dto.request;

import lombok.Data;

@Data
public class CreateAssemblyOrderRequest {
    private String sourceOrderId;
    private String orderType;
    private String traceId;
}
