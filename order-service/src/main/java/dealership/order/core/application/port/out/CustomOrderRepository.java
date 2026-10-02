package dealership.order.core.application.port.out;

import dealership.order.core.domain.entity.order.CustomOrder;

import java.util.List;

public interface CustomOrderRepository {
    CustomOrder save(CustomOrder order);

    CustomOrder findById(String id);

    CustomOrder findByIdForUpdate(String id);

    List<CustomOrder> findAll();

    List<CustomOrder> findByClientId(String clientId);

    void deleteById(String id);
}
