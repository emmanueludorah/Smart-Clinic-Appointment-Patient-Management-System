package scapms.gui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import scapms.model.AppointmentStatus;
import scapms.model.PatientRecord;

/** Table model presenting a list of patient records. */
class PatientTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    static final int PRIORITY_COLUMN = 4;
    private static final String[] COLUMNS = {
        "Patient ID", "Name", "Age", "Category", "Priority", "Phone", "Appts (Pending/Total)", "Next Appointment"
    };
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private List<PatientRecord> rows = new ArrayList<>();

    void setPatients(List<PatientRecord> patients) {
        rows = new ArrayList<>(patients);
        fireTableDataChanged();
    }

    PatientRecord getPatientAt(int row) {
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    int indexOfId(String patientId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getPatientId().equals(patientId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 2 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        PatientRecord p = rows.get(row);
        switch (column) {
            case 0: return p.getPatientId();
            case 1: return p.getName();
            case 2: return p.getAge();
            case 3: return p.getCategory().toString();
            case 4: return p.getPriorityLabel();
            case 5: return p.getPhone();
            case 6: return p.countAppointments(AppointmentStatus.PENDING) + " / " + p.getAppointments().size();
            case 7: {
                LocalDateTime next = p.getNextAppointmentDateTime();
                return next == null ? "-" : next.format(WHEN);
            }
            default: return "";
        }
    }
}
