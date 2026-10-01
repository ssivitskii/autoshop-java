package dealership.order.infrastructure.web.mapper;

import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.infrastructure.web.dto.response.CustomOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomOrderWebMapper {

    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    @Mapping(target = "selectedVariants", expression = "java(order.getConfiguration().getSelectedVariants())")
    CustomOrderResponse toResponse(CustomOrder order);

    List<CustomOrderResponse> toResponseList(List<CustomOrder> orders);
}
