package scapms.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidPatientException;

/**
 * A routine (non-urgent) patient. Normal priority; insured patients get a fee discount;
 * at most MAX_PENDING pending appointments may be held at a time.
 */
public class GeneralPatient extends PatientRecord implements Appointable {

    public static final int MAX_PENDING = 3;
    private static final double BASE_FEE = 5000.0;
    private static final double INSURANCE_DISCOUNT = 0.20;

    private String insuranceProvider;

    public GeneralPatient(String patientId, String name, int age, String phone, String email,
                          LocalDate registrationDate, String insuranceProvider) throws InvalidPatientException {
        super(patientId, name, age, phone, email, registrationDate);
        setInsuranceProvider(insuranceProvider);
    }

    public String getInsuranceProvider() {
        return insuranceProvider;
    }

    public void setInsuranceProvider(String insuranceProvider) {
        this.insuranceProvider = (insuranceProvider == null || insuranceProvider.trim().isEmpty())
                ? "None" : insuranceProvider.trim();
    }

    public boolean isInsured() {
        return !insuranceProvider.equalsIgnoreCase("None");
    }

    @Override
    public PatientCategory getCategory() {
        return PatientCategory.GENERAL;
    }

    @Override
    public int getPriorityLevel() {
        return 3;
    }

    @Override
    public String getPriorityLabel() {
        return "Normal";
    }

    @Override
    public double calculateConsultationFee() {
        return isInsured() ? BASE_FEE * (1 - INSURANCE_DISCOUNT) : BASE_FEE;
    }

    @Override
    public String getTypeSpecificDetails() {
        return "Insurance provider : " + insuranceProvider + "\n"
                + "Insurance discount : " + (isInsured() ? "20%" : "none") + "\n"
                + "Booking rule       : " + getBookingRule();
    }

    @Override
    public Map<String, String> getExtraAttributes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("insurance", insuranceProvider);
        return map;
    }

    // ------------------------------------------------------------ Appointable

    @Override
    public void scheduleAppointment(Appointment appointment) throws InvalidAppointmentException {
        validateNewAppointment(appointment);
        if (countAppointments(AppointmentStatus.PENDING) >= MAX_PENDING) {
            throw new InvalidAppointmentException("General patients may hold at most " + MAX_PENDING
                    + " pending appointments. Complete or cancel one first.");
        }
        attachAppointment(appointment);
    }

    @Override
    public void cancelAppointment(String appointmentId) throws InvalidAppointmentException {
        cancelOwnAppointment(appointmentId);
    }

    @Override
    public String getBookingRule() {
        return "General patient - clinic hours only, maximum " + MAX_PENDING + " pending appointments.";
    }
}
