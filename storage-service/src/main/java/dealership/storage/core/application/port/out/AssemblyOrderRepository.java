package dealership.storage.core.application.port.out;

import dealership.storage.core.domain.entity.assembly.AssemblyOrder;

import java.util.List;

public interface AssemblyOrderRepository {
    AssemblyOrder save(AssemblyOrder order);

    AssemblyOrder findById(String id);

    List<AssemblyOrder> findAll();

    void deleteById(String id);

    boolean existsBySourceOrderId(String sourceOrderId);
}
