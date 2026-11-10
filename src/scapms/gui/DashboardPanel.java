package scapms.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import scapms.controller.ClinicManager;
import scapms.controller.RecentPatientCache;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.FollowUpPatient;
import scapms.model.PatientCategory;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/** Main dashboard: statistic cards plus today's schedule, recent patients and alerts. */
class DashboardPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final transient ClinicManager manager;
    private final JLabel total = valueLabel();
    private final JLabel general = valueLabel();
    private final JLabel emergency = valueLabel();
    private final JLabel followUp = valueLabel();
    private final JLabel todayAppts = valueLabel();
    private final JLabel pendingAppts = valueLabel();
    private final JLabel queueSize = valueLabel();
    private final JLabel longestWait = valueLabel();
    private final JLabel refreshed = new JLabel();
    private final DefaultListModel<String> todayModel = new DefaultListModel<>();
    private final DefaultListModel<String> recentModel = new DefaultListModel<>();
    private final DefaultListModel<String> alertModel = new DefaultListModel<>();

    DashboardPanel(ClinicManager manager) {
        super(new BorderLayout(10, 10));
        this.manager = manager;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Smart Clinic Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(GuiUtils.PRIMARY);
        refreshed.setToolTipText("The dashboard is refreshed automatically by a timer every 5 seconds");
        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(refreshed, BorderLayout.EAST);

        JPanel cards = new JPanel(new GridLayout(2, 4, 10, 10));   // grid of statistic cards
        cards.add(card("Total Patients", total, GuiUtils.PRIMARY));
        cards.add(card("General", general, GuiUtils.SUCCESS));
        cards.add(card("Emergency", emergency, GuiUtils.EMERGENCY_FG));
        cards.add(card("Follow-Up", followUp, GuiUtils.FOLLOWUP_FG));
        cards.add(card("Today's Appointments", todayAppts, GuiUtils.PRIMARY));
        cards.add(card("Pending Appointments", pendingAppts, GuiUtils.WARNING));
        cards.add(card("Waiting in Queue", queueSize, GuiUtils.PRIMARY));
        cards.add(card("Longest Wait (min)", longestWait, GuiUtils.WARNING));

        JPanel lists = new JPanel(new GridLayout(1, 3, 10, 0));
        lists.add(listBox("Today's Schedule", todayModel));
        lists.add(listBox("Frequently Accessed Patients (cache)", recentModel));
        lists.add(listBox("Alerts", alertModel));

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.add(cards, BorderLayout.NORTH);
        body.add(lists, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel("0");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 28f));
        return l;
    }

    private static JPanel card(String caption, JLabel value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, accent),
                BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12))));
        JLabel cap = new JLabel(caption);
        cap.setForeground(Color.DARK_GRAY);
        value.setForeground(accent);
        card.add(cap, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JScrollPane listBox(String title, DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setFont(GuiUtils.MONO.deriveFont(12f));
        JScrollPane scroll = new JScrollPane(list);
        GuiUtils.titled(scroll, title);
        return scroll;
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        total.setText(String.valueOf(manager.getDatabase().size()));
        general.setText(String.valueOf(manager.getDatabase().countByCategory(PatientCategory.GENERAL)));
        emergency.setText(String.valueOf(manager.getDatabase().countByCategory(PatientCategory.EMERGENCY)));
        followUp.setText(String.valueOf(manager.getDatabase().countByCategory(PatientCategory.FOLLOW_UP)));

        List<Appointment> todays = manager.getAppointmentController().getAppointmentsOn(today);
        todayAppts.setText(String.valueOf(todays.size()));
        pendingAppts.setText(String.valueOf(manager.getAppointmentController().getAppointments(AppointmentStatus.PENDING).size()));
        queueSize.setText(String.valueOf(manager.getQueueManager().size()));
        longestWait.setText(String.valueOf(manager.getQueueManager().getLongestWaitMinutes()));

        todayModel.clear();
        for (Appointment a : todays) {
            PatientRecord p = manager.findPatient(a.getPatientId());
            todayModel.addElement(String.format("%s %-16s %s%s", a.getTime().format(Validator.TIME_FORMAT),
                    p == null ? a.getPatientId() : p.getName(), a.getStatus(), a.isUrgent() ? " !" : ""));
        }
        if (todayModel.isEmpty()) {
            todayModel.addElement("No appointments today.");
        }

        recentModel.clear();
        RecentPatientCache cache = manager.getRecentCache();
        for (int i = 0; i < cache.size(); i++) {
            PatientRecord p = cache.getSlot(i);
            recentModel.addElement(String.format("%s %-18s %d view(s)", p.getPatientId(), p.getName(), cache.getAccessCount(i)));
        }
        if (recentModel.isEmpty()) {
            recentModel.addElement("Open a patient record to fill the cache.");
        }

        alertModel.clear();
        int longWaits = manager.getQueueManager().countWaitingLongerThan(MainWindow.WAIT_ALERT_MINUTES);
        if (longWaits > 0) {
            alertModel.addElement("⚠ " + longWaits + " patient(s) waiting > " + MainWindow.WAIT_ALERT_MINUTES + " min");
        }
        for (Appointment a : manager.getAppointmentController().getUpcomingAppointments(LocalDateTime.now(),
                MainWindow.REMINDER_LEAD_MINUTES)) {
            alertModel.addElement("⏰ " + a.getTime().format(Validator.TIME_FORMAT) + " " + a.getAppointmentId()
                    + " (" + a.getPatientId() + ") soon");
        }
        for (PatientRecord p : manager.getDatabase().getPatients()) {
            if (p instanceof FollowUpPatient && ((FollowUpPatient) p).isOverdue()) {
                alertModel.addElement("Follow-up overdue: " + p.getPatientId() + " " + p.getName());
            }
        }
        if (alertModel.isEmpty()) {
            alertModel.addElement("No alerts.");
        }
        refreshed.setText("Last refreshed " + LocalDateTime.now().format(TIME));
    }
}
