package scapms.controller;

import java.util.ArrayList;
import java.util.List;

import scapms.exception.InvalidSearchException;
import scapms.model.ClinicDatabase;
import scapms.model.PatientCategory;
import scapms.model.PatientField;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/**
 * Student-implemented searching algorithms (no library search functions are used):
 * <ul>
 *   <li>Linear search  - patient ID, name, category (works on unsorted data, O(n))</li>
 *   <li>Binary search  - patient ID / name prefix once the records are sorted (O(log n))</li>
 *   <li>Recursive search - patient name (divide-and-conquer recursion)</li>
 * </ul>
 * In AUTO mode the engine picks the best algorithm for the current state of the data.
 */
public class SearchEngine {

    public enum Algorithm {
        AUTO("Auto (best for data state)"),
        LINEAR("Linear Search"),
        BINARY("Binary Search"),
        RECURSIVE("Recursive Search");

        private final String label;

        Algorithm(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Field {
        ID("Patient ID"),
        NAME("Patient Name"),
        CATEGORY("Category");

        private final String label;

        Field(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Outcome of a search, including statistics for display. */
    public static final class SearchResult {
        private final List<PatientRecord> matches;
        private final Algorithm algorithmUsed;
        private final int comparisons;
        private final long elapsedNanos;
        private final String explanation;

        SearchResult(List<PatientRecord> matches, Algorithm algorithmUsed, int comparisons,
                     long elapsedNanos, String explanation) {
            this.matches = matches;
            this.algorithmUsed = algorithmUsed;
            this.comparisons = comparisons;
            this.elapsedNanos = elapsedNanos;
            this.explanation = explanation;
        }

        public List<PatientRecord> getMatches() {
            return matches;
        }

        public Algorithm getAlgorithmUsed() {
            return algorithmUsed;
        }

        public int getComparisons() {
            return comparisons;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }

        public String getExplanation() {
            return explanation;
        }
    }

    private int comparisons;

    /** Validates the input, chooses an algorithm and runs the search. */
    public SearchResult search(ClinicDatabase db, String query, Field field, Algorithm requested)
            throws InvalidSearchException {
        if (query == null || query.trim().isEmpty()) {
            throw new InvalidSearchException("Please enter a search keyword.");
        }
        if (field == null || requested == null) {
            throw new InvalidSearchException("Please choose what to search by and which algorithm to use.");
        }
        String q = query.trim();
        if (q.length() > 60) {
            throw new InvalidSearchException("Search keyword is too long (maximum 60 characters).");
        }
        Algorithm algorithm = chooseAlgorithm(db, field, requested);
        List<PatientRecord> data = db.getPatients();
        List<PatientRecord> matches = new ArrayList<>();
        String explanation;
        comparisons = 0;
        long start = System.nanoTime();

        switch (field) {
            case ID: {
                String id = q.toUpperCase();
                if (!Validator.isValidPatientId(id)) {
                    throw new InvalidSearchException("'" + q + "' is not a valid Patient ID. IDs look like P0001.");
                }
                if (algorithm == Algorithm.BINARY) {
                    requireSorted(db, PatientField.ID, "Binary search by ID");
                    int index = binarySearchById(data, id);
                    if (index >= 0) {
                        matches.add(data.get(index));
                    }
                    explanation = "Records are sorted by Patient ID, so binary search halves the search space at each step - O(log n).";
                } else if (algorithm == Algorithm.LINEAR) {
                    PatientRecord found = linearSearchById(data, id);
                    if (found != null) {
                        matches.add(found);
                    }
                    explanation = db.isSortedBy(PatientField.ID)
                            ? "Linear search checks each record in turn - O(n) (binary search would also work now)."
                            : "Records are NOT sorted by Patient ID, so linear search checks each record in turn - O(n).";
                } else {
                    throw new InvalidSearchException("Recursive search is available for patient names only.");
                }
                break;
            }
            case NAME: {
                if (algorithm == Algorithm.BINARY) {
                    requireSorted(db, PatientField.NAME, "Binary search by name");
                    matches = binarySearchByNamePrefix(data, q);
                    explanation = "Records are sorted by name: binary search finds the first name starting with '"
                            + q + "' in O(log n), then collects the neighbouring matches.";
                } else if (algorithm == Algorithm.RECURSIVE) {
                    recursiveSearchByName(data, q.toLowerCase(), 0, data.size() - 1, matches);
                    explanation = "Recursive divide-and-conquer search: the list is split in half until single "
                            + "records remain; every name containing '" + q + "' is returned (works on unsorted data).";
                } else {
                    matches = linearSearchByName(data, q);
                    explanation = "Linear search checks every name for the text '" + q + "' - O(n).";
                }
                break;
            }
            case CATEGORY:
            default: {
                PatientCategory category = PatientCategory.fromText(q);
                if (category == null) {
                    throw new InvalidSearchException("Unknown category '" + q + "'. Use General, Emergency or Follow-Up.");
                }
                if (algorithm != Algorithm.LINEAR) {
                    throw new InvalidSearchException("Category search uses Linear Search (records are never sorted by category).");
                }
                matches = linearSearchByCategory(data, category);
                explanation = "Many patients share a category, so linear search collects every matching record - O(n).";
                break;
            }
        }
        long elapsed = System.nanoTime() - start;
        return new SearchResult(matches, algorithm, comparisons, elapsed, explanation);
    }

    /** AUTO picks binary search when the data is sorted on the searched field, otherwise linear/recursive. */
    public Algorithm chooseAlgorithm(ClinicDatabase db, Field field, Algorithm requested) {
        if (requested != Algorithm.AUTO) {
            return requested;
        }
        switch (field) {
            case ID:
                return db.isSortedBy(PatientField.ID) ? Algorithm.BINARY : Algorithm.LINEAR;
            case NAME:
                return db.isSortedBy(PatientField.NAME) ? Algorithm.BINARY : Algorithm.RECURSIVE;
            default:
                return Algorithm.LINEAR;
        }
    }

    private void requireSorted(ClinicDatabase db, PatientField field, String what) throws InvalidSearchException {
        if (!db.isSortedBy(field)) {
            throw new InvalidSearchException(what + " requires the records to be sorted by " + field
                    + " (ascending). Sort them first, or choose Auto / Linear search.");
        }
    }

    // ============================================================ LINEAR SEARCH

    /** Linear search by patient ID - works on unsorted data. */
    public PatientRecord linearSearchById(List<PatientRecord> list, String patientId) {
        for (int i = 0; i < list.size(); i++) {
            comparisons++;
            if (list.get(i).getPatientId().equals(patientId)) {
                return list.get(i);
            }
        }
        return null;
    }

    public List<PatientRecord> linearSearchByName(List<PatientRecord> list, String text) {
        String needle = text.toLowerCase();
        List<PatientRecord> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            comparisons++;
            if (list.get(i).getName().toLowerCase().contains(needle)) {
                result.add(list.get(i));
            }
        }
        return result;
    }

    public List<PatientRecord> linearSearchByCategory(List<PatientRecord> list, PatientCategory category) {
        List<PatientRecord> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            comparisons++;
            if (list.get(i).getCategory() == category) {
                result.add(list.get(i));
            }
        }
        return result;
    }

    /** Quick filter used by the patient table (matches ID, name or phone). */
    public List<PatientRecord> filter(List<PatientRecord> list, String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>(list);
        }
        String needle = text.trim().toLowerCase();
        List<PatientRecord> result = new ArrayList<>();
        for (PatientRecord p : list) {
            if (p.getPatientId().toLowerCase().contains(needle) || p.getName().toLowerCase().contains(needle)
                    || p.getPhone().contains(needle)) {
                result.add(p);
            }
        }
        return result;
    }

