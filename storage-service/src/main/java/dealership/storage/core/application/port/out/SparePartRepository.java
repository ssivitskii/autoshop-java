package dealership.storage.core.application.port.out;

import dealership.storage.core.domain.entity.part.SparePart;

import java.util.List;

public interface SparePartRepository {
    SparePart save(SparePart part);

    SparePart findById(String id);

    List<SparePart> findAll();

    void deleteById(String id);
}
