package scapms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidPatientException;
import scapms.util.Validator;

/**
 * Abstract base class for every patient record.
 *
 * <p>Holds the information common to all patients (ID, name, age, contact details)
 * and the patient's appointments. Data is private and only changed through
 * validating setters (encapsulation). Sub-classes must implement the pure virtual
 * (abstract) methods to supply behaviour appropriate to their patient type.</p>
 */
public abstract class PatientRecord {

    public static final LocalTime CLINIC_OPENS = LocalTime.of(8, 0);
    public static final LocalTime CLINIC_CLOSES = LocalTime.of(17, 30);

    private final String patientId;
    private String name;
    private int age;
    private String phone;
    private String email;
    private final LocalDate registrationDate;

    /** Association: a patient is linked to zero or more appointments. */
    private final List<Appointment> appointments = new ArrayList<>();

    protected PatientRecord(String patientId, String name, int age, String phone, String email,
                            LocalDate registrationDate) throws InvalidPatientException {
        this.patientId = Validator.requirePatientId(patientId);
        setName(name);
        setAge(age);
        setPhone(phone);
        setEmail(email);
        this.registrationDate = registrationDate == null ? LocalDate.now() : registrationDate;
    }

    // ============================================================ abstract API
    // ("pure virtual functions" – every subclass must provide its own version)

    /** @return the category of this patient. */
    public abstract PatientCategory getCategory();

    /** @return triage priority, 1 = most urgent. */
    public abstract int getPriorityLevel();

    /** @return text shown for the priority, e.g. "High Priority". */
    public abstract String getPriorityLabel();

    /** @return the consultation fee, calculated differently for each patient type. */
    public abstract double calculateConsultationFee();

    /** @return a multi-line description of the data specific to this patient type. */
    public abstract String getTypeSpecificDetails();

    /** @return type-specific attributes (key -> value) used for saving and editing. */
    public abstract Map<String, String> getExtraAttributes();

    // =========================================================== hook methods

    /** Patients must normally book within clinic hours; sub-classes may relax this. */
    protected boolean requiresClinicHours() {
        return true;
    }

    /** @return a sensible default date to offer when booking this patient. */
    public LocalDate suggestAppointmentDate(LocalDate today) {
        return today.plusDays(1);
    }

