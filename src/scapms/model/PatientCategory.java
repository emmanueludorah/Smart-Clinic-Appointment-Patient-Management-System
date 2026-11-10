package scapms.model;

/** The three kinds of patient handled by the clinic. */
public enum PatientCategory {
    GENERAL("General", "General Patients"),
    EMERGENCY("Emergency", "Emergency Patients"),
    FOLLOW_UP("Follow-Up", "Follow-Up Patients");

    private final String displayName;
    private final String pluralLabel;

    PatientCategory(String displayName, String pluralLabel) {
        this.displayName = displayName;
        this.pluralLabel = pluralLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPluralLabel() {
        return pluralLabel;
    }

    /**
     * Converts user text such as "emergency", "Follow-Up" or "followup" into a category.
     * @return the matching category or {@code null} if the text matches none.
     */
    public static PatientCategory fromText(String text) {
        if (text == null) {
            return null;
        }
        String normalised = text.trim().toLowerCase().replaceAll("[\s_-]", "");
        for (PatientCategory category : values()) {
            String name = category.displayName.toLowerCase().replace("-", "");
            if (name.equals(normalised) || category.name().toLowerCase().replace("_", "").equals(normalised)) {
                return category;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
