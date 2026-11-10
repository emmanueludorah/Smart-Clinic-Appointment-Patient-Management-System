package scapms.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import scapms.exception.ClinicException;

/** Shared colours, factory methods and error-handling helpers for the GUI. */
final class GuiUtils {

    static final Color PRIMARY = new Color(25, 85, 150);
    static final Color ERROR = new Color(190, 30, 30);
    static final Color SUCCESS = new Color(20, 130, 40);
    static final Color WARNING = new Color(200, 110, 0);

    static final Color EMERGENCY_BG = new Color(255, 215, 215);
    static final Color EMERGENCY_FG = new Color(150, 0, 0);
    static final Color GENERAL_BG = new Color(228, 245, 228);
    static final Color FOLLOWUP_BG = new Color(222, 234, 255);
    static final Color FOLLOWUP_FG = new Color(20, 60, 150);

    static final Color PENDING_BG = new Color(255, 244, 206);
    static final Color COMPLETED_BG = new Color(214, 238, 220);
    static final Color CANCELLED_BG = new Color(232, 232, 232);

    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** A block of code that may throw (used with {@link #run}). */
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    private GuiUtils() {
    }

    static JButton button(String text, String tooltip, ActionListener listener) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.addActionListener(listener);
        return b;
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, "Error: " + message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    static void showWarning(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Runs an action and turns any exception into an error dialog, so a user error
     * never terminates the application.
     * @return true if the action completed without an exception
     */
    static boolean run(Component parent, Action action) {
        try {
            action.run();
            return true;
        } catch (ClinicException e) {
            showError(parent, e.getMessage());
        } catch (Exception e) {
            showError(parent, "Unexpected problem: " + e);
        }
        return false;
    }

    /** Wraps a Runnable as a DocumentListener (text-field change events). */
    static DocumentListener onTextChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    static void titled(JComponent component, String title) {
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title), BorderFactory.createEmptyBorder(4, 6, 6, 6)));
    }

    /** Adds "label | field | hint" as one row of a GridBagLayout form. */
    static void addFormRow(JPanel form, int row, String label, JComponent field, JComponent hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        if (hint != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(hint, c);
        }
    }

    static void setHint(JLabel label, boolean ok, String text) {
        label.setForeground(ok ? SUCCESS : ERROR);
        label.setText((ok ? "✔ " : "✖ ") + text);
    }
}
