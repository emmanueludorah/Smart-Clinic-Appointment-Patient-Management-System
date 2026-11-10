package scapms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the collection of registered patients (stored in an {@link ArrayList},
 * Java's equivalent of std::vector) and remembers whether the collection is currently
 * sorted, so the search engine can choose the appropriate algorithm.
 */
public class ClinicDatabase {

    private final ArrayList<PatientRecord> patients = new ArrayList<>();
    private PatientField sortedBy;          // null = unsorted
    private boolean sortedAscending = true;

    public void add(PatientRecord patient) {
        patients.add(patient);
        markUnsorted();
    }

    /** Re-inserts a patient at a given position (used by Undo). */
    public void insert(int index, PatientRecord patient) {
        int safeIndex = Math.max(0, Math.min(index, patients.size()));
        patients.add(safeIndex, patient);
        markUnsorted();
    }

    /** Removing an element keeps the remaining order, so the sorted flag is kept. */
    public int remove(PatientRecord patient) {
        int index = indexOf(patient);
        if (index >= 0) {
            patients.remove(index);
        }
        return index;
    }

    public void replace(PatientRecord oldRecord, PatientRecord newRecord) {
        int index = indexOf(oldRecord);
        if (index < 0) {
            throw new IllegalArgumentException("Record to replace is not in the database");
        }
        patients.set(index, newRecord);
        markUnsorted();
    }

    public int indexOf(PatientRecord patient) {
        for (int i = 0; i < patients.size(); i++) {
            if (patients.get(i) == patient) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsId(String patientId) {
        if (patientId == null) {
            return false;
        }
        String id = patientId.trim().toUpperCase();
        for (PatientRecord p : patients) {
            if (p.getPatientId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public PatientRecord get(int index) {
        return patients.get(index);
    }

    public int size() {
        return patients.size();
    }

    public boolean isEmpty() {
        return patients.isEmpty();
    }

    /** @return a read-only view of the patient list in its current order. */
    public List<PatientRecord> getPatients() {
        return Collections.unmodifiableList(patients);
    }

    /** Replaces the order of the records with an order produced by a sorting algorithm. */
    public void applyOrder(PatientRecord[] ordered, PatientField key, boolean ascending) {
        if (ordered.length != patients.size()) {
            throw new IllegalArgumentException("Sorted array size does not match the database");
        }
        patients.clear();
        for (PatientRecord p : ordered) {
            patients.add(p);
        }
        sortedBy = key;
        sortedAscending = ascending;
    }

    public PatientField getSortedBy() {
        return sortedBy;
    }

    public boolean isSortedAscending() {
        return sortedAscending;
    }

    /** @return true when the records are sorted ascending by the given field (binary search possible). */
    public boolean isSortedBy(PatientField field) {
        return sortedBy == field && sortedAscending;
    }

    public void markUnsorted() {
        sortedBy = null;
    }

    public int countByCategory(PatientCategory category) {
        int count = 0;
        for (PatientRecord p : patients) {
            if (p.getCategory() == category) {
                count++;
            }
        }
        return count;
    }

    public List<Appointment> getAllAppointments() {
        List<Appointment> all = new ArrayList<>();
        for (PatientRecord p : patients) {
            all.addAll(p.getAppointments());
        }
        return all;
    }

    public void clear() {
        patients.clear();
        sortedBy = null;
    }
}
