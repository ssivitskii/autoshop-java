package dealership.order.infrastructure.grpc;

import dealership.common.grpc.CarDto;
import dealership.common.grpc.CarGrpcServiceGrpc;
import dealership.common.grpc.GetAvailableCarsRequest;
import dealership.common.grpc.GetAvailableCarsResponse;
import dealership.common.grpc.GetCarByIdRequest;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(CarGrpcClient.class);

    @GrpcClient("storage-service")
    private CarGrpcServiceGrpc.CarGrpcServiceBlockingStub carStub;

    public List<CarDto> getAvailableCars() {
        log.info("gRPC Client: requesting available cars from StorageService");
        try {
            GetAvailableCarsResponse response = carStub.getAvailableCars(
                    GetAvailableCarsRequest.newBuilder().build());
            log.info("gRPC Client: received {} cars", response.getCarsCount());
            return response.getCarsList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC Client: failed to get available cars, status={}", e.getStatus(), e);
            throw e;
        }
    }

    public CarDto getCarById(String id) {
        log.info("gRPC Client: requesting car id={} from StorageService", id);
        try {
            CarDto car = carStub.getCarById(
                    GetCarByIdRequest.newBuilder().setId(id).build());
            log.info("gRPC Client: received car id={}", id);
            return car;
        } catch (StatusRuntimeException e) {
            log.error("gRPC Client: failed to get car id={}, status={}", id, e.getStatus(), e);
            throw e;
        }
    }
}
