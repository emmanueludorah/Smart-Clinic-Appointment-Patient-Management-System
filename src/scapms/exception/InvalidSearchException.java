package scapms.exception;

/** Thrown when a search keyword or search configuration is invalid. */
public class InvalidSearchException extends ClinicException {
    private static final long serialVersionUID = 1L;

    public InvalidSearchException(String message) {
        super(message);
    }
}
