package scapms.controller;

import java.util.List;

import scapms.model.PatientCategory;
import scapms.model.PatientRecord;

/**
 * Demonstrates runtime polymorphism: every method works only with the base-class
 * type {@link PatientRecord}, yet the subclass implementation (GeneralPatient,
 * EmergencyPatient or FollowUpPatient) is chosen at run time by dynamic dispatch.
 */
public final class PatientProcessor {

    private PatientProcessor() {
    }

    /** Processes ANY patient type through a base-class reference. */
    public static String processPatient(PatientRecord patient) {
        return String.format("%-7s %-22s %-10s %-14s %5d %12s",
                patient.getPatientId(),
                truncate(patient.getName(), 22),
                patient.getCategory(),               // overridden in each subclass
                patient.getPriorityLabel(),          // overridden in each subclass
                patient.getPriorityLevel(),          // overridden in each subclass
                String.format("%,.2f", patient.calculateConsultationFee()));   // overridden
    }

    /** Processes a whole list of mixed patient types, ordered by triage priority. */
    public static String processAll(List<PatientRecord> patients) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-7s %-22s %-10s %-14s %5s %12s%n",
                "ID", "Name", "Category", "Priority", "Level", "Fee"));
        sb.append("-".repeat(76)).append('\n');
        double[] totals = new double[PatientCategory.values().length];
        double grandTotal = 0;
        // list patients grouped by priority level 1 (most urgent) .. 3
        for (int level = 1; level <= 3; level++) {
            for (PatientRecord p : patients) {
                if (p.getPriorityLevel() == level) {
                    sb.append(processPatient(p)).append('\n');
                    totals[p.getCategory().ordinal()] += p.calculateConsultationFee();
                    grandTotal += p.calculateConsultationFee();
                }
            }
        }
        sb.append("-".repeat(76)).append('\n');
        for (PatientCategory c : PatientCategory.values()) {
            sb.append(String.format("Expected fees - %-20s %,14.2f%n", c.getPluralLabel(), totals[c.ordinal()]));
        }
        sb.append(String.format("Expected fees - %-20s %,14.2f%n", "ALL PATIENTS", grandTotal));
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }
}
