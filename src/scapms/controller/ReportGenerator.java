package scapms.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.PatientCategory;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/** Produces the plain-text clinic reports. */
public class ReportGenerator {

    public enum ReportType {
        MOST_FREQUENT("Most Frequently Attended Patients"),
        PENDING("Patients with Pending Appointments"),
        CATEGORY("Patient Category Distribution"),
        DAILY("Daily Appointment Summary"),
        TRIAGE("Triage & Consultation Fee Summary");

        private final String title;

        ReportType(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TOP_N = 10;

    private final ClinicManager manager;

    ReportGenerator(ClinicManager manager) {
        this.manager = manager;
    }

    public String generate(ReportType type, LocalDate date) {
        switch (type) {
            case MOST_FREQUENT:
                return mostFrequentlyAttended();
            case PENDING:
                return patientsWithPendingAppointments();
            case CATEGORY:
                return categoryDistribution();
            case DAILY:
                return dailySummary(date == null ? LocalDate.now() : date);
            case TRIAGE:
            default:
                return triageSummary();
        }
    }

    private String header(String title) {
        String line = "=".repeat(76);
        return line + "\n SCAPMS REPORT: " + title.toUpperCase() + "\n Generated: "
                + LocalDateTime.now().format(STAMP) + "\n" + line + "\n\n";
    }

    // ---------------------------------------------------------------- report 1

    public String mostFrequentlyAttended() {
        List<PatientRecord> list = new ArrayList<>();
        for (PatientRecord p : manager.getDatabase().getPatients()) {
            if (p.countAppointments(AppointmentStatus.COMPLETED) > 0) {
                list.add(p);
            }
        }
        // hand-written insertion sort: most completed appointments first
        for (int i = 1; i < list.size(); i++) {
            PatientRecord key = list.get(i);
            int keyCount = key.countAppointments(AppointmentStatus.COMPLETED);
            int j = i - 1;
            while (j >= 0 && list.get(j).countAppointments(AppointmentStatus.COMPLETED) < keyCount) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
        StringBuilder sb = new StringBuilder(header(ReportType.MOST_FREQUENT.toString()));
        if (list.isEmpty()) {
            return sb.append("No completed appointments have been recorded yet.\n").toString();
        }
        sb.append(String.format("%-5s %-7s %-24s %-10s %10s %8s%n", "Rank", "ID", "Name", "Category", "Completed", "Total"));
        sb.append("-".repeat(70)).append('\n');
        for (int i = 0; i < list.size() && i < TOP_N; i++) {
            PatientRecord p = list.get(i);
            sb.append(String.format("%-5d %-7s %-24s %-10s %10d %8d%n", i + 1, p.getPatientId(), p.getName(),
                    p.getCategory(), p.countAppointments(AppointmentStatus.COMPLETED), p.getAppointments().size()));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- report 2

    public String patientsWithPendingAppointments() {
        StringBuilder sb = new StringBuilder(header(ReportType.PENDING.toString()));
        int patients = 0;
        int appointments = 0;
        for (PatientRecord p : manager.getDatabase().getPatients()) {
            if (!p.hasPendingAppointments()) {
                continue;
            }
            patients++;
            sb.append(String.format("%s  %-24s [%s]  phone %s%n", p.getPatientId(), p.getName(), p.getCategory(), p.getPhone()));
            List<Appointment> pending = new ArrayList<>();
            for (Appointment a : p.getAppointments()) {
                if (a.isPending()) {
                    pending.add(a);
                }
            }
            AppointmentController.sortByDateTime(pending);
            for (Appointment a : pending) {
                appointments++;
                sb.append(String.format("      %s  %s %s  %-18s %s%s%n", a.getAppointmentId(),
                        a.getDate().format(Validator.DATE_FORMAT), a.getTime().format(Validator.TIME_FORMAT),
                        a.getDoctor(), a.getReason(), a.isUrgent() ? "  [URGENT]" : ""));
            }
        }
        if (patients == 0) {
            sb.append("No patient has a pending appointment.\n");
        } else {
            sb.append("\nPatients with pending appointments: ").append(patients)
              .append("\nPending appointments in total     : ").append(appointments).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- report 3

    public String categoryDistribution() {
        StringBuilder sb = new StringBuilder(header(ReportType.CATEGORY.toString()));
        int total = manager.getDatabase().size();
        sb.append(String.format("%-22s %6s %8s %9s  %s%n", "Category", "Count", "Percent", "Avg. age", "Chart"));
        sb.append("-".repeat(76)).append('\n');
        for (PatientCategory c : PatientCategory.values()) {
            int count = 0;
            int ageSum = 0;
            for (PatientRecord p : manager.getDatabase().getPatients()) {
                if (p.getCategory() == c) {
                    count++;
                    ageSum += p.getAge();
                }
            }
            double percent = total == 0 ? 0 : 100.0 * count / total;
            String avgAge = count == 0 ? "-" : String.format("%.1f", (double) ageSum / count);
            sb.append(String.format("%-22s %6d %7.1f%% %9s  %s%n", c.getPluralLabel(), count, percent, avgAge,
                    "#".repeat((int) Math.round(percent / 4))));
        }
        sb.append("-".repeat(76)).append('\n');
        sb.append(String.format("%-22s %6d%n", "TOTAL", total));
        return sb.toString();
    }

    // ---------------------------------------------------------------- report 4

    public String dailySummary(LocalDate date) {
        StringBuilder sb = new StringBuilder(header(ReportType.DAILY + " - " + date.format(Validator.DATE_FORMAT)));
        List<Appointment> day = manager.getAppointmentController().getAppointmentsOn(date);
        int completed = 0;
        int pending = 0;
        int cancelled = 0;
        for (Appointment a : day) {
            switch (a.getStatus()) {
                case COMPLETED: completed++; break;
                case PENDING: pending++; break;
                default: cancelled++; break;
            }
        }
        sb.append("Total Appointments: ").append(day.size()).append('\n');
        sb.append("Completed: ").append(completed).append('\n');
        sb.append("Pending: ").append(pending).append('\n');
        sb.append("Cancelled: ").append(cancelled).append('\n');
        if (!day.isEmpty()) {
            sb.append(String.format("Completion rate: %.0f%%%n", 100.0 * completed / day.size()));
            sb.append("\nSchedule:\n");
            sb.append(String.format("  %-6s %-6s %-7s %-22s %-18s %s%n", "Time", "Appt", "Patient", "Name", "Doctor", "Status"));
            for (Appointment a : day) {
                PatientRecord p = manager.findPatient(a.getPatientId());
                sb.append(String.format("  %-6s %-6s %-7s %-22s %-18s %s%s%n", a.getTime().format(Validator.TIME_FORMAT),
                        a.getAppointmentId(), a.getPatientId(), p == null ? "?" : p.getName(), a.getDoctor(),
                        a.getStatus(), a.isUrgent() ? " (urgent)" : ""));
            }
        }
        sb.append("\nPatients currently waiting in queue: ").append(manager.getQueueManager().size()).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- report 5

    /** Uses PatientProcessor, i.e. runtime polymorphism over a list of base-class references. */
    public String triageSummary() {
        return header(ReportType.TRIAGE.toString())
                + "Every row below is produced by the SAME function, PatientProcessor.processPatient(PatientRecord),\n"
                + "but priority and fee come from each subclass's own overridden methods (dynamic dispatch).\n\n"
                + PatientProcessor.processAll(manager.getDatabase().getPatients());
    }
}
