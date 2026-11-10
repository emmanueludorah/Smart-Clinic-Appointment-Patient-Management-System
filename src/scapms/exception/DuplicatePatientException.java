package scapms.exception;

/** Thrown when a patient is registered with an ID that already exists. */
public class DuplicatePatientException extends ClinicException {
    private static final long serialVersionUID = 1L;
    private final String patientId;

    public DuplicatePatientException(String patientId) {
        super("Patient ID " + patientId + " already exists.");
        this.patientId = patientId;
    }

    public String getPatientId() {
        return patientId;
    }
}
