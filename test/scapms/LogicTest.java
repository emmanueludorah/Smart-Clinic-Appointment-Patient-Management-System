package scapms;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import scapms.controller.ClinicManager;
import scapms.controller.RecentPatientCache;
import scapms.controller.ReportGenerator;
import scapms.controller.SearchEngine;
import scapms.controller.SortEngine;
import scapms.exception.ClinicException;
import scapms.exception.DuplicatePatientException;
import scapms.exception.FileLoadException;
import scapms.exception.InvalidAgeException;
import scapms.exception.InvalidAppointmentException;
import scapms.exception.InvalidSearchException;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.EmergencyPatient;
import scapms.model.FollowUpPatient;
import scapms.model.GeneralPatient;
import scapms.model.PatientField;
import scapms.model.PatientRecord;
import scapms.model.QueueEntry;
import scapms.util.FileHandler;
import scapms.util.Validator;

/** Plain-Java self-checking test of the non-GUI logic (run: java -cp out;test-out scapms.LogicTest). */
public final class LogicTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testSorting();
        testSearching();
        testValidationAndExceptions();
        testAppointmentRules();
        testUndo();
        testQueueAndCache();
        testPersistence();
        testReports();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void expectThrows(Class<? extends Exception> type, String name, ThrowingRunnable r) {
        try {
            r.run();
            check(false, name + " (no exception)");
        } catch (Exception e) {
            check(type.isInstance(e), name + " (got " + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static ClinicManager fresh() {
        ClinicManager m = new ClinicManager();
        m.loadSampleData();
        return m;
    }

    // ------------------------------------------------------------------------

    private static void testSorting() {
        for (SortEngine.Algorithm algo : SortEngine.Algorithm.values()) {
            for (PatientField field : PatientField.values()) {
                for (boolean asc : new boolean[] {true, false}) {
                    ClinicManager m = fresh();
                    List<PatientRecord> before = new ArrayList<>(m.getDatabase().getPatients());
                    m.sortPatients(algo, field, asc);
                    List<PatientRecord> after = m.getDatabase().getPatients();
                    Comparator<PatientRecord> c = field.comparator();
                    boolean ordered = true;
                    for (int i = 1; i < after.size(); i++) {
                        int r = c.compare(after.get(i - 1), after.get(i));
                        if (asc ? r > 0 : r < 0) {
                            ordered = false;
                        }
                    }
                    check(ordered, algo + " by " + field + (asc ? " asc" : " desc") + " is ordered");
                    check(after.size() == before.size() && after.containsAll(before), algo + " keeps all records");
                    check(m.getDatabase().isSortedBy(field) == asc, algo + " sets sorted flag");
                }
            }
        }
        // stability of merge & insertion: sort by name, then by age -> equal ages keep name order
        for (SortEngine.Algorithm algo : new SortEngine.Algorithm[] {SortEngine.Algorithm.MERGE, SortEngine.Algorithm.INSERTION}) {
            ClinicManager m = fresh();
            m.sortPatients(SortEngine.Algorithm.MERGE, PatientField.NAME, true);
            m.sortPatients(algo, PatientField.AGE, true);
            List<PatientRecord> l = m.getDatabase().getPatients();
            boolean stable = true;
            for (int i = 1; i < l.size(); i++) {
                if (l.get(i - 1).getAge() == l.get(i).getAge()
                        && l.get(i - 1).getName().compareToIgnoreCase(l.get(i).getName()) > 0) {
                    stable = false;
                }
            }
            check(stable, algo + " is stable");
        }
    }

    private static void testSearching() throws Exception {
        ClinicManager m = fresh();
        SearchEngine.SearchResult r = m.searchPatients("p0004", SearchEngine.Field.ID, SearchEngine.Algorithm.AUTO);
        check(r.getAlgorithmUsed() == SearchEngine.Algorithm.LINEAR, "auto uses linear on unsorted");
        check(r.getMatches().size() == 1 && r.getMatches().get(0).getName().equals("Ibrahim Musa"), "linear finds P0004");

        expectThrows(InvalidSearchException.class, "binary on unsorted rejected",
                () -> m.searchPatients("P0004", SearchEngine.Field.ID, SearchEngine.Algorithm.BINARY));

        m.sortPatients(SortEngine.Algorithm.QUICK, PatientField.ID, true);
        for (PatientRecord p : m.getDatabase().getPatients()) {
            SearchEngine.SearchResult b = m.searchPatients(p.getPatientId(), SearchEngine.Field.ID, SearchEngine.Algorithm.AUTO);
            check(b.getAlgorithmUsed() == SearchEngine.Algorithm.BINARY && b.getMatches().size() == 1
                    && b.getMatches().get(0) == p, "binary finds " + p.getPatientId());
        }
        check(m.searchPatients("P0999", SearchEngine.Field.ID, SearchEngine.Algorithm.BINARY).getMatches().isEmpty(),
                "binary miss returns empty");

        // recursive == linear for substring search
        for (String q : new String[] {"a", "ok", "YUS", "zz", "e"}) {
            List<PatientRecord> rec = m.searchPatients(q, SearchEngine.Field.NAME, SearchEngine.Algorithm.RECURSIVE).getMatches();
            List<PatientRecord> lin = m.searchPatients(q, SearchEngine.Field.NAME, SearchEngine.Algorithm.LINEAR).getMatches();
            check(rec.equals(lin), "recursive == linear for '" + q + "'");
        }
        // binary prefix on name-sorted data
        m.sortPatients(SortEngine.Algorithm.MERGE, PatientField.NAME, true);
        for (String q : new String[] {"a", "Ad", "z", "kemi", "x"}) {
            List<PatientRecord> bin = m.searchPatients(q, SearchEngine.Field.NAME, SearchEngine.Algorithm.BINARY).getMatches();
            List<PatientRecord> expected = new ArrayList<>();
            for (PatientRecord p : m.getDatabase().getPatients()) {
                if (p.getName().toLowerCase().startsWith(q.toLowerCase())) {
                    expected.add(p);
                }
            }
            check(bin.equals(expected), "binary name prefix '" + q + "'");
        }
        check(m.searchPatients("emergency", SearchEngine.Field.CATEGORY, SearchEngine.Algorithm.AUTO).getMatches().size() == 4,
                "category search finds 4 emergency");
        check(m.searchPatients("follow up", SearchEngine.Field.CATEGORY, SearchEngine.Algorithm.AUTO).getMatches().size() == 4,
                "category 'follow up' recognised");
        expectThrows(InvalidSearchException.class, "empty keyword", () -> m.searchPatients("  ", SearchEngine.Field.NAME, SearchEngine.Algorithm.AUTO));
        expectThrows(InvalidSearchException.class, "bad ID format", () -> m.searchPatients("123", SearchEngine.Field.ID, SearchEngine.Algorithm.AUTO));
        expectThrows(InvalidSearchException.class, "bad category", () -> m.searchPatients("vip", SearchEngine.Field.CATEGORY, SearchEngine.Algorithm.AUTO));
    }

    private static void testValidationAndExceptions() {
        LocalDate t = LocalDate.now();
        expectThrows(InvalidAgeException.class, "age 121", () -> new GeneralPatient("P0100", "Ann Lee", 121, "08012345678", "", t, "None"));
        expectThrows(InvalidAgeException.class, "age -1", () -> new GeneralPatient("P0100", "Ann Lee", -1, "08012345678", "", t, "None"));
        expectThrows(InvalidAgeException.class, "age text", () -> Validator.parseAge("abc"));
        expectThrows(ClinicException.class, "bad name", () -> new GeneralPatient("P0100", "4nn", 20, "08012345678", "", t, "None"));
        expectThrows(ClinicException.class, "bad phone", () -> new GeneralPatient("P0100", "Ann Lee", 20, "12", "", t, "None"));
        expectThrows(ClinicException.class, "bad email", () -> new GeneralPatient("P0100", "Ann Lee", 20, "08012345678", "x@", t, "None"));
        expectThrows(ClinicException.class, "bad id", () -> new GeneralPatient("X1", "Ann Lee", 20, "08012345678", "", t, "None"));
        expectThrows(ClinicException.class, "severity 6", () -> new EmergencyPatient("P0100", "Ann Lee", 20, "08012345678", "", t, 6, "x"));
        expectThrows(ClinicException.class, "future last visit", () -> new FollowUpPatient("P0100", "Ann Lee", 20, "08012345678", "", t, t.plusDays(1), "Dr X", 7));
        ClinicManager m = fresh();
        expectThrows(DuplicatePatientException.class, "duplicate id",
                () -> m.registerPatient(new GeneralPatient("P0001", "Ann Lee", 20, "08012345678", "", t, "None")));
    }

    private static void testAppointmentRules() throws Exception {
        ClinicManager m = fresh();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        // emergency: > 3 days rejected, any hour allowed, flagged urgent
        expectThrows(InvalidAppointmentException.class, "emergency > 3 days",
                () -> m.getAppointmentController().scheduleAppointment("P0006", LocalDate.now().plusDays(5).toString(), "10:00", "Dr", "x"));
        Appointment e = m.getAppointmentController().scheduleAppointment("P0006", tomorrow, "22:00", "Dr", "night");
        check(e.isUrgent(), "emergency appointment urgent");
        // general: clinic hours, max 3 pending
        expectThrows(InvalidAppointmentException.class, "general out of hours",
                () -> m.getAppointmentController().scheduleAppointment("P0008", tomorrow, "22:00", "Dr", "x"));
        m.getAppointmentController().scheduleAppointment("P0008", tomorrow, "09:00", "Dr", "a");
        m.getAppointmentController().scheduleAppointment("P0008", tomorrow, "10:00", "Dr", "b");
        m.getAppointmentController().scheduleAppointment("P0008", tomorrow, "11:00", "Dr", "c");
        expectThrows(InvalidAppointmentException.class, "general max 3",
                () -> m.getAppointmentController().scheduleAppointment("P0008", tomorrow, "12:00", "Dr", "d"));
        expectThrows(InvalidAppointmentException.class, "double booking",
                () -> m.getAppointmentController().scheduleAppointment("P0006", tomorrow, "22:00", "Dr", "again"));
        // follow-up: doctor defaults to referring doctor
        Appointment f = m.getAppointmentController().scheduleAppointment("P0012", tomorrow, "09:00", "", "review");
        check(f.getDoctor().equals("Dr. Samuel Ade"), "follow-up default doctor");
        expectThrows(InvalidAppointmentException.class, "past date",
                () -> m.getAppointmentController().scheduleAppointment("P0012", "2020-01-01", "09:00", "", "x"));
        expectThrows(InvalidAppointmentException.class, "bad date", () -> m.getAppointmentController().scheduleAppointment("P0012", "31/12/2026", "09:00", "", "x"));
        expectThrows(InvalidAppointmentException.class, "bad time", () -> m.getAppointmentController().scheduleAppointment("P0012", tomorrow, "25:00", "", "x"));
        expectThrows(InvalidAppointmentException.class, "missing reason", () -> m.getAppointmentController().scheduleAppointment("P0012", tomorrow, "13:00", "", " "));
        expectThrows(ClinicException.class, "unknown patient", () -> m.getAppointmentController().scheduleAppointment("P0999", tomorrow, "13:00", "", "x"));
        // IDs: no gaps after failures
        check(f.getAppointmentId().equals("A0035"), "appointment ids sequential without gaps (got " + f.getAppointmentId() + ")");
        // cancel twice
        m.getAppointmentController().cancelAppointment(f.getAppointmentId());
        expectThrows(InvalidAppointmentException.class, "cancel twice", () -> m.getAppointmentController().cancelAppointment(f.getAppointmentId()));
    }

    private static void testUndo() throws Exception {
        ClinicManager m = fresh();
        int size = m.getDatabase().size();
        m.addToQueue("P0005");
        PatientRecord p5 = m.findPatient("P0005");
        int idx = m.getDatabase().indexOf(p5);
        int qpos = m.getQueueManager().positionOf("P0005");
        m.deletePatient("P0005");
        check(m.getDatabase().size() == size - 1 && !m.getQueueManager().contains("P0005"), "delete removes patient+queue");
        m.undoLastOperation();
        check(m.getDatabase().size() == size && m.getDatabase().indexOf(p5) == idx, "undo delete restores position");
        check(m.getQueueManager().positionOf("P0005") == qpos, "undo delete restores queue position");

        PatientRecord before = m.findPatient("P0001");
        int appts = before.getAppointments().size();
        m.updatePatient("P0001", new EmergencyPatient("P0001", "Adaeze O. Okafor", 22, "08031234567", "", before.getRegistrationDate(), 2, "Fall"));
        check(m.findPatient("P0001") instanceof EmergencyPatient && m.findPatient("P0001").getAppointments().size() == appts,
                "edit changes type and keeps appointments");
        String tomorrow = LocalDate.now().plusDays(1).toString();
        m.getAppointmentController().scheduleAppointment("P0001", tomorrow, "13:00", "Dr", "after edit");
        m.undoLastOperation();   // undo schedule
        m.undoLastOperation();   // undo edit
        check(m.findPatient("P0001") == before && before.getName().equals("Adaeze Okafor")
                && before.getAppointments().size() == appts, "undo edit restores original");

        Appointment a = m.findPatient("P0003").getNextPendingAppointment();
        m.getAppointmentController().cancelAppointment(a.getAppointmentId());
        m.undoLastOperation();
        check(a.getStatus() == AppointmentStatus.PENDING, "undo cancel");
        m.getAppointmentController().completeAppointment(a.getAppointmentId());
        m.undoLastOperation();
        check(a.getStatus() == AppointmentStatus.PENDING, "undo complete");
        int n = m.findPatient("P0003").getAppointments().size();
        m.getAppointmentController().removeAppointment(a.getAppointmentId());
        check(m.findPatient("P0003").getAppointments().size() == n - 1, "remove appointment");
        m.undoLastOperation();
        check(m.findPatient("P0003").findAppointment(a.getAppointmentId()) == a, "undo remove appointment");

        m.registerPatient(new GeneralPatient("P0100", "New Person", 30, "08012345678", "", LocalDate.now(), "None"));
        m.undoLastOperation();
        check(m.findPatient("P0100") == null, "undo register");
        check(!m.getUndoManager().canUndo(), "stack empty after undoing everything");
        expectThrows(ClinicException.class, "undo on empty stack", m::undoLastOperation);
    }

    private static void testQueueAndCache() throws Exception {
        ClinicManager m = fresh();
        m.getQueueManager().clear();
        m.addToQueue("P0001");
        m.addToQueue("P0002");
        m.addToQueue("P0003");
        expectThrows(ClinicException.class, "queue duplicate", () -> m.addToQueue("P0002"));
        check(m.callNextPatient().getPatientId().equals("P0001"), "FIFO 1");
        check(m.callNextPatient().getPatientId().equals("P0002"), "FIFO 2");
        check(m.callNextPatient().getPatientId().equals("P0003"), "FIFO 3");
        expectThrows(ClinicException.class, "empty queue", m::callNextPatient);

        RecentPatientCache c = new RecentPatientCache();
        List<PatientRecord> all = m.getDatabase().getPatients();
        for (int i = 0; i < 12; i++) {
            c.recordAccess(all.get(i));
        }
        check(c.size() == 10 && c.getSlot(0) == all.get(11) && c.getSlot(9) == all.get(2), "cache LRU eviction");
        c.recordAccess(all.get(5));
        check(c.getSlot(0) == all.get(5) && c.getAccessCount(0) == 2 && c.size() == 10, "cache move to front");
        c.remove(all.get(5).getPatientId());
        check(c.size() == 9 && c.getSlot(0) == all.get(11), "cache remove");
    }

    private static void testPersistence() throws Exception {
        ClinicManager m = fresh();
        m.getDatabase().getPatients();
        File f = File.createTempFile("scapms", ".txt");
        f.deleteOnExit();
        m.save(f);
        ClinicManager n = new ClinicManager();
        n.load(f);
        check(n.getDatabase().size() == m.getDatabase().size(), "round-trip patient count");
        check(n.getDatabase().getAllAppointments().size() == m.getDatabase().getAllAppointments().size(), "round-trip appointments");
        check(n.getQueueManager().size() == m.getQueueManager().size(), "round-trip queue");
        for (PatientRecord p : m.getDatabase().getPatients()) {
            PatientRecord q = n.findPatient(p.getPatientId());
            check(q != null && q.getClass() == p.getClass() && q.getFullDetails().equals(p.getFullDetails()),
                    "round-trip " + p.getPatientId());
        }
        File bad = File.createTempFile("scapms-bad", ".txt");
        bad.deleteOnExit();
        FileHandler.writeText(bad, FileHandler.HEADER + "\nPATIENT|GENERAL|P0001|Ann|300|08012345678||2026-01-01|insurance=None\n");
        expectThrows(FileLoadException.class, "invalid file rejected", () -> n.load(bad));
        check(n.getDatabase().size() == m.getDatabase().size(), "failed load keeps existing data");
        expectThrows(FileLoadException.class, "missing file", () -> n.load(new File("does-not-exist.txt")));
        FileHandler.writeText(bad, "hello\n");
        expectThrows(FileLoadException.class, "no header", () -> n.load(bad));
        // special characters survive
        m.registerPatient(new EmergencyPatient("P0200", "Mary O'Neil", 40, "+2348012345678", "", LocalDate.now(), 3, "Cut | bleeding \\ arm"));
        m.save(f);
        n.load(f);
        check(((EmergencyPatient) n.findPatient("P0200")).getCondition().equals("Cut | bleeding \\ arm"), "escaping round-trip");
        List<QueueEntry> q = n.getQueueManager().snapshot();
        check(!q.isEmpty(), "queue loaded");
    }

    private static void testReports() {
        ClinicManager m = fresh();
        for (ReportGenerator.ReportType t : ReportGenerator.ReportType.values()) {
            String r = m.getReportGenerator().generate(t, LocalDate.now());
            check(r != null && r.length() > 100, "report " + t);
        }
        String most = m.getReportGenerator().mostFrequentlyAttended();
        check(most.indexOf("Fatima Bello") < most.indexOf("Adaeze Okafor"), "most frequent order (3 vs 3 -> stable) / ranking");
        System.out.println(m.getReportGenerator().dailySummary(LocalDate.now()));
        System.out.println(m.getReportGenerator().triageSummary());
    }

    private LogicTest() {
    }
}