    // ============================================================ BINARY SEARCH

    /**
     * Iterative binary search by patient ID.
     * Precondition: list is sorted ascending by ID.
     * @return index of the record or -1
     */
    public int binarySearchById(List<PatientRecord> sorted, String patientId) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            comparisons++;
            int c = PatientRecord.compareIds(sorted.get(mid).getPatientId(), patientId);
            if (c == 0) {
                return mid;
            } else if (c < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    /**
     * Binary search for names that START with the given prefix.
     * Precondition: list is sorted ascending by name (case-insensitive).
     * Finds the first candidate (lower bound) in O(log n) then scans forward.
     */
    public List<PatientRecord> binarySearchByNamePrefix(List<PatientRecord> sorted, String prefix) {
        String p = prefix.toLowerCase();
        int low = 0;
        int high = sorted.size();          // search in [low, high)
        while (low < high) {
            int mid = (low + high) >>> 1;
            comparisons++;
            if (sorted.get(mid).getName().compareToIgnoreCase(p) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        List<PatientRecord> result = new ArrayList<>();
        for (int i = low; i < sorted.size(); i++) {
            comparisons++;
            if (!sorted.get(i).getName().toLowerCase().startsWith(p)) {
                break;
            }
            result.add(sorted.get(i));
        }
        return result;
    }

    // ========================================================= RECURSIVE SEARCH

    /**
     * Recursive divide-and-conquer search for names containing a keyword.
     * Base cases: empty range, or a single record. Recursive case: search the left
     * half, then the right half (keeps the original order, recursion depth O(log n)).
     */
    public void recursiveSearchByName(List<PatientRecord> list, String keywordLower, int low, int high,
                                      List<PatientRecord> results) {
        if (low > high) {
            return;                                             // base case 1: empty range
        }
        if (low == high) {                                      // base case 2: one record
            comparisons++;
            if (list.get(low).getName().toLowerCase().contains(keywordLower)) {
                results.add(list.get(low));
            }
            return;
        }
        int mid = (low + high) >>> 1;
        recursiveSearchByName(list, keywordLower, low, mid, results);       // left half
        recursiveSearchByName(list, keywordLower, mid + 1, high, results);  // right half
    }

    /** @return comparisons made by the most recent search call. */
    public int getLastComparisons() {
        return comparisons;
    }
}
