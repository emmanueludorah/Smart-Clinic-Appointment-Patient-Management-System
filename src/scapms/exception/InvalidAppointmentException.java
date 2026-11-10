package scapms.exception;

/** Thrown when appointment data is invalid or an appointment rule is broken. */
public class InvalidAppointmentException extends ClinicException {
    private static final long serialVersionUID = 1L;

    public InvalidAppointmentException(String message) {
        super(message);
    }
}
