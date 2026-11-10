package scapms.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scapms.exception.ClinicException;
import scapms.exception.FileLoadException;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.PatientCategory;
import scapms.model.PatientFactory;
import scapms.model.PatientRecord;
import scapms.model.QueueEntry;

/**
 * Saves and loads clinic data as a pipe-delimited text file, and exports CSV / reports.
 *
 * <pre>
 * # SCAPMS-DATA v1
 * PATIENT|EMERGENCY|P0002|Tunde Bakare|34|08031234567|t@x.com|2026-01-10|severity=4|condition=Asthma
 * APPOINTMENT|A0001|P0002|2026-11-10|09:30|Dr. Eze|Review|PENDING|true
 * QUEUE|P0002|2026-11-10T09:12:00
 * </pre>
 */
public final class FileHandler {

    public static final String HEADER = "# SCAPMS-DATA v1";

    /** Everything read from a data file. */
    public static final class LoadedData {
        private final List<PatientRecord> patients;
        private final List<QueueEntry> queue;

        LoadedData(List<PatientRecord> patients, List<QueueEntry> queue) {
            this.patients = patients;
            this.queue = queue;
        }

        public List<PatientRecord> getPatients() {
            return patients;
        }

        public List<QueueEntry> getQueue() {
            return queue;
        }
    }

    private FileHandler() {
    }

    // ===================================================================== save

    public static void save(File file, List<PatientRecord> patients, List<QueueEntry> queue) throws IOException {
        File target = file.getAbsoluteFile();
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create folder " + parent);
        }
        // write to a temporary file first so a failure never corrupts the existing file
        File temp = new File(target.getPath() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            w.write(HEADER);
            w.newLine();
            w.write("# Saved " + LocalDateTime.now().withNano(0) + " - " + patients.size() + " patients");
            w.newLine();
            w.write("# PATIENT|category|id|name|age|phone|email|registered|key=value...");
            w.newLine();
            for (PatientRecord p : patients) {
                List<String> f = new ArrayList<>();
                f.add("PATIENT");
                f.add(p.getCategory().name());
                f.add(p.getPatientId());
                f.add(p.getName());
                f.add(String.valueOf(p.getAge()));
                f.add(p.getPhone());
                f.add(p.getEmail());
                f.add(p.getRegistrationDate().format(Validator.DATE_FORMAT));
                for (Map.Entry<String, String> e : p.getExtraAttributes().entrySet()) {
                    f.add(e.getKey() + "=" + e.getValue());
                }
                writeRecord(w, f);
            }
            w.write("# APPOINTMENT|id|patientId|date|time|doctor|reason|status|urgent");
            w.newLine();
            for (PatientRecord p : patients) {
                for (Appointment a : p.getAppointments()) {
                    List<String> f = new ArrayList<>();
                    f.add("APPOINTMENT");
                    f.add(a.getAppointmentId());
                    f.add(a.getPatientId());
                    f.add(a.getDate().format(Validator.DATE_FORMAT));
                    f.add(a.getTime().format(Validator.TIME_FORMAT));
                    f.add(a.getDoctor());
                    f.add(a.getReason());
                    f.add(a.getStatus().name());
                    f.add(String.valueOf(a.isUrgent()));
                    writeRecord(w, f);
                }
            }
            w.write("# QUEUE|patientId|joinedAt");
            w.newLine();
            for (QueueEntry q : queue) {
                List<String> f = new ArrayList<>();
                f.add("QUEUE");
                f.add(q.getPatientId());
                f.add(q.getJoinedAt().withNano(0).toString());
                writeRecord(w, f);
            }
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeRecord(BufferedWriter w, List<String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(escape(fields.get(i)));
        }
        w.write(sb.toString());
        w.newLine();
    }

    // ===================================================================== load

