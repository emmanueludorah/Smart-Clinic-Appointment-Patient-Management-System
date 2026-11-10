package scapms.exception;

/** Thrown when a patient's age is not a whole number between 0 and 120. */
public class InvalidAgeException extends InvalidPatientException {
    private static final long serialVersionUID = 1L;

    public InvalidAgeException(String message) {
        super(message);
    }
}
