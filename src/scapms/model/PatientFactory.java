package scapms.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import scapms.exception.InvalidPatientException;
import scapms.util.Validator;

/**
 * Creates the correct PatientRecord subclass for a category, and describes the
 * extra fields each category needs (used by the GUI to build its form dynamically
 * and by the FileHandler when loading).
 */
public final class PatientFactory {

    /** Describes one type-specific input field. */
    public static final class FieldSpec {
        private final String key;
        private final String label;
        private final String tooltip;
        private final String defaultValue;
        private final String[] options;

        FieldSpec(String key, String label, String tooltip, String defaultValue, String... options) {
            this.key = key;
            this.label = label;
            this.tooltip = tooltip;
            this.defaultValue = defaultValue;
            this.options = options;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public String getTooltip() {
            return tooltip;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        /** @return fixed choices (shown as a combo box) or an empty array for free text. */
        public String[] getOptions() {
            return options.clone();
        }
    }

    private PatientFactory() {
    }

    public static List<FieldSpec> extraFieldsFor(PatientCategory category) {
        List<FieldSpec> specs = new ArrayList<>();
        switch (category) {
            case GENERAL:
                specs.add(new FieldSpec("insurance", "Insurance Provider:",
                        "Health insurance scheme (type 'None' if uninsured). Insured patients get 20% off.", "None"));
                break;
            case EMERGENCY:
                specs.add(new FieldSpec("severity", "Severity Level:",
                        "1 = Minor, 2 = Moderate, 3 = Serious, 4 = Severe, 5 = Critical", "3",
                        "1", "2", "3", "4", "5"));
                specs.add(new FieldSpec("condition", "Emergency Condition:",
                        "Short description of the emergency (required)", ""));
                break;
            case FOLLOW_UP:
                specs.add(new FieldSpec("lastVisit", "Last Visit Date:",
                        "Date of the previous consultation (yyyy-MM-dd)",
                        LocalDate.now().minusDays(14).format(Validator.DATE_FORMAT)));
                specs.add(new FieldSpec("referringDoctor", "Referring Doctor:",
                        "Doctor who requested the follow-up (required)", ""));
                specs.add(new FieldSpec("interval", "Review Interval (days):",
                        "Number of days after the last visit when the patient should be reviewed (1-365)", "14"));
                break;
            default:
                break;
        }
        return Collections.unmodifiableList(specs);
    }

    public static PatientRecord create(PatientCategory category, String id, String name, int age, String phone,
                                       String email, LocalDate registered, Map<String, String> extras)
            throws InvalidPatientException {
        if (category == null) {
            throw new InvalidPatientException("Please choose a patient type.");
        }
        switch (category) {
            case GENERAL:
                return new GeneralPatient(id, name, age, phone, email, registered, extras.get("insurance"));
            case EMERGENCY:
                return new EmergencyPatient(id, name, age, phone, email, registered,
                        Validator.parseIntInRange(extras.get("severity"), "Severity level", 1, 5),
                        extras.get("condition"));
            case FOLLOW_UP:
                return new FollowUpPatient(id, name, age, phone, email, registered,
                        Validator.parsePatientDate(extras.get("lastVisit"), "Last visit date"),
                        extras.get("referringDoctor"),
                        Validator.parseIntInRange(extras.get("interval"), "Review interval", 1, 365));
            default:
                throw new InvalidPatientException("Unsupported patient type: " + category);
        }
    }
}
