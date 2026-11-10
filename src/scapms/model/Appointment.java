package scapms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import scapms.exception.InvalidAppointmentException;
import scapms.util.Validator;

/**
 * A single appointment belonging to one patient (a patient has one or more appointments).
 */
public class Appointment {
    private final String appointmentId;
    private final String patientId;
    private final LocalDate date;
    private final LocalTime time;
    private String doctor;
    private String reason;
    private AppointmentStatus status;
    private boolean urgent;

    public Appointment(String appointmentId, String patientId, LocalDate date, LocalTime time,
                       String doctor, String reason) throws InvalidAppointmentException {
        if (appointmentId == null || appointmentId.trim().isEmpty()) {
            throw new InvalidAppointmentException("Appointment ID is required.");
        }
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new InvalidAppointmentException("An appointment must belong to a patient.");
        }
        if (date == null || time == null) {
            throw new InvalidAppointmentException("Appointment date and time are required.");
        }
        this.appointmentId = appointmentId.trim();
        this.patientId = patientId.trim();
        this.date = date;
        this.time = time;
        this.doctor = doctor == null ? "" : doctor.trim();
        this.reason = reason == null ? "" : reason.trim();
        this.status = AppointmentStatus.PENDING;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public String getPatientId() {
        return patientId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public LocalDateTime getDateTime() {
        return LocalDateTime.of(date, time);
    }

    public String getDoctor() {
        return doctor;
    }

    public void setDoctor(String doctor) {
        this.doctor = doctor == null ? "" : doctor.trim();
    }

    public String getReason() {
        return reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    /** Used when loading files and when undoing a cancel/complete operation. */
    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public boolean isPending() {
        return status == AppointmentStatus.PENDING;
    }

    public void markCompleted() throws InvalidAppointmentException {
        if (status != AppointmentStatus.PENDING) {
            throw new InvalidAppointmentException("Only pending appointments can be marked as completed ("
                    + appointmentId + " is " + status + ").");
        }
        status = AppointmentStatus.COMPLETED;
    }

    public void cancel() throws InvalidAppointmentException {
        if (status != AppointmentStatus.PENDING) {
            throw new InvalidAppointmentException("Only pending appointments can be cancelled ("
                    + appointmentId + " is " + status + ").");
        }
        status = AppointmentStatus.CANCELLED;
    }

    @Override
    public String toString() {
        return appointmentId + "  " + date.format(Validator.DATE_FORMAT) + " " + time.format(Validator.TIME_FORMAT)
                + (doctor.isEmpty() ? "" : "  with " + doctor) + "  (" + status + ")";
    }
}
