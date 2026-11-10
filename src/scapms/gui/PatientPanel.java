package scapms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import scapms.controller.ClinicManager;
import scapms.model.AppointmentStatus;
import scapms.model.PatientRecord;

/** "Patient Records" tab: view, add, edit, delete and queue patients. */
class PatientPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private final transient ClinicManager manager;
    private final MainWindow window;
    private final PatientTableModel model = new PatientTableModel();
    private final JTable table = new JTable(model);
    private final JTextField filterField = new JTextField(16);
    private final JTextArea detailsArea = new JTextArea();
    private final JLabel countLabel = new JLabel();
    private boolean refreshing;

    PatientPanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(6, 6));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ---- toolbar (BorderLayout holding two FlowLayouts)
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(GuiUtils.button("Add Patient", "Register a new patient (Ctrl+P)", e -> addPatient()));
        buttons.add(GuiUtils.button("Edit Patient", "Edit the selected patient (or double-click a row)", e -> editPatient()));
        buttons.add(GuiUtils.button("Delete Patient", "Delete the selected patient (Delete key) - can be undone", e -> deletePatient()));
        buttons.add(GuiUtils.button("Add to Queue", "Put the selected patient in the waiting queue", e -> addToQueue()));

        JPanel filterBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterBox.add(new JLabel("Quick filter:"));
        filterField.setToolTipText("Type part of an ID, name or phone number to filter the table instantly");
        filterBox.add(filterField);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(buttons, BorderLayout.WEST);
        toolbar.add(filterBox, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        // ---- table + details
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new CategoryCellRenderer(
                r -> model.getPatientAt(r) == null ? null : model.getPatientAt(r).getCategory(),
                PatientTableModel.PRIORITY_COLUMN));
        table.setDefaultRenderer(Integer.class, table.getDefaultRenderer(Object.class));
        table.setToolTipText("Red = Emergency (High Priority), Blue = Follow-Up (Scheduled), Green = General (Normal)");

        detailsArea.setEditable(false);
        detailsArea.setFont(GuiUtils.MONO);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setText("Select a patient to see the full record.");
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        GuiUtils.titled(detailsScroll, "Patient Details");

        detailsScroll.setPreferredSize(new java.awt.Dimension(380, 300));
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new java.awt.Dimension(800, 300));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailsScroll);
        split.setResizeWeight(0.68);
        add(split, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // ---- events
        filterField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::refresh));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails();
                if (!refreshing && getSelected() != null) {
                    manager.recordAccess(getSelected());   // a real user access -> update the array cache
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    editPatient();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deletePatient");
        table.getActionMap().put("deletePatient", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deletePatient();
            }
        });
    }

    @Override
    public void refresh() {
        PatientRecord selected = getSelected();
        refreshing = true;
        try {
            model.setPatients(manager.getSearchEngine().filter(manager.getDatabase().getPatients(), filterField.getText()));
            if (selected != null) {
                int row = model.indexOfId(selected.getPatientId());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                }
            }
        } finally {
            refreshing = false;
        }
        int total = manager.getDatabase().size();
        String order = manager.getDatabase().getSortedBy() == null ? "unsorted (registration order)"
                : "sorted by " + manager.getDatabase().getSortedBy()
                  + (manager.getDatabase().isSortedAscending() ? " (ascending)" : " (descending)");
        countLabel.setText("Showing " + model.getRowCount() + " of " + total + " patients  |  Order: " + order);
        showDetails();
    }

    private PatientRecord getSelected() {
        return model.getPatientAt(table.getSelectedRow());
    }

    private PatientRecord requireSelection() {
        PatientRecord p = getSelected();
        if (p == null) {
            GuiUtils.showWarning(this, "Please select a patient in the table first.");
        }
        return p;
    }

    private void showDetails() {
        PatientRecord p = getSelected();
        if (p == null) {
            detailsArea.setText("Select a patient to see the full record.");
            return;
        }
        detailsArea.setText(p.getFullDetails());  // polymorphic output (template method)
        detailsArea.setCaretPosition(0);
    }

    void addPatient() {
        PatientRecord added = PatientDialog.showAddDialog(window, manager, manager::registerPatient);
        if (added != null) {
            window.setStatus("Registered patient " + added.getPatientId() + " - " + added.getName());
            selectPatient(added.getPatientId());
        }
    }

    void editPatient() {
        PatientRecord p = requireSelection();
        if (p == null) {
            return;
        }
        PatientRecord edited = PatientDialog.showEditDialog(window, manager, p,
                updated -> manager.updatePatient(p.getPatientId(), updated));
        if (edited != null) {
            window.setStatus("Updated patient " + edited.getPatientId() + " (Ctrl+Z to undo)");
        }
    }

    void deletePatient() {
        PatientRecord p = requireSelection();
        if (p == null) {
            return;
        }
        int pending = p.countAppointments(AppointmentStatus.PENDING);
        String msg = "Delete patient " + p.getPatientId() + " - " + p.getName() + "?\n"
                + "This also removes " + p.getAppointments().size() + " appointment(s)"
                + (pending > 0 ? " (" + pending + " pending)" : "") + ".\n\n"
                + "You can reverse this with Edit > Undo (Ctrl+Z).";
        if (GuiUtils.confirm(this, "Confirm Delete", msg)
                && GuiUtils.run(this, () -> manager.deletePatient(p.getPatientId()))) {
            window.setStatus("Deleted patient " + p.getPatientId() + " (Ctrl+Z to undo)");
        }
    }

    private void addToQueue() {
        PatientRecord p = requireSelection();
        if (p != null && GuiUtils.run(this, () -> manager.addToQueue(p.getPatientId()))) {
            window.setStatus(p.getName() + " added to the waiting queue (position "
                    + manager.getQueueManager().size() + ")");
        }
    }

    void selectPatient(String patientId) {
        filterField.setText("");
        int row = model.indexOfId(patientId);
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
    }
}
