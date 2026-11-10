package scapms.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.table.AbstractTableModel;

import scapms.model.Appointment;
import scapms.util.Validator;

/** Table model presenting appointments. */
class AppointmentTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    static final int STATUS_COLUMN = 8;
    private static final String[] COLUMNS = {
        "Appt ID", "Patient ID", "Patient Name", "Date", "Time", "Doctor", "Reason", "Urgent", "Status"
    };

    private final transient Function<String, String> patientNameLookup;
    private List<Appointment> rows = new ArrayList<>();

    AppointmentTableModel(Function<String, String> patientNameLookup) {
        this.patientNameLookup = patientNameLookup;
    }

    void setAppointments(List<Appointment> appointments) {
        rows = new ArrayList<>(appointments);
        fireTableDataChanged();
    }

    Appointment getAppointmentAt(int row) {
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
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
    public Object getValueAt(int row, int column) {
        Appointment a = rows.get(row);
        switch (column) {
            case 0: return a.getAppointmentId();
            case 1: return a.getPatientId();
            case 2: return patientNameLookup.apply(a.getPatientId());
            case 3: return a.getDate().format(Validator.DATE_FORMAT);
            case 4: return a.getTime().format(Validator.TIME_FORMAT);
            case 5: return a.getDoctor();
            case 6: return a.getReason();
            case 7: return a.isUrgent() ? "URGENT" : "";
            case 8: return a.getStatus().toString();
            default: return "";
        }
    }
}
