package scapms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import scapms.controller.AdminOperation;
import scapms.controller.ClinicManager;
import scapms.controller.RecentPatientCache;
import scapms.model.PatientCategory;
import scapms.model.PatientRecord;

/**
 * "Admin" tab: shows the undo stack (top = most recent), lets the administrator
 * undo the last operation, and displays system information including the
 * contents of the fixed-size recent-patient array.
 */
class AdminPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private final transient ClinicManager manager;
    private final MainWindow window;
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JTextArea infoArea = new JTextArea();
    private final JButton undoButton;
    private final JLabel topLabel = new JLabel();

    AdminPanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(8, 8));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel("<html>Administrative operations (register, modify, delete patient; schedule, cancel, "
                + "complete, remove appointment) are pushed onto a <b>stack</b>. <b>Undo</b> pops the most recent one "
                + "and reverses it (LIFO).</html>");
        add(intro, BorderLayout.NORTH);

        historyList.setFont(GuiUtils.MONO);
        historyList.setToolTipText("The first line is the top of the stack - it will be undone next");
        JScrollPane historyScroll = new JScrollPane(historyList);
        GuiUtils.titled(historyScroll, "Undo Stack (top = most recent)");

        infoArea.setEditable(false);
        infoArea.setFont(GuiUtils.MONO);
        JScrollPane infoScroll = new JScrollPane(infoArea);
        GuiUtils.titled(infoScroll, "System Information");

        JPanel centre = new JPanel(new GridLayout(1, 2, 8, 0));
        centre.add(historyScroll);
        centre.add(infoScroll);
        add(centre, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        undoButton = GuiUtils.button("Undo Last Operation", "Reverse the operation at the top of the stack (Ctrl+Z)",
                e -> window.undo());
        buttons.add(undoButton);
        buttons.add(GuiUtils.button("Clear Undo History", "Empty the undo stack (cannot be reversed)", e -> clearHistory()));
        buttons.add(GuiUtils.button("Clear Waiting Queue", "Remove every patient from the waiting queue", e -> clearQueue()));
        buttons.add(topLabel);
        add(buttons, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        historyModel.clear();
        for (AdminOperation op : manager.getUndoManager().getHistory()) {
            historyModel.addElement(op.toString());
        }
        if (historyModel.isEmpty()) {
            historyModel.addElement("(stack is empty - nothing to undo)");
        }
        AdminOperation top = manager.getUndoManager().peek();
        undoButton.setEnabled(top != null);
        topLabel.setText(top == null ? "" : "  Next undo: " + top.getDescription());
        refreshInfo();
    }

    /** Also called by the dashboard timer so the cache view stays current. */
    void refreshInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Data file       : ").append(manager.getCurrentFile() == null ? "(not saved yet)"
                : manager.getCurrentFile().getAbsolutePath()).append('\n');
        sb.append("Unsaved changes : ").append(manager.isModified() ? "YES" : "no").append('\n');
        sb.append("Patients        : ").append(manager.getDatabase().size()).append('\n');
        for (PatientCategory c : PatientCategory.values()) {
            sb.append(String.format("   %-19s: %d%n", c.getPluralLabel(), manager.getDatabase().countByCategory(c)));
        }
        sb.append("Appointments    : ").append(manager.getDatabase().getAllAppointments().size()).append('\n');
        sb.append("Waiting queue   : ").append(manager.getQueueManager().size()).append('\n');
        sb.append("Undo stack size : ").append(manager.getUndoManager().size()).append('\n');
        sb.append("Sort state      : ").append(manager.getDatabase().getSortedBy() == null ? "unsorted"
                : manager.getDatabase().getSortedBy().toString()).append("\n\n");

        RecentPatientCache cache = manager.getRecentCache();
        sb.append("Frequently accessed cache - PatientRecord* recentPatients[")
          .append(RecentPatientCache.CAPACITY).append("]\n");
        for (int i = 0; i < RecentPatientCache.CAPACITY; i++) {
            PatientRecord p = cache.getSlot(i);
            sb.append(String.format("  [%d] %s%n", i, p == null ? "null"
                    : p.getPatientId() + " " + p.getName() + "  (" + cache.getAccessCount(i) + " views)"));
        }
        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(0);
    }

    private void clearHistory() {
        if (GuiUtils.confirm(this, "Clear Undo History",
                "Remove all " + manager.getUndoManager().size() + " operations from the undo stack?")) {
            manager.clearUndoHistory();
            window.setStatus("Undo history cleared");
        }
    }

    private void clearQueue() {
        if (manager.getQueueManager().isEmpty()) {
            GuiUtils.showInfo(this, "Waiting Queue", "The waiting queue is already empty.");
        } else if (GuiUtils.confirm(this, "Clear Queue",
                "Remove all " + manager.getQueueManager().size() + " patients from the waiting queue?")) {
            manager.clearQueue();
            window.setStatus("Waiting queue cleared");
        }
    }
}
