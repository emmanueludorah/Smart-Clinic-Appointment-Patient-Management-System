package scapms;

import java.io.File;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import scapms.controller.ClinicManager;
import scapms.exception.ClinicException;
import scapms.exception.FileLoadException;
import scapms.gui.MainWindow;

/**
 * Entry point of SCAPMS - Smart Clinic Appointment &amp; Patient Management System.
 *
 * <p>Usage: {@code java -cp out scapms.Main [data-file]}<br>
 * {@code java -cp out scapms.Main --generate-sample data/clinic_data.txt} writes a fresh sample file.</p>
 */
public final class Main {

    public static final File DEFAULT_DATA_FILE = new File("data", "clinic_data.txt");

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length >= 1 && args[0].equals("--generate-sample")) {
            generateSample(args.length >= 2 ? new File(args[1]) : DEFAULT_DATA_FILE);
            return;
        }
        File dataFile = args.length >= 1 ? new File(args[0]) : DEFAULT_DATA_FILE;

        // Any exception not handled elsewhere is reported instead of silently killing the program.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "An unexpected error occurred:\n" + ex + "\n\nThe application will keep running.",
                    "Unexpected Error", JOptionPane.ERROR_MESSAGE));
        });

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to the default look and feel
            }
            ClinicManager manager = new ClinicManager();
            String startup;
            String loadError = null;
            if (dataFile.exists()) {
                try {
                    manager.load(dataFile);
                    startup = "Loaded " + manager.getDatabase().size() + " patients from " + dataFile.getPath();
                } catch (FileLoadException e) {
                    loadError = e.getMessage();
                    manager.loadSampleData();
                    startup = "Could not load " + dataFile.getPath() + " - sample data loaded instead";
                }
            } else {
                manager.loadSampleData();
                manager.setCurrentFile(dataFile);
                startup = "No data file found - sample data loaded (Ctrl+S saves to " + dataFile.getPath() + ")";
            }
            MainWindow window = new MainWindow(manager);
            window.setVisible(true);
            window.setStatus(startup);
            if (loadError != null) {
                JOptionPane.showMessageDialog(window, "Error: " + loadError, "File Loading Failure",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void generateSample(File file) {
        try {
            ClinicManager manager = new ClinicManager();
            manager.loadSampleData();
            manager.save(file);
            System.out.println("Sample data written to " + file.getAbsolutePath());
        } catch (ClinicException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
