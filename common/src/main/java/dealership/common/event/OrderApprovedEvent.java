package dealership.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderApprovedEvent {
    private String orderId;
    private String orderType;
    private String traceId;
    private String assemblyOrderId;
}