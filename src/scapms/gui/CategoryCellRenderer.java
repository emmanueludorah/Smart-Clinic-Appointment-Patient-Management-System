package scapms.gui;

import java.awt.Component;
import java.awt.Font;
import java.util.function.IntFunction;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import scapms.model.PatientCategory;

/**
 * Custom table presentation: colours each row by patient category so staff can see
 * priorities at a glance (Emergency = red / High Priority, General = green / Normal,
 * Follow-Up = blue / Scheduled). The priority column is drawn in bold.
 */
class CategoryCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    private final transient IntFunction<PatientCategory> categoryOfRow;
    private final int boldColumn;

    CategoryCellRenderer(IntFunction<PatientCategory> categoryOfRow, int boldColumn) {
        this.categoryOfRow = categoryOfRow;
        this.boldColumn = boldColumn;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setHorizontalAlignment(value instanceof Number ? SwingConstants.CENTER : SwingConstants.LEFT);
        Font base = table.getFont();
        setFont(column == boldColumn ? base.deriveFont(Font.BOLD) : base);
        if (!isSelected) {
            PatientCategory category = categoryOfRow.apply(table.convertRowIndexToModel(row));
            setForeground(table.getForeground());
            if (category == PatientCategory.EMERGENCY) {
                setBackground(GuiUtils.EMERGENCY_BG);
                setForeground(GuiUtils.EMERGENCY_FG);
            } else if (category == PatientCategory.FOLLOW_UP) {
                setBackground(GuiUtils.FOLLOWUP_BG);
                setForeground(GuiUtils.FOLLOWUP_FG);
            } else if (category == PatientCategory.GENERAL) {
                setBackground(GuiUtils.GENERAL_BG);
            } else {
                setBackground(table.getBackground());
            }
        }
        return this;
    }
}
