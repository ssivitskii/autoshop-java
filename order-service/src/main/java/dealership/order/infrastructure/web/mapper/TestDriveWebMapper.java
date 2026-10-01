package dealership.order.infrastructure.web.mapper;

import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.infrastructure.web.dto.response.TestDriveResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TestDriveWebMapper {

    @Mapping(target = "status", expression = "java(request.getStatus().name())")
    TestDriveResponse toResponse(TestDriveRequest request);

    List<TestDriveResponse> toResponseList(List<TestDriveRequest> requests);
}
