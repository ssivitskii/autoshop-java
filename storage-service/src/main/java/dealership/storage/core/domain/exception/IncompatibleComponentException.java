package dealership.storage.core.domain.exception;

public class IncompatibleComponentException extends RuntimeException {
    public IncompatibleComponentException(String message) {
        super(message);
    }
}