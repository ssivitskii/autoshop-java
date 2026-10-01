package dealership.order.infrastructure.web.mapper;

import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.infrastructure.web.dto.response.StockOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StockOrderWebMapper {
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    StockOrderResponse toResponse(StockOrder order);

    List<StockOrderResponse> toResponseList(List<StockOrder> orders);
}
