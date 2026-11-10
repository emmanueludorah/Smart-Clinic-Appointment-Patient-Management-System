package scapms.exception;

/** Thrown when patient information (name, phone, type-specific data...) is invalid. */
public class InvalidPatientException extends ClinicException {
    private static final long serialVersionUID = 1L;

    public InvalidPatientException(String message) {
        super(message);
    }
}
