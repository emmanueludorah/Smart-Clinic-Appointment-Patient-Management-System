package scapms.util;

import scapms.model.Appointment;
import scapms.model.ClinicDatabase;
import scapms.model.PatientRecord;

/**
 * Generates unique sequential IDs: patients P0001, P0002 ... and appointments A0001, A0002 ...
 */
public class IDGenerator {

    private int nextPatientNumber = 1;
    private int nextAppointmentNumber = 1;

    /** @return the next free patient ID and advances the counter. */
    public String nextPatientId(ClinicDatabase database) {
        String id = peekPatientId(database);
        nextPatientNumber = numberOf(id) + 1;
        return id;
    }

    /** @return the next free patient ID without advancing the counter. */
    public String peekPatientId(ClinicDatabase database) {
        int n = nextPatientNumber;
        String id = String.format("P%04d", n);
        while (database.containsId(id)) {
            n++;
            id = String.format("P%04d", n);
        }
        return id;
    }

    public String peekAppointmentId() {
        return String.format("A%04d", nextAppointmentNumber);
    }

    /** Call after an appointment using {@link #peekAppointmentId()} was successfully booked. */
    public void advanceAppointmentId() {
        nextAppointmentNumber++;
    }

    /** Moves both counters past the highest IDs found in the database (after loading). */
    public void synchronise(ClinicDatabase database) {
        int maxPatient = 0;
        int maxAppointment = 0;
        for (PatientRecord p : database.getPatients()) {
            maxPatient = Math.max(maxPatient, numberOf(p.getPatientId()));
            for (Appointment a : p.getAppointments()) {
                maxAppointment = Math.max(maxAppointment, numberOf(a.getAppointmentId()));
            }
        }
        nextPatientNumber = maxPatient + 1;
        nextAppointmentNumber = maxAppointment + 1;
    }

    public void reset() {
        nextPatientNumber = 1;
        nextAppointmentNumber = 1;
    }

    private static int numberOf(String id) {
        try {
            return Integer.parseInt(id.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
