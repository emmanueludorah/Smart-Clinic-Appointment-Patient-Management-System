package scapms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import scapms.controller.ClinicManager;
import scapms.model.Appointable;
import scapms.model.Appointment;
import scapms.model.AppointmentStatus;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/** "Appointments" tab: schedule, cancel, complete and view appointments. */
class AppointmentPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private static final String ALL = "All appointments";

    private final transient ClinicManager manager;
    private final MainWindow window;

    private final JComboBox<PatientRecord> patientCombo = new JComboBox<>();
    private final JTextField dateField = new JTextField(10);
    private final JTextField timeField = new JTextField(6);
    private final JTextField doctorField = new JTextField(16);
    private final JTextField reasonField = new JTextField(22);
    private final JLabel dateHint = new JLabel(" ");
    private final JLabel timeHint = new JLabel(" ");
    private final JLabel ruleLabel = new JLabel(" ");
    private final JComboBox<Object> filterCombo = new JComboBox<>(
            new Object[] {ALL, AppointmentStatus.PENDING, AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED});
    private final AppointmentTableModel model;
    private final JTable table;
    private final JLabel countLabel = new JLabel();
    private boolean updatingCombo;

    AppointmentPanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(6, 6));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        model = new AppointmentTableModel(id -> {
            PatientRecord p = manager.findPatient(id);
            return p == null ? "?" : p.getName();
        });
        table = new JTable(model);

        add(buildForm(), BorderLayout.NORTH);
        add(buildTableArea(), BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // ---- events
        patientCombo.addActionListener(e -> {
            if (!updatingCombo) {
                onPatientChosen();
            }
        });
        dateField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::validateDateLive));
        timeField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::validateTimeLive));
        filterCombo.addActionListener(e -> refreshTable());

        dateField.setText(LocalDate.now().plusDays(1).format(Validator.DATE_FORMAT));
        timeField.setText("09:00");
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GuiUtils.titled(form, "Schedule Appointment");
        patientCombo.setToolTipText("Choose the patient to book");
        dateField.setToolTipText("Appointment date, yyyy-MM-dd");
        timeField.setToolTipText("24-hour time, HH:mm (clinic hours 08:00-17:30 except emergencies)");
        doctorField.setToolTipText("Doctor's name (follow-up patients default to their referring doctor)");
        reasonField.setToolTipText("Reason for the visit (required)");
        ruleLabel.setForeground(GuiUtils.PRIMARY);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;

        addCell(form, c, 0, 0, new JLabel("Patient:"), 0);
        c.gridwidth = 3;
        addCell(form, c, 1, 0, patientCombo, 1);
        c.gridwidth = 1;
        addCell(form, c, 4, 0, new JLabel("Doctor:"), 0);
        addCell(form, c, 5, 0, doctorField, 1);

        addCell(form, c, 0, 1, new JLabel("Date:"), 0);
        addCell(form, c, 1, 1, dateField, 0.5);
        addCell(form, c, 2, 1, new JLabel("Time:"), 0);
        addCell(form, c, 3, 1, timeField, 0.3);
        addCell(form, c, 4, 1, new JLabel("Reason:"), 0);
        addCell(form, c, 5, 1, reasonField, 1);

        JPanel hints = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        hints.add(dateHint);
        hints.add(timeHint);
        c.gridwidth = 4;
        addCell(form, c, 0, 2, hints, 0);
        c.gridwidth = 1;
        JButton schedule = GuiUtils.button("Schedule Appointment", "Validate and book this appointment", e -> schedule());
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 5;
        c.gridy = 2;
        form.add(schedule, c);
        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = 6;
        addCell(form, c, 0, 3, ruleLabel, 1);
        return form;
    }

    private static void addCell(JPanel p, GridBagConstraints c, int x, int y, JComponent comp, double weight) {
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        p.add(comp, c);
    }

    private JPanel buildTableArea() {
        JPanel area = new JPanel(new BorderLayout(4, 4));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(new JLabel("Show:"));
        filterCombo.setToolTipText("Filter the list by appointment status");
        actions.add(filterCombo);
        actions.add(GuiUtils.button("Cancel Appointment", "Cancel the selected pending appointment (can be undone)",
                e -> cancelSelected()));
        actions.add(GuiUtils.button("Mark Completed", "Record that the selected appointment took place",
                e -> completeSelected()));
        actions.add(GuiUtils.button("Remove (Admin)", "Permanently remove the selected appointment record (can be undone)",
                e -> removeSelected()));
        area.add(actions, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new StatusCellRenderer(model::getAppointmentAt));
        table.setToolTipText("Amber = Pending, Green = Completed, Grey = Cancelled, bold red = urgent");
        JScrollPane scroll = new JScrollPane(table);
        GuiUtils.titled(scroll, "Appointments");
        area.add(scroll, BorderLayout.CENTER);
        return area;
    }

    @Override
    public void refresh() {
        refreshPatientCombo();
        refreshTable();
    }

    private void refreshPatientCombo() {
        Object selected = patientCombo.getSelectedItem();
        String selectedId = selected instanceof PatientRecord ? ((PatientRecord) selected).getPatientId() : null;
        updatingCombo = true;
        try {
            patientCombo.removeAllItems();
            PatientRecord reselect = null;
            for (PatientRecord p : manager.getDatabase().getPatients()) {
                patientCombo.addItem(p);
                if (p.getPatientId().equals(selectedId)) {
                    reselect = p;
                }
            }
            if (reselect != null) {
                patientCombo.setSelectedItem(reselect);
            }
        } finally {
            updatingCombo = false;
        }
        updateRuleLabel();
    }

    private void refreshTable() {
        Object filter = filterCombo.getSelectedItem();
        AppointmentStatus status = filter instanceof AppointmentStatus ? (AppointmentStatus) filter : null;
        model.setAppointments(manager.getAppointmentController().getAppointments(status));
        int total = manager.getDatabase().getAllAppointments().size();
        countLabel.setText("Showing " + model.getRowCount() + " of " + total + " appointments");
    }

    /** Combo-box event: show the booking rule and suggest a date for the chosen patient type. */
    private void onPatientChosen() {
        Object item = patientCombo.getSelectedItem();
        if (item instanceof PatientRecord) {
            PatientRecord p = (PatientRecord) item;
            dateField.setText(p.suggestAppointmentDate(LocalDate.now()).format(Validator.DATE_FORMAT));
        }
        updateRuleLabel();
    }

    private void updateRuleLabel() {
        Object item = patientCombo.getSelectedItem();
        if (item instanceof Appointable) {
            ruleLabel.setText("Rule: " + ((Appointable) item).getBookingRule());
        } else {
            ruleLabel.setText("Register a patient first, then choose them here.");
        }
    }

    private void validateDateLive() {
        LocalDate d = Validator.tryParseDate(dateField.getText());
        if (d == null) {
            GuiUtils.setHint(dateHint, false, "Date must be yyyy-MM-dd");
        } else if (d.isBefore(LocalDate.now())) {
            GuiUtils.setHint(dateHint, false, "Date is in the past");
        } else {
            GuiUtils.setHint(dateHint, true, d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + (d.equals(LocalDate.now()) ? " (today)" : ""));
        }
    }

    private void validateTimeLive() {
        LocalTime t = Validator.tryParseTime(timeField.getText());
        if (t == null) {
            GuiUtils.setHint(timeHint, false, "Time must be HH:mm");
        } else if (t.isBefore(PatientRecord.CLINIC_OPENS) || t.isAfter(PatientRecord.CLINIC_CLOSES)) {
            timeHint.setForeground(GuiUtils.WARNING);
            timeHint.setText("⚠ Outside clinic hours (emergencies only)");
        } else {
            GuiUtils.setHint(timeHint, true, "Within clinic hours");
        }
    }

    private void schedule() {
        Object item = patientCombo.getSelectedItem();
        String patientId = item instanceof PatientRecord ? ((PatientRecord) item).getPatientId() : null;
        Appointment[] booked = new Appointment[1];
        boolean ok = GuiUtils.run(this, () -> booked[0] = manager.getAppointmentController().scheduleAppointment(
                patientId, dateField.getText(), timeField.getText(), doctorField.getText(), reasonField.getText()));
        if (ok) {
            reasonField.setText("");
            window.setStatus("Scheduled " + booked[0]);
            GuiUtils.showInfo(this, "Appointment Scheduled", "Appointment booked successfully:\n\n" + booked[0]
                    + (booked[0].isUrgent() ? "\n\nThis appointment is flagged URGENT." : ""));
        }
    }

    private Appointment requireSelection() {
        Appointment a = model.getAppointmentAt(table.getSelectedRow());
        if (a == null) {
            GuiUtils.showWarning(this, "Please select an appointment in the table first.");
        }
        return a;
    }

    private void cancelSelected() {
        Appointment a = requireSelection();
        if (a != null && GuiUtils.confirm(this, "Cancel Appointment", "Cancel appointment " + a + "?")
                && GuiUtils.run(this, () -> manager.getAppointmentController().cancelAppointment(a.getAppointmentId()))) {
            window.setStatus("Cancelled appointment " + a.getAppointmentId() + " (Ctrl+Z to undo)");
        }
    }

    private void completeSelected() {
        Appointment a = requireSelection();
        if (a != null && GuiUtils.run(this, () -> manager.getAppointmentController().completeAppointment(a.getAppointmentId()))) {
            window.setStatus("Marked " + a.getAppointmentId() + " as completed");
        }
    }

    private void removeSelected() {
        Appointment a = requireSelection();
        if (a != null && GuiUtils.confirm(this, "Remove Appointment",
                "Permanently remove appointment record " + a + "?\n(Administrative operation - can be undone with Ctrl+Z)")
                && GuiUtils.run(this, () -> manager.getAppointmentController().removeAppointment(a.getAppointmentId()))) {
            window.setStatus("Removed appointment " + a.getAppointmentId() + " (Ctrl+Z to undo)");
        }
    }
}
