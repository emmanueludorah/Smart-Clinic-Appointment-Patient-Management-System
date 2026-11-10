package scapms.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import scapms.controller.ClinicManager;
import scapms.model.Appointment;
import scapms.model.PatientCategory;
import scapms.model.PatientRecord;
import scapms.model.QueueEntry;

/** "Waiting Queue" tab: add patients to the FIFO queue and call the next patient. */
class QueuePanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final transient ClinicManager manager;
    private final MainWindow window;
    private final JComboBox<PatientRecord> patientCombo = new JComboBox<>();
    private final QueueTableModel model = new QueueTableModel();
    private final JTable table = new JTable(model);
    private final JLabel nowServing = new JLabel("-");
    private final JLabel nextUp = new JLabel("-");
    private final JLabel sizeLabel = new JLabel();

    QueuePanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(8, 8));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel("Patient:"));
        patientCombo.setToolTipText("Choose a registered patient to add to the end of the queue");
        top.add(patientCombo);
        top.add(GuiUtils.button("Add to Queue", "Add the chosen patient to the back of the queue (FIFO)", e -> addToQueue()));
        add(top, BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        CategoryCellRenderer renderer = new CategoryCellRenderer(model::categoryAt, 2) {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                if (column == 5 && model.isLongWait(row)) {          // highlight long waits
                    setForeground(GuiUtils.WARNING);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setText(value + "  ⚠");
                }
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
        JScrollPane scroll = new JScrollPane(table);
        GuiUtils.titled(scroll, "Waiting Patients (first in, first out)");
        add(scroll, BorderLayout.CENTER);

        add(buildServingPanel(), BorderLayout.EAST);
        add(sizeLabel, BorderLayout.SOUTH);
    }

    /** Vertical BoxLayout panel for the consultation room. */
    private JPanel buildServingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        GuiUtils.titled(panel, "Consultation Room");
        JLabel servingTitle = new JLabel("Now serving:");
        nowServing.setFont(nowServing.getFont().deriveFont(Font.BOLD, 18f));
        nowServing.setForeground(GuiUtils.PRIMARY);
        JLabel nextTitle = new JLabel("Next in line:");
        JButton callNext = GuiUtils.button("Call Next Patient",
                "Remove the patient at the front of the queue and call them in", e -> callNext());
        callNext.setFont(callNext.getFont().deriveFont(Font.BOLD, 14f));
        for (javax.swing.JComponent c : new javax.swing.JComponent[] {servingTitle, nowServing, nextTitle, nextUp, callNext}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        panel.add(servingTitle);
        panel.add(Box.createVerticalStrut(4));
        panel.add(nowServing);
        panel.add(Box.createVerticalStrut(18));
        panel.add(nextTitle);
        panel.add(Box.createVerticalStrut(4));
        panel.add(nextUp);
        panel.add(Box.createVerticalStrut(18));
        panel.add(callNext);
        panel.add(Box.createVerticalGlue());
        panel.setPreferredSize(new java.awt.Dimension(270, 10));
        return panel;
    }

    @Override
    public void refresh() {
        Object selected = patientCombo.getSelectedItem();
        patientCombo.removeAllItems();
        for (PatientRecord p : manager.getDatabase().getPatients()) {
            patientCombo.addItem(p);
            if (selected instanceof PatientRecord && ((PatientRecord) selected).getPatientId().equals(p.getPatientId())) {
                patientCombo.setSelectedItem(p);
            }
        }
        refreshQueueView();
    }

    /** Lightweight refresh used by the waiting-time timer (does not touch the combo box). */
    void refreshQueueView() {
        model.reload();
        QueueEntry front = manager.getQueueManager().peek();
        PatientRecord next = front == null ? null : manager.findPatient(front.getPatientId());
        nextUp.setText(next == null ? "(queue is empty)" : "<html>" + next.getPatientId() + " - " + next.getName()
                + "<br>" + next.getCategory() + " (" + next.getPriorityLabel() + ")</html>");
        sizeLabel.setText("Patients waiting: " + manager.getQueueManager().size()
                + "   |   Longest wait: " + manager.getQueueManager().getLongestWaitMinutes() + " min"
                + "   |   Alert threshold: " + MainWindow.WAIT_ALERT_MINUTES + " min");
    }

    private void addToQueue() {
        Object item = patientCombo.getSelectedItem();
        if (!(item instanceof PatientRecord)) {
            GuiUtils.showWarning(this, "Please choose a patient first.");
            return;
        }
        PatientRecord p = (PatientRecord) item;
        if (GuiUtils.run(this, () -> manager.addToQueue(p.getPatientId()))) {
            window.setStatus(p.getName() + " joined the queue at position " + manager.getQueueManager().size());
        }
    }

    private void callNext() {
        QueueEntry[] called = new QueueEntry[1];
        if (!GuiUtils.run(this, () -> called[0] = manager.callNextPatient())) {
            return;
        }
        PatientRecord p = manager.findPatient(called[0].getPatientId());
        if (p == null) {
            return;
        }
        long waited = called[0].getWaitingMinutes(LocalDateTime.now());
        nowServing.setText("<html>" + p.getPatientId() + "<br>" + p.getName() + "</html>");
        window.setStatus("Now serving " + p.getPatientId() + " - " + p.getName());
        GuiUtils.showInfo(this, "Next Patient", "Now serving: " + p.getPatientId() + " - " + p.getName()
                + "\nCategory: " + p.getCategory() + " (" + p.getPriorityLabel() + ")\nWaited: " + waited + " minute(s)");
        Appointment today = manager.getAppointmentController().findTodaysPendingAppointment(p.getPatientId());
        if (today != null && GuiUtils.confirm(this, "Consultation",
                p.getName() + " has a pending appointment today:\n" + today + "\n\nMark it as completed?")) {
            GuiUtils.run(this, () -> manager.getAppointmentController().completeAppointment(today.getAppointmentId()));
        }
    }

    /** Table model listing the queue in service order. */
    private final class QueueTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Position", "Patient ID", "Name", "Category", "Joined At", "Waiting (min)"};
        private List<QueueEntry> entries = new ArrayList<>();

        void reload() {
            entries = manager.getQueueManager().snapshot();
            fireTableDataChanged();
        }

        PatientCategory categoryAt(int row) {
            PatientRecord p = patientAt(row);
            return p == null ? null : p.getCategory();
        }

        boolean isLongWait(int row) {
            return row < entries.size()
                    && entries.get(row).getWaitingMinutes(LocalDateTime.now()) >= MainWindow.WAIT_ALERT_MINUTES;
        }

        private PatientRecord patientAt(int row) {
            return row < entries.size() ? manager.findPatient(entries.get(row).getPatientId()) : null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int c) {
            return columns[c];
        }

        @Override
        public Object getValueAt(int row, int column) {
            QueueEntry e = entries.get(row);
            PatientRecord p = patientAt(row);
            switch (column) {
                case 0: return String.valueOf(row + 1);
                case 1: return e.getPatientId();
                case 2: return p == null ? "?" : p.getName();
                case 3: return p == null ? "?" : p.getCategory().toString();
                case 4: return e.getJoinedAt().format(HM);
                case 5: return String.valueOf(e.getWaitingMinutes(LocalDateTime.now()));
                default: return "";
            }
        }
    }
}
