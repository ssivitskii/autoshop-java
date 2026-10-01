package dealership.order.core.application.port.out;

import dealership.order.core.domain.entity.order.StockOrder;

import java.util.List;

public interface StockOrderRepository {
    StockOrder save(StockOrder order);

    StockOrder findById(String id);

    List<StockOrder> findAll();

    List<StockOrder> findByClientId(String clientId);

    void deleteById(String id);
}