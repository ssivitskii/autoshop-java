package dealership.common.event;

public class PermanentMessageException extends RuntimeException {
    public PermanentMessageException(String message) {
        super(message);
    }

    public PermanentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
