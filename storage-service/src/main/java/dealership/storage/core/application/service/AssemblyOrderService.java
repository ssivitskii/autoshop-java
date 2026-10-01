package dealership.storage.core.application.service;

import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssemblyOrderService {

    private final AssemblyOrderRepository repository;

    public AssemblyOrderService(AssemblyOrderRepository repository) {
        this.repository = repository;
    }

    public AssemblyOrder create(AssemblyOrder order) {
        return repository.save(order);
    }

    public AssemblyOrder getById(String id) {
        return repository.findById(id);
    }

    public List<AssemblyOrder> getAll() {
        return repository.findAll();
    }

    public AssemblyOrder update(String id, AssemblyOrder updated) {
        repository.findById(id);
        return repository.save(updated);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public boolean existsBySourceOrderId(String sourceOrderId) {
        return repository.existsBySourceOrderId(sourceOrderId);
    }
}
