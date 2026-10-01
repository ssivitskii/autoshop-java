package dealership.storage.infrastructure.web.mapper;

import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.infrastructure.web.dto.response.SparePartResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SparePartWebMapper {
    SparePartResponse toResponse(SparePart part);

    List<SparePartResponse> toResponseList(List<SparePart> parts);
}
