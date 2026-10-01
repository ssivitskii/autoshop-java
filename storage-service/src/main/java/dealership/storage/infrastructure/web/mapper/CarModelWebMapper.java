package dealership.storage.infrastructure.web.mapper;

import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.infrastructure.web.dto.response.CarModelResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CarModelWebMapper {
    CarModelResponse toResponse(CarModel model);

    List<CarModelResponse> toResponseList(List<CarModel> models);
}
