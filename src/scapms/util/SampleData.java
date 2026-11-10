package scapms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import scapms.exception.ClinicException;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.EmergencyPatient;
import scapms.model.FollowUpPatient;
import scapms.model.GeneralPatient;
import scapms.model.PatientRecord;
import scapms.model.QueueEntry;

/**
 * Builds a realistic demonstration data set whose dates are relative to today, so the
 * dashboard, reminders and reports always have something meaningful to show.
 */
public final class SampleData {

    private static final String DR_EZE = "Dr. Ngozi Eze";
    private static final String DR_ADE = "Dr. Samuel Ade";
    private static final String DR_LAWAL = "Dr. Aisha Lawal";

    private SampleData() {
    }

    public static List<PatientRecord> createPatients() {
        LocalDate t = LocalDate.now();
        try {
            GeneralPatient p1 = new GeneralPatient("P0001", "Adaeze Okafor", 21, "08031234567", "adaeze.okafor@uni.edu", t.minusDays(120), "NHIS");
            EmergencyPatient p2 = new EmergencyPatient("P0002", "Tunde Bakare", 34, "08022223333", "tunde.bakare@uni.edu", t.minusDays(3), 4, "Severe asthma attack");
            GeneralPatient p3 = new GeneralPatient("P0003", "Grace Mensah", 19, "08134567890", "grace.mensah@uni.edu", t.minusDays(60), "None");
            FollowUpPatient p4 = new FollowUpPatient("P0004", "Ibrahim Musa", 45, "07012345678", "ibrahim.musa@uni.edu", t.minusDays(200), t.minusDays(10), DR_EZE, 14);
            GeneralPatient p5 = new GeneralPatient("P0005", "Chloe Adams", 23, "09087654321", "chloe.adams@uni.edu", t.minusDays(45), "AXA Mansard");
            EmergencyPatient p6 = new EmergencyPatient("P0006", "Emeka Nwosu", 28, "08055556666", "", t.minusDays(1), 2, "Sprained ankle (sports injury)");
            FollowUpPatient p7 = new FollowUpPatient("P0007", "Fatima Bello", 52, "08177778888", "fatima.bello@uni.edu", t.minusDays(300), t.minusDays(30), DR_ADE, 30);
            GeneralPatient p8 = new GeneralPatient("P0008", "David Johnson", 20, "08099990000", "david.j@uni.edu", t.minusDays(15), "None");
            EmergencyPatient p9 = new EmergencyPatient("P0009", "Zainab Yusuf", 18, "08144445555", "zainab.yusuf@uni.edu", t, 5, "Allergic reaction (anaphylaxis)");
            FollowUpPatient p10 = new FollowUpPatient("P0010", "Kemi Adeyemi", 61, "08066667777", "kemi.adeyemi@uni.edu", t.minusDays(400), t.minusDays(7), DR_EZE, 7);
            GeneralPatient p11 = new GeneralPatient("P0011", "Michael Obi", 25, "07033334444", "michael.obi@uni.edu", t.minusDays(90), "NHIS");
            FollowUpPatient p12 = new FollowUpPatient("P0012", "Halima Sani", 37, "09011112222", "", t.minusDays(150), t.minusDays(21), DR_ADE, 21);
            GeneralPatient p13 = new GeneralPatient("P0013", "Samuel Etim", 22, "08188889999", "samuel.etim@uni.edu", t.minusDays(30), "None");
            EmergencyPatient p14 = new EmergencyPatient("P0014", "Blessing Okon", 30, "08100001111", "blessing.okon@uni.edu", t.minusDays(2), 3, "High fever and dehydration");
            GeneralPatient p15 = new GeneralPatient("P0015", "Yusuf Garba", 40, "08122223333", "yusuf.garba@uni.edu", t.minusDays(75), "Hygeia HMO");

            // an appointment ~20 minutes from now so the reminder timer has something to announce
            LocalDateTime soon = LocalDateTime.now().plusMinutes(20).withSecond(0).withNano(0);

            add(p1, "A0001", t.minusDays(90), "09:00", DR_LAWAL, "Malaria symptoms", AppointmentStatus.COMPLETED);
            add(p1, "A0002", t.minusDays(40), "10:30", DR_LAWAL, "Malaria review", AppointmentStatus.COMPLETED);
            add(p1, "A0003", t.minusDays(5), "11:00", DR_EZE, "Headache", AppointmentStatus.COMPLETED);
            add(p1, "A0004", t.plusDays(2), "09:30", DR_LAWAL, "General check-up", AppointmentStatus.PENDING);
            add(p2, "A0005", t, "08:15", DR_ADE, "Asthma - nebulisation", AppointmentStatus.COMPLETED);
            add(p2, "A0006", soon.toLocalDate(), soon.toLocalTime().toString(), DR_ADE, "Asthma review", AppointmentStatus.PENDING);
            add(p3, "A0007", t.minusDays(20), "14:00", DR_LAWAL, "Skin rash", AppointmentStatus.COMPLETED);
            add(p3, "A0008", t.plusDays(1), "10:00", DR_LAWAL, "Rash review", AppointmentStatus.PENDING);
            add(p4, "A0009", t.minusDays(10), "13:00", DR_EZE, "Blood pressure", AppointmentStatus.COMPLETED);
            add(p4, "A0010", t.plusDays(4), "13:00", DR_EZE, "BP follow-up", AppointmentStatus.PENDING);
            add(p5, "A0011", t.minusDays(12), "15:30", DR_ADE, "Sports physical", AppointmentStatus.CANCELLED);
            add(p5, "A0012", t, "15:30", DR_ADE, "Sports physical", AppointmentStatus.PENDING);
            add(p6, "A0013", t, "09:45", DR_LAWAL, "Ankle X-ray", AppointmentStatus.COMPLETED);
            add(p7, "A0014", t.minusDays(30), "11:30", DR_ADE, "Diabetes check", AppointmentStatus.COMPLETED);
            add(p7, "A0015", t.minusDays(60), "11:30", DR_ADE, "Diabetes check", AppointmentStatus.COMPLETED);
            add(p7, "A0016", t.minusDays(90), "11:30", DR_ADE, "Diabetes check", AppointmentStatus.COMPLETED);
            add(p7, "A0017", t, "16:00", DR_ADE, "Diabetes review", AppointmentStatus.PENDING);
            add(p8, "A0018", t, "12:00", DR_EZE, "Fever", AppointmentStatus.CANCELLED);
            add(p9, "A0019", t, "07:40", DR_EZE, "Emergency allergy treatment", AppointmentStatus.COMPLETED);
            add(p9, "A0020", t.plusDays(1), "08:30", DR_EZE, "Allergy review", AppointmentStatus.PENDING);
            add(p10, "A0021", t.minusDays(7), "10:00", DR_EZE, "Post-surgery check", AppointmentStatus.COMPLETED);
            add(p10, "A0022", t.minusDays(14), "10:00", DR_EZE, "Post-surgery check", AppointmentStatus.COMPLETED);
            add(p10, "A0023", t, "10:00", DR_EZE, "Post-surgery review", AppointmentStatus.PENDING);
            add(p11, "A0024", t.minusDays(3), "09:00", DR_LAWAL, "Eye strain", AppointmentStatus.COMPLETED);
            add(p11, "A0025", t.plusDays(6), "09:00", DR_LAWAL, "Eye test", AppointmentStatus.PENDING);
            add(p12, "A0026", t.minusDays(21), "14:30", DR_ADE, "Physiotherapy", AppointmentStatus.COMPLETED);
            add(p13, "A0027", t.plusDays(3), "11:15", DR_EZE, "Vaccination", AppointmentStatus.PENDING);
            add(p14, "A0028", t, "11:00", DR_LAWAL, "IV fluids", AppointmentStatus.COMPLETED);
            add(p14, "A0029", t.plusDays(1), "11:00", DR_LAWAL, "Fever review", AppointmentStatus.PENDING);
            add(p15, "A0030", t.minusDays(1), "16:30", DR_ADE, "Back pain", AppointmentStatus.CANCELLED);

            // deliberately NOT in ID order, so sorting has a visible effect
            List<PatientRecord> list = new ArrayList<>();
            PatientRecord[] order = {p7, p2, p13, p10, p1, p15, p9, p4, p11, p6, p3, p14, p8, p12, p5};
            for (PatientRecord p : order) {
                list.add(p);
            }
            return list;
        } catch (ClinicException e) {
            throw new IllegalStateException("Built-in sample data is invalid: " + e.getMessage(), e);
        }
    }

    public static List<QueueEntry> createQueue() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<QueueEntry> queue = new ArrayList<>();
        queue.add(new QueueEntry("P0012", now.minusMinutes(22)));
        queue.add(new QueueEntry("P0014", now.minusMinutes(9)));
        queue.add(new QueueEntry("P0003", now.minusMinutes(3)));
        return queue;
    }

    private static void add(PatientRecord p, String id, LocalDate date, String time, String doctor,
                            String reason, AppointmentStatus status) throws ClinicException {
        Appointment a = new Appointment(id, p.getPatientId(), date, LocalTime.parse(time), doctor, reason);
        a.setStatus(status);
        a.setUrgent(p instanceof EmergencyPatient);
        p.attachAppointment(a);
    }
}
