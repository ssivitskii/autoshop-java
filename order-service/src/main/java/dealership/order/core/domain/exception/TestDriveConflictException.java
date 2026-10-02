package dealership.order.core.domain.exception;

public class TestDriveConflictException extends RuntimeException {
    public TestDriveConflictException(String message) {
        super(message);
    }

    public TestDriveConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
