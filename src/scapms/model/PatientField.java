package scapms.model;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Fields that patient records can be sorted (and binary-searched) by.
 * Each field supplies the comparison rule used by the hand-written sorting algorithms.
 */
public enum PatientField {
    NAME("Patient Name"),
    ID("Patient ID"),
    AGE("Age"),
    APPOINTMENT_DATE("Next Appointment Date");

    private final String label;

    PatientField(String label) {
        this.label = label;
    }

    /** @return the comparison rule for this field (ascending order). */
    public Comparator<PatientRecord> comparator() {
        switch (this) {
            case NAME:
                return (a, b) -> {
                    int c = a.getName().compareToIgnoreCase(b.getName());
                    return c != 0 ? c : PatientRecord.compareIds(a.getPatientId(), b.getPatientId());
                };
            case ID:
                return (a, b) -> PatientRecord.compareIds(a.getPatientId(), b.getPatientId());
            case AGE:
                return (a, b) -> Integer.compare(a.getAge(), b.getAge());
            case APPOINTMENT_DATE:
            default:
                return (a, b) -> {
                    LocalDateTime x = a.getNextAppointmentDateTime();
                    LocalDateTime y = b.getNextAppointmentDateTime();
                    if (x == null && y == null) {
                        return 0;
                    }
                    if (x == null) {
                        return 1;  // patients without a pending appointment go last
                    }
                    if (y == null) {
                        return -1;
                    }
                    return x.compareTo(y);
                };
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
