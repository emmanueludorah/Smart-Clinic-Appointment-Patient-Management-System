package scapms.exception;

/** Thrown when a data file cannot be read or contains invalid data. */
public class FileLoadException extends ClinicException {
    private static final long serialVersionUID = 1L;

    public FileLoadException(String message) {
        super(message);
    }

    public FileLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
