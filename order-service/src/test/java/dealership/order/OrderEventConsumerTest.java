package dealership.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderRejectedEvent;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.infrastructure.messaging.OrderEventConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderEventConsumerTest {

    @Test
    void storageFailureOnStockRejectionIsPropagatedForKafkaRetry() throws Exception {
        StockOrderService stockOrders = mock(StockOrderService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OrderEventConsumer consumer = new OrderEventConsumer(
                stockOrders, mock(CustomOrderService.class), objectMapper);
        OrderRejectedEvent event = new OrderRejectedEvent();
        event.setOrderId("order-1");
        event.setOrderType("STOCK");
        event.setReason("rejected");
        when(stockOrders.cancelOrder("order-1"))
                .thenThrow(new StorageUnavailableException("storage down", null));

        String message = objectMapper.writeValueAsString(event);

        assertThrows(StorageUnavailableException.class, () -> consumer.handleOrderResponse(message));
    }
}
