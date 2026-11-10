package scapms.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import scapms.exception.ClinicException;
import scapms.exception.DuplicatePatientException;
import scapms.exception.FileLoadException;
import scapms.exception.InvalidPatientException;
import scapms.exception.InvalidSearchException;
import scapms.exception.PatientNotFoundException;
import scapms.model.ClinicDatabase;
import scapms.model.PatientField;
import scapms.model.PatientRecord;
import scapms.model.QueueEntry;
import scapms.util.FileHandler;
import scapms.util.IDGenerator;
import scapms.util.SampleData;

/**
 * Central business-logic controller. The GUI never manipulates the data directly:
 * it calls ClinicManager, which coordinates the database, the waiting queue, the
 * undo stack, the recent-patient cache and persistence, then notifies listeners.
 */
public class ClinicManager {

    private final ClinicDatabase database = new ClinicDatabase();
    private final QueueManager queueManager = new QueueManager();
    private final UndoManager undoManager = new UndoManager();
    private final RecentPatientCache recentCache = new RecentPatientCache();
    private final IDGenerator idGenerator = new IDGenerator();
    private final SearchEngine searchEngine = new SearchEngine();
    private final SortEngine sortEngine = new SortEngine();
    private final AppointmentController appointmentController = new AppointmentController(this);
    private final ReportGenerator reportGenerator = new ReportGenerator(this);

    private final List<Runnable> changeListeners = new ArrayList<>();
    private File currentFile;
    private boolean modified;

    // ================================================================ listeners

