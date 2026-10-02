package dealership.order.core.application.port.out;

public interface TestDriveCarGateway {
    TestDriveCar get(String carId);

    record TestDriveCar(String id, boolean available, boolean availableForTestDrive) {
    }
}
