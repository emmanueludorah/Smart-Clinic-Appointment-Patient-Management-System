package scapms.controller;

import java.util.ArrayList;
import java.util.List;

import scapms.model.PatientRecord;

/**
 * A small cache of "Frequently Accessed Patient Records" implemented with a
 * fixed-size array. The most recently accessed record is kept in slot 0; when the
 * cache is full the least recently used record (last slot) is dropped.
 */
public class RecentPatientCache {

    public static final int CAPACITY = 10;

    private final PatientRecord[] recentPatients = new PatientRecord[CAPACITY];
    private final int[] accessCounts = new int[CAPACITY];
    private int count = 0;

    /** Records an access, moving the patient to the front of the array. */
    public void recordAccess(PatientRecord patient) {
        if (patient == null) {
            return;
        }
        int index = indexOf(patient.getPatientId());
        int hits;
        if (index >= 0) {
            hits = accessCounts[index] + 1;
        } else {
            hits = 1;
            if (count < CAPACITY) {
                count++;
            }
            index = count - 1;        // the slot that will be overwritten by the shift
        }
        // shift elements [0 .. index-1] one place to the right
        for (int i = index; i > 0; i--) {
            recentPatients[i] = recentPatients[i - 1];
            accessCounts[i] = accessCounts[i - 1];
        }
        recentPatients[0] = patient;
        accessCounts[0] = hits;
    }

    public void remove(String patientId) {
        int index = indexOf(patientId);
        if (index < 0) {
            return;
        }
        for (int i = index; i < count - 1; i++) {
            recentPatients[i] = recentPatients[i + 1];
            accessCounts[i] = accessCounts[i + 1];
        }
        count--;
        recentPatients[count] = null;
        accessCounts[count] = 0;
    }

    /** Keeps cached pointers valid when a record object is replaced (edit / undo). */
    public void replace(PatientRecord oldRecord, PatientRecord newRecord) {
        for (int i = 0; i < count; i++) {
            if (recentPatients[i] == oldRecord) {
                recentPatients[i] = newRecord;
            }
        }
    }

    private int indexOf(String patientId) {
        for (int i = 0; i < count; i++) {
            if (recentPatients[i].getPatientId().equals(patientId)) {
                return i;
            }
        }
        return -1;
    }

    public PatientRecord getSlot(int index) {
        return recentPatients[index];
    }

    public int getAccessCount(int index) {
        return accessCounts[index];
    }

    public int size() {
        return count;
    }

    public List<PatientRecord> getRecentPatients() {
        List<PatientRecord> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(recentPatients[i]);
        }
        return list;
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            recentPatients[i] = null;
            accessCounts[i] = 0;
        }
        count = 0;
    }
}
