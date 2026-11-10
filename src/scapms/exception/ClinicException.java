package scapms.exception;

/**
 * Base class for every checked exception raised by SCAPMS.
 * All custom exceptions extend this class so that the GUI can catch a single
 * type and show a meaningful message instead of terminating unexpectedly.
 */
public class ClinicException extends Exception {
    private static final long serialVersionUID = 1L;

    public ClinicException(String message) {
        super(message);
    }

    public ClinicException(String message, Throwable cause) {
        super(message, cause);
    }
}
