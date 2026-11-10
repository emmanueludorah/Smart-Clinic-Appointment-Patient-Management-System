package scapms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;

import scapms.controller.ClinicManager;
import scapms.exception.ClinicException;
import scapms.exception.DuplicatePatientException;
import scapms.exception.InvalidAgeException;
import scapms.model.PatientCategory;
import scapms.model.PatientFactory;
import scapms.model.PatientRecord;
import scapms.util.Validator;

/**
 * Modal dialog for registering or editing a patient.
 * <ul>
 *   <li>Dynamic components: the type-specific fields are removed and re-created whenever
 *       the patient type combo box changes.</li>
 *   <li>Text-field events: the ID and age fields are validated live while typing.</li>
 *   <li>Input validation: invalid data produces an error dialog and the form stays open.</li>
 * </ul>
 */
class PatientDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    /** Receives the validated patient; throws if the controller rejects it. */
    @FunctionalInterface
    interface Submitter {
        void submit(PatientRecord patient) throws ClinicException;
    }

    private final transient ClinicManager manager;
    private final transient PatientRecord existing;
    private final transient Submitter submitter;

    private final JComboBox<PatientCategory> typeCombo = new JComboBox<>(PatientCategory.values());
    private final JTextField idField = new JTextField(10);
    private final JTextField nameField = new JTextField(22);
    private final JTextField ageField = new JTextField(5);
    private final JTextField phoneField = new JTextField(14);
    private final JTextField emailField = new JTextField(22);
    private final JLabel idHint = new JLabel(" ");
    private final JLabel nameHint = new JLabel(" ");
    private final JLabel ageHint = new JLabel(" ");
    private final JPanel extraPanel = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> extraInputs = new LinkedHashMap<>();

    private transient PatientRecord result;

    private PatientDialog(Frame owner, ClinicManager manager, PatientRecord existing, Submitter submitter) {
        super(owner, existing == null ? "Register New Patient" : "Edit Patient " + existing.getPatientId(), true);
        this.manager = manager;
        this.existing = existing;
        this.submitter = submitter;
        buildUi();
        populate();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    /** @return the registered patient, or null if the user cancelled. */
    static PatientRecord showAddDialog(Frame owner, ClinicManager manager, Submitter submitter) {
        PatientDialog d = new PatientDialog(owner, manager, null, submitter);
        d.setVisible(true);
        return d.result;
    }

    /** @return the edited patient, or null if the user cancelled. */
    static PatientRecord showEditDialog(Frame owner, ClinicManager manager, PatientRecord existing, Submitter submitter) {
        PatientDialog d = new PatientDialog(owner, manager, existing, submitter);
        d.setVisible(true);
        return d.result;
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel heading = new JLabel(existing == null ? "Patient Registration" : "Edit Patient Information");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        heading.setForeground(GuiUtils.PRIMARY);
        content.add(heading, BorderLayout.NORTH);

        // ---- basic information form (GridBagLayout behaves like a form layout)
        JPanel basic = new JPanel(new GridBagLayout());
        GuiUtils.titled(basic, "Basic Information");
        typeCombo.setToolTipText("Choosing a type changes the extra fields shown below");
        idField.setToolTipText("Format: P followed by 4-6 digits, e.g. P0016");
        nameField.setToolTipText("Full name (letters, spaces, apostrophes, hyphens)");
        ageField.setToolTipText("Whole number between 0 and 120");
        phoneField.setToolTipText("7-15 digits, may start with +");
        emailField.setToolTipText("Optional");

        JPanel idRow = new JPanel(new BorderLayout(4, 0));
        idRow.add(idField, BorderLayout.CENTER);
        JButton generate = GuiUtils.button("Generate", "Fill in the next free Patient ID",
                e -> idField.setText(manager.getIdGenerator().peekPatientId(manager.getDatabase())));
        generate.setEnabled(existing == null);
        idRow.add(generate, BorderLayout.EAST);

        int row = 0;
        GuiUtils.addFormRow(basic, row++, "Patient Type:", typeCombo, null);
        GuiUtils.addFormRow(basic, row++, "Patient ID:", idRow, idHint);
        GuiUtils.addFormRow(basic, row++, "Full Name:", nameField, nameHint);
        GuiUtils.addFormRow(basic, row++, "Age:", ageField, ageHint);
        GuiUtils.addFormRow(basic, row++, "Phone:", phoneField, null);
        GuiUtils.addFormRow(basic, row, "Email:", emailField, null);

        JPanel centre = new JPanel(new BorderLayout(0, 8));
        centre.add(basic, BorderLayout.NORTH);
        GuiUtils.titled(extraPanel, "Details");
        centre.add(extraPanel, BorderLayout.CENTER);
        content.add(centre, BorderLayout.CENTER);

        // ---- buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = GuiUtils.button(existing == null ? "Register Patient" : "Save Changes",
                "Validate and save this patient (Enter)", e -> onSave());
        JButton cancel = GuiUtils.button("Cancel", "Close without saving (Esc)", e -> dispose());
        buttons.add(save);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(content);

        // ---- events
        typeCombo.addActionListener(e -> rebuildExtraFields((PatientCategory) typeCombo.getSelectedItem(), null));
        idField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::validateIdLive));
        nameField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::validateNameLive));
        ageField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::validateAgeLive));
    }

    private void populate() {
        if (existing == null) {
            idField.setText(manager.getIdGenerator().peekPatientId(manager.getDatabase()));
            typeCombo.setSelectedItem(PatientCategory.GENERAL);
            rebuildExtraFields(PatientCategory.GENERAL, null);
        } else {
            idField.setText(existing.getPatientId());
            idField.setEditable(false);
            nameField.setText(existing.getName());
            ageField.setText(String.valueOf(existing.getAge()));
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            typeCombo.setSelectedItem(existing.getCategory());          // fires rebuild with defaults
            rebuildExtraFields(existing.getCategory(), existing.getExtraAttributes());
        }
        validateIdLive();
        validateNameLive();
        validateAgeLive();
    }

    /** Dynamic components: removes the old type-specific fields and adds the new ones. */
    private void rebuildExtraFields(PatientCategory category, Map<String, String> values) {
        if (category == null) {
            return;
        }
        extraPanel.removeAll();
        extraInputs.clear();
        ((TitledBorder) ((javax.swing.border.CompoundBorder) extraPanel.getBorder()).getOutsideBorder())
                .setTitle(category + " Patient Details");
        List<PatientFactory.FieldSpec> specs = PatientFactory.extraFieldsFor(category);
        int row = 0;
        for (PatientFactory.FieldSpec spec : specs) {
            String value = values != null && values.containsKey(spec.getKey()) ? values.get(spec.getKey())
                    : spec.getDefaultValue();
            JComponent input;
            if (spec.getOptions().length > 0) {
                JComboBox<String> combo = new JComboBox<>(spec.getOptions());
                combo.setSelectedItem(value);
                input = combo;
            } else {
                input = new JTextField(value, 22);
            }
            input.setToolTipText(spec.getTooltip());
            extraInputs.put(spec.getKey(), input);
            GuiUtils.addFormRow(extraPanel, row++, spec.getLabel(), input, null);
        }
        JLabel info = new JLabel(describe(category));
        info.setForeground(GuiUtils.PRIMARY);
        GuiUtils.addFormRow(extraPanel, row, " ", info, null);
        extraPanel.revalidate();
        extraPanel.repaint();
        if (isVisible()) {
            pack();
        }
    }

    private static String describe(PatientCategory category) {
        switch (category) {
            case EMERGENCY: return "High priority - seen within 3 days, any hour.";
            case FOLLOW_UP: return "Scheduled review after the last visit.";
            default: return "Normal priority - clinic hours, max 3 pending appointments.";
        }
    }

    // ------------------------------------------------------- live validation

    private void validateIdLive() {
        if (existing != null) {
            idHint.setForeground(GuiUtils.PRIMARY);
            idHint.setText("ID cannot be changed");
            return;
        }
        String id = idField.getText().trim().toUpperCase();
        if (id.isEmpty()) {
            GuiUtils.setHint(idHint, false, "Required");
        } else if (!Validator.isValidPatientId(id)) {
            GuiUtils.setHint(idHint, false, "Format: P0001");
        } else if (manager.getDatabase().containsId(id)) {
            GuiUtils.setHint(idHint, false, "ID already exists");
        } else {
            GuiUtils.setHint(idHint, true, "Available");
        }
    }

    private void validateNameLive() {
        try {
            Validator.requireName(nameField.getText());
            GuiUtils.setHint(nameHint, true, "OK");
        } catch (ClinicException e) {
            GuiUtils.setHint(nameHint, false, nameField.getText().trim().isEmpty() ? "Required" : "Invalid");
        }
    }

    private void validateAgeLive() {
        try {
            Validator.parseAge(ageField.getText());
            GuiUtils.setHint(ageHint, true, "OK");
        } catch (InvalidAgeException e) {
            GuiUtils.setHint(ageHint, false, "0 - 120");
        }
    }

    // ------------------------------------------------------------------ save

    private void onSave() {
        try {
            PatientCategory category = (PatientCategory) typeCombo.getSelectedItem();
            int age = Validator.parseAge(ageField.getText());
            Map<String, String> extras = new LinkedHashMap<>();
            for (Map.Entry<String, JComponent> e : extraInputs.entrySet()) {
                JComponent c = e.getValue();
                String value = c instanceof JComboBox ? String.valueOf(((JComboBox<?>) c).getSelectedItem())
                        : ((JTextField) c).getText();
                extras.put(e.getKey(), value);
            }
            LocalDate registered = existing == null ? LocalDate.now() : existing.getRegistrationDate();
            PatientRecord patient = PatientFactory.create(category, idField.getText(), nameField.getText(), age,
                    phoneField.getText(), emailField.getText(), registered, extras);
            submitter.submit(patient);
            result = patient;
            dispose();
        } catch (InvalidAgeException e) {
            GuiUtils.showError(this, e.getMessage());
            ageField.requestFocusInWindow();
            ageField.selectAll();
        } catch (DuplicatePatientException e) {
            GuiUtils.showError(this, e.getMessage());
            idField.requestFocusInWindow();
            idField.selectAll();
        } catch (ClinicException e) {
            GuiUtils.showError(this, e.getMessage());
        } catch (RuntimeException e) {
            GuiUtils.showError(this, "Unexpected problem: " + e);
        }
    }
}
