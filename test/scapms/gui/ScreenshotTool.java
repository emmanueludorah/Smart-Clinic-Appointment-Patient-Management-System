package scapms.gui;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import scapms.controller.ClinicManager;
import scapms.model.PatientCategory;

/**
 * Launches the real GUI and captures a PNG of every tab plus the registration dialog
 * (for the submission document). Run from the project folder:
 * java -cp out;test-out scapms.gui.ScreenshotTool docs/screenshots
 */
public final class ScreenshotTool {

    private static MainWindow window;
    private static ClinicManager manager;

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "docs/screenshots");
        dir.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel
            }
            manager = new ClinicManager();
            try {
                manager.load(new File("data/clinic_data.txt"));
            } catch (Exception e) {
                manager.loadSampleData();
            }
            window = new MainWindow(manager);
            window.setVisible(true);
            window.toFront();
        });
        Robot robot = new Robot();
        robot.delay(5500);                     // let the reminder timer fire once, then close it
        closeStrayDialogs();
        robot.delay(600);
        String[] names = {"1_dashboard", "2_patient_records", "3_appointments", "4_waiting_queue",
            "5_search_sort", "6_admin_undo", "7_reports"};
        for (int i = 0; i < names.length; i++) {
            final int tab = i;
            SwingUtilities.invokeAndWait(() -> prepareTab(tab));
            robot.delay(800);
            closeStrayDialogs();
            robot.delay(300);
            capture(robot, window, new File(dir, names[i] + ".png"));
            if (tab == 4) {
                // second search/sort shot: binary search on the ID-sorted data
                SwingUtilities.invokeAndWait(() -> {
                    java.awt.Container search = (java.awt.Container) findTabs().getComponentAt(4);
                    for (java.awt.Component c : allComponents(search)) {
                        if (c instanceof javax.swing.JTextField && ((javax.swing.JTextField) c).isEditable()) {
                            ((javax.swing.JTextField) c).setText("P0010");
                            break;
                        }
                    }
                    clickButton(search, "Search");
                });
                robot.delay(800);
                closeStrayDialogs();
                robot.delay(300);
                capture(robot, window, new File(dir, "5b_binary_search.png"));
            }
        }
        // registration dialog with dynamic Emergency fields
        SwingUtilities.invokeLater(() -> {
            findTabs().setSelectedIndex(1);
            ((PatientPanel) findTabs().getComponentAt(1)).addPatient();
        });
        robot.delay(1200);
        for (Window w : Window.getWindows()) {
            if (w instanceof PatientDialog && w.isShowing()) {
                PatientDialog d = (PatientDialog) w;
                SwingUtilities.invokeAndWait(() -> selectEmergency(d));
                robot.delay(600);
                capture(robot, d, new File(dir, "8_patient_registration.png"));
                SwingUtilities.invokeAndWait(d::dispose);
            }
        }
        System.out.println("Screenshots written to " + dir.getAbsolutePath());
        System.exit(0);
    }

    /** Performs some realistic actions so each screenshot shows the feature in use. */
    @SuppressWarnings("unchecked")
    private static void prepareTab(int tab) {
        javax.swing.JTabbedPane tabs = findTabs();
        ClinicManager m = manager;
        try {
            if (tab == 0) {
                for (String id : new String[] {"P0004", "P0009", "P0001", "P0002"}) {
                    m.recordAccess(m.findPatient(id));
                }
                m.recordAccess(m.findPatient("P0002"));
            } else if (tab == 5) {
                m.getAppointmentController().scheduleAppointment("P0013", java.time.LocalDate.now().plusDays(2).toString(),
                        "14:00", "Dr. Aisha Lawal", "Vaccination booster");
                m.getAppointmentController().cancelAppointment("A0025");
                m.updatePatient("P0011", new scapms.model.GeneralPatient("P0011", "Michael Obi", 26, "07033334444",
                        "michael.obi@uni.edu", m.findPatient("P0011").getRegistrationDate(), "AXA Mansard"));
                m.deletePatient("P0015");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        tabs.setSelectedIndex(tab);
        java.awt.Container panel = (java.awt.Container) tabs.getComponentAt(tab);
        ((Refreshable) panel).refresh();
        if (tab == 1) {
            findTable(panel).setRowSelectionInterval(1, 1);
        } else if (tab == 4) {
            for (java.awt.Component c : allComponents(panel)) {
                if (c instanceof JComboBox && ((JComboBox<?>) c).getItemAt(0) instanceof scapms.controller.SortEngine.Algorithm) {
                    ((JComboBox<Object>) c).setSelectedItem(scapms.controller.SortEngine.Algorithm.MERGE);
                }
                if (c instanceof JComboBox && ((JComboBox<?>) c).getItemAt(0) instanceof scapms.model.PatientField) {
                    ((JComboBox<Object>) c).setSelectedItem(scapms.model.PatientField.ID);
                }
            }
            clickButton(panel, "SORT");
        } else if (tab == 6) {
            for (java.awt.Component c : allComponents(panel)) {
                if (c instanceof JComboBox && ((JComboBox<?>) c).getItemAt(0) instanceof scapms.controller.ReportGenerator.ReportType) {
                    ((JComboBox<Object>) c).setSelectedItem(scapms.controller.ReportGenerator.ReportType.CATEGORY);
                }
            }
        }
    }

    private static void clickButton(java.awt.Container root, String text) {
        for (java.awt.Component c : allComponents(root)) {
            if (c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) c).getText())) {
                ((javax.swing.JButton) c).doClick();
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectEmergency(PatientDialog d) {
        for (java.awt.Component c : allComponents(d.getContentPane())) {
            if (c instanceof JComboBox && ((JComboBox<?>) c).getItemCount() == PatientCategory.values().length
                    && ((JComboBox<?>) c).getItemAt(0) instanceof PatientCategory) {
                ((JComboBox<PatientCategory>) c).setSelectedItem(PatientCategory.EMERGENCY);
            }
        }
        d.pack();
    }

    private static java.util.List<java.awt.Component> allComponents(java.awt.Container root) {
        java.util.List<java.awt.Component> list = new java.util.ArrayList<>();
        for (java.awt.Component c : root.getComponents()) {
            list.add(c);
            if (c instanceof java.awt.Container) {
                list.addAll(allComponents((java.awt.Container) c));
            }
        }
        return list;
    }

    private static javax.swing.JTabbedPane findTabs() {
        for (java.awt.Component c : allComponents(window.getContentPane())) {
            if (c instanceof javax.swing.JTabbedPane) {
                return (javax.swing.JTabbedPane) c;
            }
        }
        throw new IllegalStateException("no tabs");
    }

    private static javax.swing.JTable findTable(java.awt.Component root) {
        for (java.awt.Component c : allComponents((java.awt.Container) root)) {
            if (c instanceof javax.swing.JTable) {
                return (javax.swing.JTable) c;
            }
        }
        throw new IllegalStateException("no table");
    }

    private static void closeStrayDialogs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing() && !(w instanceof PatientDialog)) {
                    System.out.println("Closing dialog: " + ((JDialog) w).getTitle());
                    w.dispose();
                }
            }
        });
    }

    private static void capture(Robot robot, Window w, File file) throws Exception {
        Rectangle r = w.getBounds();
        BufferedImage img = robot.createScreenCapture(r);
        ImageIO.write(img, "png", file);
    }

    private ScreenshotTool() {
    }
}
