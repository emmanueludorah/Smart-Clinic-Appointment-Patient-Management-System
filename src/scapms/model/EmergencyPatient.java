package scapms.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidPatientException;
import scapms.util.Validator;

/**
 * An urgent patient. Highest priority, may be booked at any hour, must be seen
 * within MAX_DAYS_AHEAD days, and every appointment is flagged as urgent.
 */
public class EmergencyPatient extends PatientRecord implements Appointable {

    public static final int MAX_DAYS_AHEAD = 3;
    private static final String[] SEVERITY_NAMES = {"", "Minor", "Moderate", "Serious", "Severe", "Critical"};

    private int severityLevel;   // 1 (minor) .. 5 (critical)
    private String condition;

    public EmergencyPatient(String patientId, String name, int age, String phone, String email,
                            LocalDate registrationDate, int severityLevel, String condition)
            throws InvalidPatientException {
        super(patientId, name, age, phone, email, registrationDate);
        setSeverityLevel(severityLevel);
        setCondition(condition);
    }

    public int getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(int severityLevel) throws InvalidPatientException {
        if (severityLevel < 1 || severityLevel > 5) {
            throw new InvalidPatientException("Severity level must be between 1 (minor) and 5 (critical).");
        }
        this.severityLevel = severityLevel;
    }

    public String getSeverityDescription() {
        return SEVERITY_NAMES[severityLevel];
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) throws InvalidPatientException {
        this.condition = Validator.requireText(condition, "Emergency condition");
    }

    @Override
    public PatientCategory getCategory() {
        return PatientCategory.EMERGENCY;
    }

    @Override
    public int getPriorityLevel() {
        return 1;
    }

    @Override
    public String getPriorityLabel() {
        return "High Priority";
    }

    @Override
    public double calculateConsultationFee() {
        return 10000.0 + 2500.0 * severityLevel;   // more severe -> more resources
    }

    @Override
    public String getTypeSpecificDetails() {
        return "Condition          : " + condition + "\n"
                + "Severity           : " + severityLevel + " - " + getSeverityDescription() + "\n"
                + "Booking rule       : " + getBookingRule();
    }

    @Override
    public Map<String, String> getExtraAttributes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("severity", String.valueOf(severityLevel));
        map.put("condition", condition);
        return map;
    }

    /** Emergency cases are seen at any time of day. */
    @Override
    protected boolean requiresClinicHours() {
        return false;
    }

    @Override
    public LocalDate suggestAppointmentDate(LocalDate today) {
        return today;
    }

    // ------------------------------------------------------------ Appointable

    @Override
    public void scheduleAppointment(Appointment appointment) throws InvalidAppointmentException {
        validateNewAppointment(appointment);
        LocalDate latest = LocalDate.now().plusDays(MAX_DAYS_AHEAD);
        if (appointment.getDate().isAfter(latest)) {
            throw new InvalidAppointmentException("Emergency patients must be seen within "
                    + MAX_DAYS_AHEAD + " days (on or before " + latest + ").");
        }
        appointment.setUrgent(true);
        attachAppointment(appointment);
    }

    @Override
    public void cancelAppointment(String appointmentId) throws InvalidAppointmentException {
        cancelOwnAppointment(appointmentId);
    }

    @Override
    public String getBookingRule() {
        return "Emergency patient - any hour (24h), must be seen within " + MAX_DAYS_AHEAD
                + " days; appointments are flagged URGENT.";
    }
}
