package scapms.gui;

import java.awt.Component;
import java.awt.Font;
import java.util.function.IntFunction;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import scapms.model.Appointment;
import scapms.model.AppointmentStatus;

/**
 * Custom table presentation for appointments: Pending = amber, Completed = green,
 * Cancelled = grey italic; urgent appointments are shown in bold red.
 */
class StatusCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    private final transient IntFunction<Appointment> appointmentOfRow;

    StatusCellRenderer(IntFunction<Appointment> appointmentOfRow) {
        this.appointmentOfRow = appointmentOfRow;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Appointment a = appointmentOfRow.apply(table.convertRowIndexToModel(row));
        Font base = table.getFont();
        setFont(base);
        if (a == null) {
            return this;
        }
        if (column == AppointmentTableModel.STATUS_COLUMN || (a.isUrgent() && a.isPending())) {
            setFont(base.deriveFont(Font.BOLD));
        }
        if (a.getStatus() == AppointmentStatus.CANCELLED) {
            setFont(getFont().deriveFont(Font.ITALIC));
        }
        if (!isSelected) {
            setForeground(a.isUrgent() && a.isPending() ? GuiUtils.EMERGENCY_FG : table.getForeground());
            switch (a.getStatus()) {
                case PENDING:
                    setBackground(GuiUtils.PENDING_BG);
                    break;
                case COMPLETED:
                    setBackground(GuiUtils.COMPLETED_BG);
                    break;
                default:
                    setBackground(GuiUtils.CANCELLED_BG);
                    setForeground(java.awt.Color.GRAY);
                    break;
            }
        }
        return this;
    }
}
