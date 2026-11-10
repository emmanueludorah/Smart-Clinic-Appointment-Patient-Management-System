package scapms.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import scapms.controller.AdminOperation;
import scapms.controller.ClinicManager;
import scapms.model.Appointment;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/**
 * The main application window. It only assembles the panels, menus, timers and
 * status bar; all business logic lives in {@link ClinicManager}.
 */
public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    static final int REMINDER_LEAD_MINUTES = 30;
    static final int WAIT_ALERT_MINUTES = 15;
    private static final String APP_TITLE = "SCAPMS - Smart Clinic Appointment & Patient Management System";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEE dd MMM yyyy  HH:mm:ss");

    private final transient ClinicManager manager;
    private final JTabbedPane tabs = new JTabbedPane();
    private final DashboardPanel dashboardPanel;
    private final PatientPanel patientPanel;
    private final AppointmentPanel appointmentPanel;
    private final QueuePanel queuePanel;
    private final SearchPanel searchPanel;
    private final AdminPanel adminPanel;
    private final ReportPanel reportPanel;
    private final Refreshable[] panels;

    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel queueStatus = new JLabel();
    private final JLabel clockLabel = new JLabel();
    private JMenuItem undoItem;

    private final Set<String> remindedAppointments = new HashSet<>();
    private final Timer clockTimer;
    private final Timer dashboardTimer;
    private final Timer reminderTimer;
    private final Timer waitingTimer;

    public MainWindow(ClinicManager manager) {
        super(APP_TITLE);
        this.manager = manager;

        dashboardPanel = new DashboardPanel(manager);
        patientPanel = new PatientPanel(manager, this);
        appointmentPanel = new AppointmentPanel(manager, this);
        queuePanel = new QueuePanel(manager, this);
        searchPanel = new SearchPanel(manager, this);
        adminPanel = new AdminPanel(manager, this);
        reportPanel = new ReportPanel(manager, this);
        panels = new Refreshable[] {dashboardPanel, patientPanel, appointmentPanel, queuePanel, searchPanel,
            adminPanel, reportPanel};

        tabs.addTab("Dashboard", null, dashboardPanel, "Clinic overview (Ctrl+1)");
        tabs.addTab("Patient Records", null, patientPanel, "View, add, edit and delete patients (Ctrl+2)");
        tabs.addTab("Appointments", null, appointmentPanel, "Schedule and cancel appointments (Ctrl+3)");
        tabs.addTab("Waiting Queue", null, queuePanel, "Manage the consultation queue (Ctrl+4)");
        tabs.addTab("Search & Sort", null, searchPanel, "Search and sort patient records (Ctrl+5)");
        tabs.addTab("Admin", null, adminPanel, "Undo stack and system information (Ctrl+6)");
        tabs.addTab("Reports", null, reportPanel, "Clinic reports (Ctrl+7)");

        setJMenuBar(buildMenuBar());
        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        // ---- events
        manager.addChangeListener(this::refreshAll);          // model -> view notifications
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() instanceof Refreshable) {
                ((Refreshable) tabs.getSelectedComponent()).refresh();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        // ---- timers (javax.swing.Timer fires on the event-dispatch thread)
        clockTimer = new Timer(1000, e -> clockLabel.setText(LocalDateTime.now().format(CLOCK)));
        dashboardTimer = new Timer(5000, e -> {
            dashboardPanel.refresh();
            adminPanel.refreshInfo();
        });
        reminderTimer = new Timer(60_000, e -> checkAppointmentReminders());
        reminderTimer.setInitialDelay(4000);
        waitingTimer = new Timer(30_000, e -> checkWaitingPatients());
        waitingTimer.setInitialDelay(2000);
        clockTimer.start();
        dashboardTimer.start();
        reminderTimer.start();
        waitingTimer.start();

        refreshAll();
        setSize(1240, 780);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
    }

    // ================================================================== menus

    private JMenuBar buildMenuBar() {
        int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(item("New", KeyEvent.VK_N, ctrl, "Start an empty clinic database", e -> newClinic()));
        file.add(item("Load...", KeyEvent.VK_O, ctrl, "Open a SCAPMS data file", e -> loadFile()));
        file.add(item("Save", KeyEvent.VK_S, ctrl, "Save to the current data file", e -> save()));
        file.add(item("Save As...", KeyEvent.VK_S, ctrl | InputEvent.SHIFT_DOWN_MASK, "Save to a new file", e -> saveAs()));
        file.addSeparator();
        file.add(item("Export Patients to CSV...", KeyEvent.VK_E, ctrl, "Export patient records for spreadsheets", e -> exportCsv()));
        file.add(item("Load Sample Data", 0, 0, "Replace current data with demonstration data", e -> loadSample()));
        file.addSeparator();
        file.add(item("Exit", KeyEvent.VK_Q, ctrl, "Close the application", e -> exitApplication()));

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        undoItem = item("Undo Last Admin Operation", KeyEvent.VK_Z, ctrl, "Reverse the most recent operation", e -> undo());
        edit.add(undoItem);
        edit.addSeparator();
        edit.add(item("Add Patient...", KeyEvent.VK_P, ctrl, "Register a new patient", e -> {
            tabs.setSelectedComponent(patientPanel);
            patientPanel.addPatient();
        }));
        edit.add(item("Find Patient", KeyEvent.VK_F, ctrl, "Go to Search & Sort", e -> focusSearch()));

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            final int index = i;
            view.add(item(tabs.getTitleAt(i), KeyEvent.VK_1 + i, ctrl, "Show the " + tabs.getTitleAt(i) + " tab",
                    e -> tabs.setSelectedIndex(index)));
        }

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(item("Keyboard Shortcuts", KeyEvent.VK_F1, 0, "List of shortcuts", e -> showShortcuts()));
        help.add(item("About", 0, 0, "About SCAPMS", e -> GuiUtils.showInfo(this, "About SCAPMS",
                "Smart Clinic Appointment & Patient Management System\nCOS202 Programming II - Lab Assessment\n"
                + "Java Swing implementation")));

        bar.add(file);
        bar.add(edit);
        bar.add(view);
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String text, int key, int modifiers, String tooltip, ActionListener l) {
        JMenuItem item = new JMenuItem(text);
        if (key != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        }
        item.setToolTipText(tooltip);
        item.addActionListener(l);
        return item;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.add(queueStatus);
        right.add(clockLabel);
        bar.add(statusLabel, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ================================================================ refresh

    private void refreshAll() {
        for (Refreshable p : panels) {
            p.refresh();
        }
        AdminOperation top = manager.getUndoManager().peek();
        undoItem.setEnabled(top != null);
        undoItem.setText(top == null ? "Undo Last Admin Operation" : "Undo: " + top.getDescription());
        updateTitle();
        updateQueueStatus();
    }

    private void updateTitle() {
        File f = manager.getCurrentFile();
        setTitle(APP_TITLE + " - " + (f == null ? "Untitled" : f.getName()) + (manager.isModified() ? " *" : ""));
    }

    private void updateQueueStatus() {
        int waiting = manager.getQueueManager().size();
        int late = manager.getQueueManager().countWaitingLongerThan(WAIT_ALERT_MINUTES);
        if (late > 0) {
            queueStatus.setForeground(GuiUtils.ERROR);
            queueStatus.setText("⚠ " + late + " of " + waiting + " waiting > " + WAIT_ALERT_MINUTES + " min");
        } else {
            queueStatus.setForeground(GuiUtils.PRIMARY);
            queueStatus.setText("Queue: " + waiting + " waiting");
        }
        tabs.setTitleAt(3, waiting == 0 ? "Waiting Queue" : "Waiting Queue (" + waiting + ")");
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    // ================================================================= timers

    /** Timer event: pops up a reminder for appointments starting soon (once each). */
    private void checkAppointmentReminders() {
        List<Appointment> upcoming = manager.getAppointmentController()
                .getUpcomingAppointments(LocalDateTime.now(), REMINDER_LEAD_MINUTES);
        StringBuilder sb = new StringBuilder();
        for (Appointment a : upcoming) {
            if (remindedAppointments.add(a.getAppointmentId())) {
                PatientRecord p = manager.findPatient(a.getPatientId());
                sb.append(a.getTime().format(Validator.TIME_FORMAT)).append("  ").append(a.getAppointmentId())
                  .append("  ").append(p == null ? a.getPatientId() : p.getName())
                  .append(a.getDoctor().isEmpty() ? "" : "  with " + a.getDoctor())
                  .append(a.isUrgent() ? "  [URGENT]" : "").append('\n');
            }
        }
        if (sb.length() > 0) {
            setStatus("Reminder: appointment(s) starting within " + REMINDER_LEAD_MINUTES + " minutes");
            JOptionPane pane = new JOptionPane("Appointments starting within the next " + REMINDER_LEAD_MINUTES
                    + " minutes:\n\n" + sb, JOptionPane.INFORMATION_MESSAGE);
            JDialog dialog = pane.createDialog(this, "Appointment Reminder");
            dialog.setModal(false);                    // do not block the user
            dialog.setVisible(true);
        }
    }

    /** Timer event: refreshes waiting times and warns about long waits. */
    private void checkWaitingPatients() {
        queuePanel.refreshQueueView();
        updateQueueStatus();
        int late = manager.getQueueManager().countWaitingLongerThan(WAIT_ALERT_MINUTES);
        if (late > 0) {
            setStatus("Alert: " + late + " patient(s) have waited more than " + WAIT_ALERT_MINUTES + " minutes");
        }
    }

    // ================================================================ actions

    void undo() {
        AdminOperation[] undone = new AdminOperation[1];
        if (GuiUtils.run(this, () -> undone[0] = manager.undoLastOperation())) {
            setStatus("Undone: " + undone[0].getType() + " - " + undone[0].getDescription());
        }
    }

    private void focusSearch() {
        tabs.setSelectedComponent(searchPanel);
        searchPanel.focusSearchField();
    }

    private void newClinic() {
        if (confirmDiscardChanges()) {
            manager.newClinic();
            remindedAppointments.clear();
            setStatus("Started a new, empty clinic database");
        }
    }

    private void loadSample() {
        if (confirmDiscardChanges()) {
            manager.loadSampleData();
            remindedAppointments.clear();
            setStatus("Loaded sample data (" + manager.getDatabase().size() + " patients)");
        }
    }

    private JFileChooser dataChooser(String title) {
        File dir = manager.getCurrentFile() != null ? manager.getCurrentFile().getAbsoluteFile().getParentFile()
                : new File("data");
        JFileChooser chooser = new JFileChooser(dir.exists() ? dir : new File("."));
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("SCAPMS data files (*.txt)", "txt"));
        return chooser;
    }

    private void loadFile() {
        if (!confirmDiscardChanges()) {
            return;
        }
        JFileChooser chooser = dataChooser("Load Clinic Data");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (GuiUtils.run(this, () -> manager.load(file))) {
                remindedAppointments.clear();
                setStatus("Loaded " + manager.getDatabase().size() + " patients from " + file.getName());
            }
        }
    }

    private boolean save() {
        if (manager.getCurrentFile() == null) {
            return saveAs();
        }
        File file = manager.getCurrentFile();
        if (GuiUtils.run(this, () -> manager.save(file))) {
            setStatus("Saved " + manager.getDatabase().size() + " patients to " + file.getName());
            return true;
        }
        return false;
    }

    private boolean saveAs() {
        JFileChooser chooser = dataChooser("Save Clinic Data As");
        chooser.setSelectedFile(new File("clinic_data.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File file = withExtension(chooser.getSelectedFile(), ".txt");
        if (file.exists() && !GuiUtils.confirm(this, "Overwrite?", file.getName() + " already exists. Overwrite it?")) {
            return false;
        }
        if (GuiUtils.run(this, () -> manager.save(file))) {
            setStatus("Saved to " + file.getAbsolutePath());
            return true;
        }
        return false;
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Export Patients to CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("patients.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = withExtension(chooser.getSelectedFile(), ".csv");
            if (GuiUtils.run(this, () -> manager.exportCsv(file))) {
                setStatus("Exported " + manager.getDatabase().size() + " patients to " + file.getName());
            }
        }
    }

    static File withExtension(File file, String extension) {
        return file.getName().toLowerCase().endsWith(extension) ? file : new File(file.getPath() + extension);
    }

    /** @return true if it is safe to discard the current data. */
    private boolean confirmDiscardChanges() {
        if (!manager.isModified()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Save them first?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            return save();
        }
        return choice == JOptionPane.NO_OPTION;
    }

    private void exitApplication() {
        if (confirmDiscardChanges()) {
            clockTimer.stop();
            dashboardTimer.stop();
            reminderTimer.stop();
            waitingTimer.stop();
            dispose();
            System.exit(0);
        }
    }

    private void showShortcuts() {
        GuiUtils.showInfo(this, "Keyboard Shortcuts",
                "Ctrl+N   New\nCtrl+O   Load\nCtrl+S   Save\nCtrl+Shift+S   Save As\nCtrl+E   Export CSV\n"
                + "Ctrl+Q   Exit\nCtrl+Z   Undo last admin operation\nCtrl+P   Add patient\nCtrl+F   Find patient\n"
                + "Ctrl+1..7   Switch tabs\nDelete   Delete selected patient\nEnter    Search / confirm dialog\n"
                + "Esc      Close dialog\nF1       This help");
    }
}
