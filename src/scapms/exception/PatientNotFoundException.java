package scapms.exception;

/** Thrown when an operation refers to a patient ID that is not registered. */
public class PatientNotFoundException extends ClinicException {
    private static final long serialVersionUID = 1L;

    public PatientNotFoundException(String patientId) {
        super("No patient found with ID " + patientId + ".");
    }
}
