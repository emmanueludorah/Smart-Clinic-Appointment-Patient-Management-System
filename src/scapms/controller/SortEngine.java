package scapms.controller;

import java.util.Comparator;

import scapms.model.ClinicDatabase;
import scapms.model.PatientField;
import scapms.model.PatientRecord;

/**
 * Student-implemented sorting algorithms (no Collections.sort / Arrays.sort):
 * Selection Sort, Insertion Sort, Merge Sort (recursive) and Quick Sort (recursive).
 * Comparisons and moves are counted so the GUI can show how each algorithm behaves.
 */
public class SortEngine {

    public enum Algorithm {
        SELECTION("Selection Sort", "O(n²) always",
                "Simple and predictable: it always makes about n²/2 comparisons but at most n-1 swaps. "
                + "Appropriate for the small patient lists of a university clinic (tens to a few hundred "
                + "records) and when moving records is costly. Not stable."),
        INSERTION("Insertion Sort", "O(n) best, O(n²) worst",
                "Very fast when the list is already almost sorted - e.g. after a few new patients have been "
                + "registered at the end of a sorted list it runs in nearly O(n). Stable (equal keys keep their "
                + "previous order) and in-place with very low overhead for small lists."),
        MERGE("Merge Sort", "O(n log n) always",
                "Guaranteed O(n log n) in every case and stable, so sorting by Age after sorting by Name keeps "
                + "patients of the same age in name order. Best choice for large or unknown data sizes. "
                + "Uses O(n) extra memory. Implemented recursively."),
        QUICK("Quick Sort", "O(n log n) average",
                "In-place and usually the fastest in practice on large, randomly ordered lists. Uses the middle "
                + "element as pivot to avoid the O(n²) worst case on already-sorted data. Not stable. "
                + "Implemented recursively.");

        private final String label;
        private final String complexity;
        private final String rationale;

        Algorithm(String label, String complexity, String rationale) {
            this.label = label;
            this.complexity = complexity;
            this.rationale = rationale;
        }

        public String getComplexity() {
            return complexity;
        }

        public String getRationale() {
            return rationale;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Statistics produced by a sort. */
    public static final class SortResult {
        private final Algorithm algorithm;
        private final PatientField field;
        private final boolean ascending;
        private final int size;
        private final long comparisons;
        private final long moves;
        private final long elapsedNanos;

        SortResult(Algorithm algorithm, PatientField field, boolean ascending, int size,
                   long comparisons, long moves, long elapsedNanos) {
            this.algorithm = algorithm;
            this.field = field;
            this.ascending = ascending;
            this.size = size;
            this.comparisons = comparisons;
            this.moves = moves;
            this.elapsedNanos = elapsedNanos;
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public PatientField getField() {
            return field;
        }

        public boolean isAscending() {
            return ascending;
        }

        public int getSize() {
            return size;
        }

        public long getComparisons() {
            return comparisons;
        }

        public long getMoves() {
            return moves;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }
    }

    private long comparisons;
    private long moves;
    private Comparator<PatientRecord> comparator;

    /** Sorts the database records in place using the chosen algorithm and key. */
    public SortResult sort(ClinicDatabase db, Algorithm algorithm, PatientField field, boolean ascending) {
        PatientRecord[] data = db.getPatients().toArray(new PatientRecord[0]);
        Comparator<PatientRecord> base = field.comparator();
        comparator = ascending ? base : (a, b) -> base.compare(b, a);
        comparisons = 0;
        moves = 0;
        long start = System.nanoTime();
        switch (algorithm) {
            case SELECTION:
                selectionSort(data);
                break;
            case INSERTION:
                insertionSort(data);
                break;
            case MERGE:
                mergeSort(data, new PatientRecord[data.length], 0, data.length - 1);
                break;
            case QUICK:
            default:
                quickSort(data, 0, data.length - 1);
                break;
        }
        long elapsed = System.nanoTime() - start;
        db.applyOrder(data, field, ascending);
        return new SortResult(algorithm, field, ascending, data.length, comparisons, moves, elapsed);
    }

    /** Suggests an algorithm for the current situation (shown in the GUI). */
    public static String recommend(int size, boolean partlySorted) {
        if (partlySorted) {
            return "Recommendation: the list is already partly ordered, so Insertion Sort will finish in close to O(n).";
        }
        if (size <= 30) {
            return "Recommendation: with only " + size + " records any algorithm is instant; Insertion Sort "
                    + "(stable, low overhead) or Selection Sort (fewest swaps) are perfectly adequate.";
        }
        return "Recommendation: with " + size + " records an O(n log n) algorithm is preferable - Merge Sort "
                + "(stable, guaranteed) or Quick Sort (fastest on average, in-place).";
    }

    private int compare(PatientRecord a, PatientRecord b) {
        comparisons++;
        return comparator.compare(a, b);
    }

    private void swap(PatientRecord[] a, int i, int j) {
        PatientRecord tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
        moves += 3;
    }

    // ========================================================== SELECTION SORT

    /** Repeatedly selects the smallest remaining element and swaps it into place. */
    void selectionSort(PatientRecord[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            int min = i;
            for (int j = i + 1; j < n; j++) {
                if (compare(a[j], a[min]) < 0) {
                    min = j;
                }
            }
            if (min != i) {
                swap(a, i, min);
            }
        }
    }

    // ========================================================== INSERTION SORT

    /** Inserts each element into its correct position within the sorted left part. */
    void insertionSort(PatientRecord[] a) {
        for (int i = 1; i < a.length; i++) {
            PatientRecord key = a[i];
            int j = i - 1;
            while (j >= 0 && compare(a[j], key) > 0) {
                a[j + 1] = a[j];       // shift larger element right
                moves++;
                j--;
            }
            a[j + 1] = key;
            moves++;
        }
    }

    // ============================================================== MERGE SORT

    /** Recursive merge sort on a[low..high]. */
    void mergeSort(PatientRecord[] a, PatientRecord[] temp, int low, int high) {
        if (low >= high) {
            return;                                    // base case: 0 or 1 element
        }
        int mid = (low + high) >>> 1;
        mergeSort(a, temp, low, mid);
        mergeSort(a, temp, mid + 1, high);
        merge(a, temp, low, mid, high);
    }

    private void merge(PatientRecord[] a, PatientRecord[] temp, int low, int mid, int high) {
        for (int k = low; k <= high; k++) {
            temp[k] = a[k];
        }
        int i = low;
        int j = mid + 1;
        for (int k = low; k <= high; k++) {
            if (i > mid) {
                a[k] = temp[j++];
            } else if (j > high) {
                a[k] = temp[i++];
            } else if (compare(temp[j], temp[i]) < 0) {   // strict '<' keeps the sort stable
                a[k] = temp[j++];
            } else {
                a[k] = temp[i++];
            }
            moves++;
        }
    }

    // ============================================================== QUICK SORT

    /** Recursive quick sort on a[low..high]. */
    void quickSort(PatientRecord[] a, int low, int high) {
        if (low >= high) {
            return;
        }
        int p = partition(a, low, high);
        quickSort(a, low, p - 1);
        quickSort(a, p + 1, high);
    }

    /** Lomuto partition using the middle element as pivot. */
    private int partition(PatientRecord[] a, int low, int high) {
        int mid = (low + high) >>> 1;
        swap(a, mid, high);
        PatientRecord pivot = a[high];
        int i = low;
        for (int j = low; j < high; j++) {
            if (compare(a[j], pivot) < 0) {
                if (i != j) {
                    swap(a, i, j);
                }
                i++;
            }
        }
        swap(a, i, high);
        return i;
    }
}