    public static LoadedData load(File file) throws FileLoadException {
        if (file == null) {
            throw new FileLoadException("No file was selected.");
        }
        if (!file.exists()) {
            throw new FileLoadException("File not found: " + file.getPath());
        }
        if (!file.isFile() || !file.canRead()) {
            throw new FileLoadException("Cannot read file: " + file.getPath());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException e) {
            throw new FileLoadException("Could not read '" + file.getName() + "': " + e.getMessage(), e);
        }
        if (lines.isEmpty() || !lines.get(0).trim().startsWith(HEADER)) {
            throw new FileLoadException("'" + file.getName() + "' is not a SCAPMS data file (missing '" + HEADER + "' header).");
        }

        Map<String, PatientRecord> patientsById = new LinkedHashMap<>();
        Set<String> appointmentIds = new HashSet<>();
        List<QueueEntry> queue = new ArrayList<>();
        Set<String> queued = new HashSet<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            try {
                List<String> f = split(line);
                switch (f.get(0)) {
                    case "PATIENT": {
                        PatientRecord p = parsePatient(f);
                        if (patientsById.containsKey(p.getPatientId())) {
                            throw new FileLoadException("Duplicate patient ID " + p.getPatientId() + ".");
                        }
                        patientsById.put(p.getPatientId(), p);
                        break;
                    }
                    case "APPOINTMENT": {
                        Appointment a = parseAppointment(f);
                        PatientRecord owner = patientsById.get(a.getPatientId());
                        if (owner == null) {
                            throw new FileLoadException("Appointment " + a.getAppointmentId()
                                    + " refers to unknown patient " + a.getPatientId() + ".");
                        }
                        if (!appointmentIds.add(a.getAppointmentId())) {
                            throw new FileLoadException("Duplicate appointment ID " + a.getAppointmentId() + ".");
                        }
                        owner.attachAppointment(a);
                        break;
                    }
                    case "QUEUE": {
                        requireFields(f, 3, "QUEUE");
                        String pid = f.get(1);
                        if (!patientsById.containsKey(pid)) {
                            throw new FileLoadException("Queue entry refers to unknown patient " + pid + ".");
                        }
                        if (queued.add(pid)) {
                            queue.add(new QueueEntry(pid, parseDateTime(f.get(2), "queue time")));
                        }
                        break;
                    }
                    default:
                        throw new FileLoadException("Unknown record type '" + f.get(0) + "'.");
                }
            } catch (ClinicException | RuntimeException e) {
                throw new FileLoadException("Error in '" + file.getName() + "' at line " + (i + 1) + ": "
                        + e.getMessage(), e);
            }
        }
        return new LoadedData(new ArrayList<>(patientsById.values()), queue);
    }

    private static PatientRecord parsePatient(List<String> f) throws ClinicException {
        requireFields(f, 8, "PATIENT");
        PatientCategory category;
        try {
            category = PatientCategory.valueOf(f.get(1));
        } catch (IllegalArgumentException e) {
            throw new FileLoadException("Unknown patient category '" + f.get(1) + "'.");
        }
        int age = parseInt(f.get(4), "age");
        LocalDate registered = parseDate(f.get(7), "registration date");
        Map<String, String> extras = new LinkedHashMap<>();
        for (int i = 8; i < f.size(); i++) {
            String pair = f.get(i);
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new FileLoadException("Invalid attribute '" + pair + "' (expected key=value).");
            }
            extras.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return PatientFactory.create(category, f.get(2), f.get(3), age, f.get(5), f.get(6), registered, extras);
    }

    private static Appointment parseAppointment(List<String> f) throws ClinicException {
        requireFields(f, 9, "APPOINTMENT");
        LocalDate date = parseDate(f.get(3), "appointment date");
        LocalTime time = Validator.tryParseTime(f.get(4));
        if (time == null) {
            throw new FileLoadException("Invalid appointment time '" + f.get(4) + "'.");
        }
        Appointment a = new Appointment(f.get(1), f.get(2), date, time, f.get(5), f.get(6));
        try {
            a.setStatus(AppointmentStatus.valueOf(f.get(7)));
        } catch (IllegalArgumentException e) {
            throw new FileLoadException("Unknown appointment status '" + f.get(7) + "'.");
        }
        a.setUrgent(Boolean.parseBoolean(f.get(8)));
        return a;
    }

    private static void requireFields(List<String> f, int count, String type) throws FileLoadException {
        if (f.size() < count) {
            throw new FileLoadException(type + " record has " + f.size() + " fields; at least " + count + " expected.");
        }
    }

    private static int parseInt(String text, String what) throws FileLoadException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new FileLoadException("Invalid " + what + " '" + text + "'.");
        }
    }

    private static LocalDate parseDate(String text, String what) throws FileLoadException {
        LocalDate d = Validator.tryParseDate(text);
        if (d == null) {
            throw new FileLoadException("Invalid " + what + " '" + text + "'.");
        }
        return d;
    }

    private static LocalDateTime parseDateTime(String text, String what) throws FileLoadException {
        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new FileLoadException("Invalid " + what + " '" + text + "'.");
        }
    }

    // ============================================================ export/report

    public static void exportPatientsCsv(File file, List<PatientRecord> patients) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write("PatientID,Name,Age,Category,Priority,Phone,Email,Registered,ConsultationFee,"
                    + "PendingAppointments,TotalAppointments,Details");
            w.newLine();
            for (PatientRecord p : patients) {
                StringBuilder details = new StringBuilder();
                for (Map.Entry<String, String> e : p.getExtraAttributes().entrySet()) {
                    if (details.length() > 0) {
                        details.append("; ");
                    }
                    details.append(e.getKey()).append('=').append(e.getValue());
                }
                String[] cells = {
                    p.getPatientId(), p.getName(), String.valueOf(p.getAge()), p.getCategory().toString(),
                    p.getPriorityLabel(), p.getPhone(), p.getEmail(),
                    p.getRegistrationDate().format(Validator.DATE_FORMAT),
                    String.format("%.2f", p.calculateConsultationFee()),
                    String.valueOf(p.countAppointments(AppointmentStatus.PENDING)),
                    String.valueOf(p.getAppointments().size()), details.toString()
                };
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < cells.length; i++) {
                    if (i > 0) {
                        row.append(',');
                    }
                    row.append(csv(cells[i]));
                }
                w.write(row.toString());
                w.newLine();
            }
        }
    }

    public static void writeText(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================= helpers

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '|': sb.append("\\|"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                current.append(next == 'n' ? '\n' : next);
            } else if (c == '|') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
