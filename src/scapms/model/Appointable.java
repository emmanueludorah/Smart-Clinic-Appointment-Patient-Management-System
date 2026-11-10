package scapms.model;

import java.util.List;

import scapms.exception.InvalidAppointmentException;

/**
 * Interface implemented by every patient type that can be scheduled for appointments.
 * Each implementing class applies its own booking rules.
 */
public interface Appointable {

    /** Books the appointment for this patient, applying type-specific rules. */
    void scheduleAppointment(Appointment appointment) throws InvalidAppointmentException;

    /** Cancels one of this patient's pending appointments. */
    void cancelAppointment(String appointmentId) throws InvalidAppointmentException;

    /** @return a read-only view of this patient's appointments. */
    List<Appointment> getAppointments();

    /** @return a human readable description of this patient's booking rules. */
    String getBookingRule();
}