    /** Registers a callback run after every data change (observer pattern). */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChange() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }

    void markModified() {
        modified = true;
        fireChange();
    }

    // ========================================================= patient records

    public void registerPatient(PatientRecord patient) throws ClinicException {
        if (patient == null) {
            throw new InvalidPatientException("No patient information was supplied.");
        }
        if (database.containsId(patient.getPatientId())) {
            throw new DuplicatePatientException(patient.getPatientId());
        }
        database.add(patient);
        recentCache.recordAccess(patient);
        final String id = patient.getPatientId();
        undoManager.record(new AdminOperation(AdminOperation.Type.REGISTER_PATIENT,
                "Registered " + id + " (" + patient.getName() + ")",
                () -> {
                    PatientRecord current = requirePatient(id);
                    database.remove(current);
                    recentCache.remove(id);
                    queueManager.remove(id);
                }));
        markModified();
    }

    /** Replaces a patient's record with an edited version (the ID cannot change). */
    public void updatePatient(String patientId, PatientRecord updated) throws ClinicException {
        PatientRecord original = requirePatient(patientId);
        if (updated == null) {
            throw new InvalidPatientException("No patient information was supplied.");
        }
        if (!original.getPatientId().equals(updated.getPatientId())) {
            throw new InvalidPatientException("The Patient ID cannot be changed while editing.");
        }
        updated.replaceAppointmentsWith(original);        // appointments stay with the patient
        database.replace(original, updated);
        recentCache.replace(original, updated);
        recentCache.recordAccess(updated);
        undoManager.record(new AdminOperation(AdminOperation.Type.MODIFY_PATIENT,
                "Modified " + patientId + " (" + original.getName() + ")",
                () -> {
                    PatientRecord current = requirePatient(patientId);
                    original.replaceAppointmentsWith(current);
                    database.replace(current, original);
                    recentCache.replace(current, original);
                }));
        markModified();
    }

    public void deletePatient(String patientId) throws ClinicException {
        PatientRecord patient = requirePatient(patientId);
        int queuePosition = queueManager.positionOf(patient.getPatientId());
        QueueEntry queueEntry = queueManager.remove(patient.getPatientId());
        int index = database.remove(patient);
        recentCache.remove(patient.getPatientId());
        undoManager.record(new AdminOperation(AdminOperation.Type.DELETE_PATIENT,
                "Deleted " + patient.getPatientId() + " (" + patient.getName() + ")",
                () -> {
                    if (database.containsId(patient.getPatientId())) {
                        throw new DuplicatePatientException(patient.getPatientId());
                    }
                    database.insert(index, patient);
                    if (queueEntry != null) {
                        queueManager.insertAt(queuePosition, queueEntry);
                    }
                }));
        markModified();
    }

    /** Linear search by ID (the patient list is generally unsorted). */
    public PatientRecord findPatient(String patientId) {
        if (patientId == null) {
            return null;
        }
        return searchEngine.linearSearchById(database.getPatients(), patientId.trim().toUpperCase());
    }

    public PatientRecord requirePatient(String patientId) throws PatientNotFoundException {
        PatientRecord p = findPatient(patientId);
        if (p == null) {
            throw new PatientNotFoundException(patientId);
        }
        return p;
    }

    /** Called when a user views a record; updates the fixed-size array cache. */
    public void recordAccess(PatientRecord patient) {
        recentCache.recordAccess(patient);
    }

    // ============================================================ waiting queue

    public void addToQueue(String patientId) throws ClinicException {
        PatientRecord p = requirePatient(patientId);
        queueManager.enqueue(p.getPatientId());
        markModified();
    }

    public QueueEntry callNextPatient() throws ClinicException {
        QueueEntry next = queueManager.callNext();
        markModified();
        return next;
    }

    public void clearQueue() {
        queueManager.clear();
        markModified();
    }

    // ===================================================================== undo

    public AdminOperation undoLastOperation() throws ClinicException {
        try {
            AdminOperation op = undoManager.undoLast();
            modified = true;
            return op;
        } finally {
            fireChange();
        }
    }

    public void clearUndoHistory() {
        undoManager.clear();
        fireChange();
    }

    // ========================================================= search and sort

    public SearchEngine.SearchResult searchPatients(String query, SearchEngine.Field field,
                                                    SearchEngine.Algorithm algorithm) throws InvalidSearchException {
        return searchEngine.search(database, query, field, algorithm);
    }

    public SortEngine.SortResult sortPatients(SortEngine.Algorithm algorithm, PatientField field, boolean ascending) {
        SortEngine.SortResult result = sortEngine.sort(database, algorithm, field, ascending);
        fireChange();
        return result;
    }

    // ============================================================== persistence

    public void newClinic() {
        database.clear();
        queueManager.clear();
        undoManager.clear();
        recentCache.clear();
        idGenerator.reset();
        currentFile = null;
        modified = false;
        fireChange();
    }

    public void save(File file) throws ClinicException {
        if (file == null) {
            throw new ClinicException("No file was selected.");
        }
        try {
            FileHandler.save(file, database.getPatients(), queueManager.snapshot());
        } catch (IOException e) {
            throw new ClinicException("Could not save to '" + file.getName() + "': " + e.getMessage(), e);
        }
        currentFile = file;
        modified = false;
        fireChange();
    }

    /** Loads a data file. Current data is only replaced if the whole file is valid. */
    public void load(File file) throws FileLoadException {
        FileHandler.LoadedData data = FileHandler.load(file);
        replaceAll(data.getPatients(), data.getQueue());
        currentFile = file;
        modified = false;
        fireChange();
    }

    public void loadSampleData() {
        replaceAll(SampleData.createPatients(), SampleData.createQueue());
        modified = true;
        fireChange();
    }

    public void exportCsv(File file) throws ClinicException {
        try {
            FileHandler.exportPatientsCsv(file, database.getPatients());
        } catch (IOException e) {
            throw new ClinicException("Could not export to '" + file.getName() + "': " + e.getMessage(), e);
        }
    }

    private void replaceAll(List<PatientRecord> patients, List<QueueEntry> queue) {
        database.clear();
        for (PatientRecord p : patients) {
            database.add(p);
        }
        List<QueueEntry> validQueue = new ArrayList<>();
        for (QueueEntry e : queue) {
            if (database.containsId(e.getPatientId())) {
                validQueue.add(e);
            }
        }
        queueManager.restore(validQueue);
        undoManager.clear();
        recentCache.clear();
        idGenerator.synchronise(database);
    }

    // ================================================================= getters

    public ClinicDatabase getDatabase() {
        return database;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public RecentPatientCache getRecentCache() {
        return recentCache;
    }

    public IDGenerator getIdGenerator() {
        return idGenerator;
    }

    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public AppointmentController getAppointmentController() {
        return appointmentController;
    }

    public ReportGenerator getReportGenerator() {
        return reportGenerator;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    public boolean isModified() {
        return modified;
    }
}
