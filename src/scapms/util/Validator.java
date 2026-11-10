package scapms.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import scapms.exception.InvalidAgeException;
import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidPatientException;

/**
 * Central input-validation rules. Each method either returns a cleaned value
 * or throws a meaningful custom exception that the GUI shows in a dialog.
 */
public final class Validator {

    public static final int MIN_AGE = 0;
    public static final int MAX_AGE = 120;

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Pattern PATIENT_ID = Pattern.compile("P\\d{4,6}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}][\\p{L} .'-]*");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{7,15}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private Validator() {
    }

    // ------------------------------------------------------------------ patient

    public static boolean isValidPatientId(String id) {
        return id != null && PATIENT_ID.matcher(id.trim().toUpperCase()).matches();
    }

    public static String requirePatientId(String id) throws InvalidPatientException {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidPatientException("Patient ID is required.");
        }
        String cleaned = id.trim().toUpperCase();
        if (!PATIENT_ID.matcher(cleaned).matches()) {
            throw new InvalidPatientException("Patient ID must be 'P' followed by 4-6 digits (e.g. P0001).");
        }
        return cleaned;
    }

    public static String requireName(String name) throws InvalidPatientException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidPatientException("Patient name cannot be empty.");
        }
        String cleaned = name.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < 2 || cleaned.length() > 60) {
            throw new InvalidPatientException("Patient name must be between 2 and 60 characters.");
        }
        if (!NAME.matcher(cleaned).matches()) {
            throw new InvalidPatientException(
                    "Patient name may only contain letters, spaces, apostrophes, hyphens and full stops.");
        }
        return cleaned;
    }

    /** Parses an age typed by the user. */
    public static int parseAge(String text) throws InvalidAgeException {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidAgeException("Patient age is required.");
        }
        try {
            return requireValidAge(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidAgeException("Patient age must be a whole number between 0 and 120.");
        }
    }

    public static int requireValidAge(int age) throws InvalidAgeException {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new InvalidAgeException("Patient age must be between 0 and 120.");
        }
        return age;
    }

    public static String requirePhone(String phone) throws InvalidPatientException {
        if (phone == null || phone.trim().isEmpty()) {
            throw new InvalidPatientException("Contact phone number is required.");
        }
        String cleaned = phone.trim().replaceAll("[\\s-]", "");
        if (!PHONE.matcher(cleaned).matches()) {
            throw new InvalidPatientException("Phone number must contain 7-15 digits (optionally starting with +).");
        }
        return cleaned;
    }

    /** Email is optional; an empty value is accepted. */
    public static String optionalEmail(String email) throws InvalidPatientException {
        if (email == null || email.trim().isEmpty()) {
            return "";
        }
        String cleaned = email.trim();
        if (!EMAIL.matcher(cleaned).matches()) {
            throw new InvalidPatientException("Email address '" + cleaned + "' is not valid.");
        }
        return cleaned;
    }

    public static String requireText(String value, String fieldName) throws InvalidPatientException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidPatientException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static int parseIntInRange(String text, String fieldName, int min, int max)
            throws InvalidPatientException {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            if (value < min || value > max) {
                throw new InvalidPatientException(fieldName + " must be between " + min + " and " + max + ".");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new InvalidPatientException(fieldName + " must be a whole number between " + min + " and " + max + ".");
        }
    }

    public static LocalDate parsePatientDate(String text, String fieldName) throws InvalidPatientException {
        LocalDate date = tryParseDate(text);
        if (date == null) {
            throw new InvalidPatientException(fieldName + " must be a valid date in the format yyyy-MM-dd.");
        }
        return date;
    }

    // -------------------------------------------------------------- appointment

    public static LocalDate parseAppointmentDate(String text) throws InvalidAppointmentException {
        LocalDate date = tryParseDate(text);
        if (date == null) {
            throw new InvalidAppointmentException("Appointment date must be a valid date in the format yyyy-MM-dd.");
        }
        return date;
    }

    public static LocalTime parseAppointmentTime(String text) throws InvalidAppointmentException {
        LocalTime time = tryParseTime(text);
        if (time == null) {
            throw new InvalidAppointmentException("Appointment time must be a valid 24-hour time in the format HH:mm.");
        }
        return time;
    }

    /** @return the parsed date, or {@code null} when the text is not a valid date. */
    public static LocalDate tryParseDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** @return the parsed time, or {@code null} when the text is not a valid HH:mm time. */
    public static LocalTime tryParseTime(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
