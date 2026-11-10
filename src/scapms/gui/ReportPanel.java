package scapms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.time.LocalDate;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import scapms.controller.ClinicManager;
import scapms.controller.ReportGenerator;
import scapms.exception.ClinicException;
import scapms.util.FileHandler;
import scapms.util.Validator;

/** "Reports" tab: generate and export the clinic reports. */
class ReportPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private final transient ClinicManager manager;
    private final MainWindow window;
    private final JComboBox<ReportGenerator.ReportType> reportCombo =
            new JComboBox<>(ReportGenerator.ReportType.values());
    private final JTextField dateField = new JTextField(LocalDate.now().format(Validator.DATE_FORMAT), 10);
    private final JTextArea output = new JTextArea();
    private ReportGenerator.ReportType lastReport;
    private LocalDate lastDate;

    ReportPanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(6, 6));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel("Report:"));
        reportCombo.setToolTipText("Choose the report to generate");
        top.add(reportCombo);
        JLabel dateLabel = new JLabel("Date:");
        top.add(dateLabel);
        dateField.setToolTipText("Day for the Daily Appointment Summary (yyyy-MM-dd)");
        top.add(dateField);
        top.add(GuiUtils.button("Generate", "Generate the selected report", e -> generate()));
        top.add(GuiUtils.button("Export...", "Save the report to a text file", e -> export()));
        add(top, BorderLayout.NORTH);

        output.setEditable(false);
        output.setFont(GuiUtils.MONO);
        output.setText("Choose a report and press Generate.");
        JScrollPane scroll = new JScrollPane(output);
        GuiUtils.titled(scroll, "Report Output");
        add(scroll, BorderLayout.CENTER);

        // dynamic component: the date field is only relevant to the daily summary
        reportCombo.addActionListener(e -> {
            boolean daily = reportCombo.getSelectedItem() == ReportGenerator.ReportType.DAILY;
            dateLabel.setEnabled(daily);
            dateField.setEnabled(daily);
            generate();
        });
        dateField.addActionListener(e -> generate());
        reportCombo.setSelectedIndex(0);
    }

    @Override
    public void refresh() {
        if (lastReport != null) {          // keep the visible report up to date
            output.setText(manager.getReportGenerator().generate(lastReport, lastDate));
            output.setCaretPosition(0);
        }
    }

    private void generate() {
        ReportGenerator.ReportType type = (ReportGenerator.ReportType) reportCombo.getSelectedItem();
        LocalDate date = LocalDate.now();
        if (type == ReportGenerator.ReportType.DAILY) {
            date = Validator.tryParseDate(dateField.getText());
            if (date == null) {
                GuiUtils.showError(this, "Report date must be a valid date in the format yyyy-MM-dd.");
                return;
            }
        }
        lastReport = type;
        lastDate = date;
        output.setText(manager.getReportGenerator().generate(type, date));
        output.setCaretPosition(0);
        if (window != null) {
            window.setStatus("Generated report: " + type);
        }
    }

    private void export() {
        if (lastReport == null) {
            GuiUtils.showWarning(this, "Generate a report first.");
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Export Report");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        chooser.setSelectedFile(new File(lastReport.name().toLowerCase() + "_report.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = MainWindow.withExtension(chooser.getSelectedFile(), ".txt");
        if (GuiUtils.run(this, () -> {
            try {
                FileHandler.writeText(file, output.getText());
            } catch (java.io.IOException ex) {
                throw new ClinicException("Could not write report: " + ex.getMessage(), ex);
            }
        })) {
            window.setStatus("Report exported to " + file.getAbsolutePath());
        }
    }
}