    /**
     * Template method: builds the full description using the abstract methods,
     * so the output automatically adapts to the run-time type of the object.
     */
    public String getFullDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("Patient ID      : ").append(patientId).append('\n');
        sb.append("Name            : ").append(name).append('\n');
        sb.append("Age             : ").append(age).append('\n');
        sb.append("Phone           : ").append(phone).append('\n');
        sb.append("Email           : ").append(email.isEmpty() ? "-" : email).append('\n');
        sb.append("Registered      : ").append(registrationDate.format(Validator.DATE_FORMAT)).append('\n');
        sb.append("Category        : ").append(getCategory()).append('\n');
        sb.append("Priority        : ").append(getPriorityLabel()).append(" (level ").append(getPriorityLevel()).append(")\n");
        sb.append(String.format("Consultation fee: %,.2f%n", calculateConsultationFee()));
        sb.append("\n--- ").append(getCategory()).append(" details ---\n");
        sb.append(getTypeSpecificDetails()).append('\n');
        sb.append("\n--- Appointments (").append(appointments.size()).append(") ---\n");
        sb.append("Pending: ").append(countAppointments(AppointmentStatus.PENDING))
          .append("   Completed: ").append(countAppointments(AppointmentStatus.COMPLETED))
          .append("   Cancelled: ").append(countAppointments(AppointmentStatus.CANCELLED)).append('\n');
        for (Appointment a : appointments) {
            sb.append("  ").append(a).append('\n');
        }
        return sb.toString();
    }

    // ===================================================== getters / setters

    public String getPatientId() {
        return patientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) throws InvalidPatientException {
        this.name = Validator.requireName(name);
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) throws InvalidPatientException {
        this.age = Validator.requireValidAge(age);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) throws InvalidPatientException {
        this.phone = Validator.requirePhone(phone);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) throws InvalidPatientException {
        this.email = Validator.optionalEmail(email);
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    // ============================================== appointment association

    public List<Appointment> getAppointments() {
        return Collections.unmodifiableList(appointments);
    }

    /** Adds an appointment without applying booking rules (used for loading and undo). */
    public void attachAppointment(Appointment appointment) {
        attachAppointment(appointments.size(), appointment);
    }

    public void attachAppointment(int index, Appointment appointment) {
        if (appointment == null || !appointment.getPatientId().equals(patientId)) {
            throw new IllegalArgumentException("Appointment does not belong to patient " + patientId);
        }
        int safeIndex = Math.max(0, Math.min(index, appointments.size()));
        appointments.add(safeIndex, appointment);
    }

    /** Removes an appointment and returns the index it occupied, or -1 if absent. */
    public int detachAppointment(String appointmentId) {
        for (int i = 0; i < appointments.size(); i++) {
            if (appointments.get(i).getAppointmentId().equals(appointmentId)) {
                appointments.remove(i);
                return i;
            }
        }
        return -1;
    }

    public Appointment findAppointment(String appointmentId) {
        for (Appointment a : appointments) {
            if (a.getAppointmentId().equals(appointmentId)) {
                return a;
            }
        }
        return null;
    }

    /** Makes this record share the appointment list of another (used when a record is edited). */
    public void replaceAppointmentsWith(PatientRecord other) {
        List<Appointment> copy = new ArrayList<>(other.appointments);
        appointments.clear();
        appointments.addAll(copy);
    }

    public int countAppointments(AppointmentStatus status) {
        int count = 0;
        for (Appointment a : appointments) {
            if (a.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    public boolean hasPendingAppointments() {
        return countAppointments(AppointmentStatus.PENDING) > 0;
    }

    /** @return the earliest pending appointment, or {@code null} if none. */
    public Appointment getNextPendingAppointment() {
        Appointment earliest = null;
        for (Appointment a : appointments) {
            if (a.isPending() && (earliest == null || a.getDateTime().isBefore(earliest.getDateTime()))) {
                earliest = a;
            }
        }
        return earliest;
    }

    public LocalDateTime getNextAppointmentDateTime() {
        Appointment next = getNextPendingAppointment();
        return next == null ? null : next.getDateTime();
    }

    /**
     * Rules shared by every patient type; sub-classes call this before adding their own rules.
     */
    protected void validateNewAppointment(Appointment appointment) throws InvalidAppointmentException {
        if (appointment == null) {
            throw new InvalidAppointmentException("No appointment information supplied.");
        }
        if (!appointment.getPatientId().equals(patientId)) {
            throw new InvalidAppointmentException("Appointment belongs to a different patient.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (appointment.getDate().isBefore(now.toLocalDate())) {
            throw new InvalidAppointmentException("Appointment date cannot be in the past.");
        }
        if (appointment.getDateTime().isBefore(now)) {
            throw new InvalidAppointmentException("That appointment time has already passed today.");
        }
        if (requiresClinicHours()
                && (appointment.getTime().isBefore(CLINIC_OPENS) || appointment.getTime().isAfter(CLINIC_CLOSES))) {
            throw new InvalidAppointmentException("Appointments must be booked between "
                    + CLINIC_OPENS + " and " + CLINIC_CLOSES + " (clinic hours).");
        }
        for (Appointment existing : appointments) {
            if (existing.isPending() && existing.getDateTime().equals(appointment.getDateTime())) {
                throw new InvalidAppointmentException(name + " already has an appointment on "
                        + appointment.getDate() + " at " + appointment.getTime() + ".");
            }
        }
    }

    /** Shared cancellation logic used by the Appointable implementations. */
    protected void cancelOwnAppointment(String appointmentId) throws InvalidAppointmentException {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment == null) {
            throw new InvalidAppointmentException("Appointment " + appointmentId
                    + " was not found for patient " + patientId + ".");
        }
        appointment.cancel();
    }

    /**
     * Compares patient IDs numerically (P0009 &lt; P0010 &lt; P10000) so that the
     * sort order and binary search agree.
     */
    public static int compareIds(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    @Override
    public String toString() {
        return patientId + " - " + name;
    }
}
