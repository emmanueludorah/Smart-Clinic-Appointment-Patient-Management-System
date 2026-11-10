package scapms.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidPatientException;
import scapms.util.Validator;

/**
 * A patient returning for review after a previous visit. Appointments must be after the
 * last visit and default to the referring doctor; a recommended review date is calculated.
 */
public class FollowUpPatient extends PatientRecord implements Appointable {

    private static final double REVIEW_FEE = 3000.0;

    private LocalDate lastVisitDate;
    private String referringDoctor;
    private int followUpIntervalDays;

    public FollowUpPatient(String patientId, String name, int age, String phone, String email,
                           LocalDate registrationDate, LocalDate lastVisitDate, String referringDoctor,
                           int followUpIntervalDays) throws InvalidPatientException {
        super(patientId, name, age, phone, email, registrationDate);
        setLastVisitDate(lastVisitDate);
        setReferringDoctor(referringDoctor);
        setFollowUpIntervalDays(followUpIntervalDays);
    }

    public LocalDate getLastVisitDate() {
        return lastVisitDate;
    }

    public void setLastVisitDate(LocalDate lastVisitDate) throws InvalidPatientException {
        if (lastVisitDate == null) {
            throw new InvalidPatientException("Last visit date is required for follow-up patients.");
        }
        if (lastVisitDate.isAfter(LocalDate.now())) {
            throw new InvalidPatientException("Last visit date cannot be in the future.");
        }
        this.lastVisitDate = lastVisitDate;
    }

    public String getReferringDoctor() {
        return referringDoctor;
    }

    public void setReferringDoctor(String referringDoctor) throws InvalidPatientException {
        this.referringDoctor = Validator.requireText(referringDoctor, "Referring doctor");
    }

    public int getFollowUpIntervalDays() {
        return followUpIntervalDays;
    }

    public void setFollowUpIntervalDays(int days) throws InvalidPatientException {
        if (days < 1 || days > 365) {
            throw new InvalidPatientException("Follow-up interval must be between 1 and 365 days.");
        }
        this.followUpIntervalDays = days;
    }

    public LocalDate getRecommendedFollowUpDate() {
        return lastVisitDate.plusDays(followUpIntervalDays);
    }

    /** @return true when the recommended review date has passed and nothing is booked. */
    public boolean isOverdue() {
        return LocalDate.now().isAfter(getRecommendedFollowUpDate()) && !hasPendingAppointments();
    }

    @Override
    public PatientCategory getCategory() {
        return PatientCategory.FOLLOW_UP;
    }

    @Override
    public int getPriorityLevel() {
        return 2;
    }

    @Override
    public String getPriorityLabel() {
        return "Scheduled";
    }

    @Override
    public double calculateConsultationFee() {
        return REVIEW_FEE;
    }

    @Override
    public String getTypeSpecificDetails() {
        return "Last visit         : " + lastVisitDate.format(Validator.DATE_FORMAT) + "\n"
                + "Referring doctor   : " + referringDoctor + "\n"
                + "Review interval    : " + followUpIntervalDays + " days\n"
                + "Recommended review : " + getRecommendedFollowUpDate().format(Validator.DATE_FORMAT)
                + (isOverdue() ? "  (OVERDUE)" : "") + "\n"
                + "Booking rule       : " + getBookingRule();
    }

    @Override
    public Map<String, String> getExtraAttributes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("lastVisit", lastVisitDate.format(Validator.DATE_FORMAT));
        map.put("referringDoctor", referringDoctor);
        map.put("interval", String.valueOf(followUpIntervalDays));
        return map;
    }

    @Override
    public LocalDate suggestAppointmentDate(LocalDate today) {
        LocalDate recommended = getRecommendedFollowUpDate();
        return recommended.isAfter(today) ? recommended : today.plusDays(1);
    }

    // ------------------------------------------------------------ Appointable

    @Override
    public void scheduleAppointment(Appointment appointment) throws InvalidAppointmentException {
        validateNewAppointment(appointment);
        if (!appointment.getDate().isAfter(lastVisitDate)) {
            throw new InvalidAppointmentException("A follow-up appointment must be after the last visit ("
                    + lastVisitDate.format(Validator.DATE_FORMAT) + ").");
        }
        if (appointment.getDoctor().isEmpty()) {
            appointment.setDoctor(referringDoctor);   // continuity of care
        }
        attachAppointment(appointment);
    }

    @Override
    public void cancelAppointment(String appointmentId) throws InvalidAppointmentException {
        cancelOwnAppointment(appointmentId);
    }

    @Override
    public String getBookingRule() {
        return "Follow-up patient - date must be after last visit (" + lastVisitDate.format(Validator.DATE_FORMAT)
                + "), recommended " + getRecommendedFollowUpDate().format(Validator.DATE_FORMAT)
                + "; doctor defaults to " + referringDoctor + ".";
    }
}
