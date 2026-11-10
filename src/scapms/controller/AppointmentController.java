package scapms.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import scapms.exception.ClinicException;
import scapms.exception.InvalidAppointmentException;
import scapms.model.Appointable;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/**
 * Business logic for appointments: scheduling, cancelling, completing and removing.
 * Every change is recorded on the undo stack.
 */
public class AppointmentController {

    private final ClinicManager manager;

    AppointmentController(ClinicManager manager) {
        this.manager = manager;
    }

    public Appointment scheduleAppointment(String patientId, String dateText, String timeText,
                                           String doctor, String reason) throws ClinicException {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new InvalidAppointmentException("Please select a patient.");
        }
        PatientRecord patient = manager.requirePatient(patientId);
        LocalDate date = Validator.parseAppointmentDate(dateText);
        LocalTime time = Validator.parseAppointmentTime(timeText);
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidAppointmentException("Please enter the reason for the appointment.");
        }
        if (reason.trim().length() > 100) {
            throw new InvalidAppointmentException("Reason must be 100 characters or fewer.");
        }
        if (!(patient instanceof Appointable)) {
            throw new InvalidAppointmentException(patient.getCategory() + " patients cannot be scheduled for appointments.");
        }
        Appointable appointable = (Appointable) patient;
        Appointment appointment = new Appointment(manager.getIdGenerator().peekAppointmentId(),
                patient.getPatientId(), date, time, doctor, reason);

        appointable.scheduleAppointment(appointment);     // polymorphic: subclass booking rules apply
        manager.getIdGenerator().advanceAppointmentId();

        final String pid = patient.getPatientId();
        final String aid = appointment.getAppointmentId();
        manager.getUndoManager().record(new AdminOperation(AdminOperation.Type.SCHEDULE_APPOINTMENT,
                "Scheduled " + aid + " for " + patient.getName(),
                () -> manager.requirePatient(pid).detachAppointment(aid)));
        manager.markModified();
        return appointment;
    }

    public void cancelAppointment(String appointmentId) throws ClinicException {
        PatientRecord owner = findOwner(appointmentId);
        Appointment appointment = owner.findAppointment(appointmentId);
        if (!(owner instanceof Appointable)) {
            throw new InvalidAppointmentException("This patient type does not support appointments.");
        }
        ((Appointable) owner).cancelAppointment(appointmentId);
        manager.getUndoManager().record(new AdminOperation(AdminOperation.Type.CANCEL_APPOINTMENT,
                "Cancelled " + appointmentId + " for " + owner.getName(),
                () -> appointment.setStatus(AppointmentStatus.PENDING)));
        manager.markModified();
    }

    public void completeAppointment(String appointmentId) throws ClinicException {
        PatientRecord owner = findOwner(appointmentId);
        Appointment appointment = owner.findAppointment(appointmentId);
        appointment.markCompleted();
        manager.getUndoManager().record(new AdminOperation(AdminOperation.Type.COMPLETE_APPOINTMENT,
                "Completed " + appointmentId + " for " + owner.getName(),
                () -> appointment.setStatus(AppointmentStatus.PENDING)));
        manager.markModified();
    }

    /** Administrative removal of an appointment record (any status). */
    public void removeAppointment(String appointmentId) throws ClinicException {
        PatientRecord owner = findOwner(appointmentId);
        Appointment appointment = owner.findAppointment(appointmentId);
        int index = owner.detachAppointment(appointmentId);
        final String pid = owner.getPatientId();
        manager.getUndoManager().record(new AdminOperation(AdminOperation.Type.REMOVE_APPOINTMENT,
                "Removed " + appointmentId + " of " + owner.getName(),
                () -> manager.requirePatient(pid).attachAppointment(index, appointment)));
        manager.markModified();
    }

    /** @return the patient that owns the appointment. */
    public PatientRecord findOwner(String appointmentId) throws InvalidAppointmentException {
        if (appointmentId == null || appointmentId.trim().isEmpty()) {
            throw new InvalidAppointmentException("Please select an appointment first.");
        }
        for (PatientRecord p : manager.getDatabase().getPatients()) {
            if (p.findAppointment(appointmentId) != null) {
                return p;
            }
        }
        throw new InvalidAppointmentException("Appointment " + appointmentId + " was not found.");
    }

    /** @return all appointments (optionally filtered by status) in date/time order. */
    public List<Appointment> getAppointments(AppointmentStatus statusFilter) {
        List<Appointment> list = new ArrayList<>();
        for (Appointment a : manager.getDatabase().getAllAppointments()) {
            if (statusFilter == null || a.getStatus() == statusFilter) {
                list.add(a);
            }
        }
        sortByDateTime(list);
        return list;
    }

    public List<Appointment> getAppointmentsOn(LocalDate date) {
        List<Appointment> list = new ArrayList<>();
        for (Appointment a : manager.getDatabase().getAllAppointments()) {
            if (a.getDate().equals(date)) {
                list.add(a);
            }
        }
        sortByDateTime(list);
        return list;
    }

    /** Pending appointments starting within the next {@code minutesAhead} minutes. */
    public List<Appointment> getUpcomingAppointments(LocalDateTime from, int minutesAhead) {
        LocalDateTime until = from.plusMinutes(minutesAhead);
        List<Appointment> list = new ArrayList<>();
        for (Appointment a : manager.getDatabase().getAllAppointments()) {
            LocalDateTime when = a.getDateTime();
            if (a.isPending() && !when.isBefore(from) && !when.isAfter(until)) {
                list.add(a);
            }
        }
        sortByDateTime(list);
        return list;
    }

    /** @return today's earliest pending appointment for a patient, or null. */
    public Appointment findTodaysPendingAppointment(String patientId) {
        PatientRecord p = manager.findPatient(patientId);
        if (p == null) {
            return null;
        }
        Appointment best = null;
        LocalDate today = LocalDate.now();
        for (Appointment a : p.getAppointments()) {
            if (a.isPending() && a.getDate().equals(today)
                    && (best == null || a.getTime().isBefore(best.getTime()))) {
                best = a;
            }
        }
        return best;
    }

    /** Hand-written insertion sort by date/time (stable; lists are small and mostly ordered). */
    public static void sortByDateTime(List<Appointment> list) {
        for (int i = 1; i < list.size(); i++) {
            Appointment key = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).getDateTime().isAfter(key.getDateTime())) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }
}
