package scapms.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import scapms.controller.ClinicManager;
import scapms.controller.SearchEngine;
import scapms.controller.SortEngine;
import scapms.model.ClinicDatabase;
import scapms.model.PatientField;

/**
 * "Search &amp; Sort" tab: choose a search algorithm / field and a sorting algorithm / key.
 * The rationale for the chosen sorting algorithm is displayed next to the controls.
 */
class SearchPanel extends JPanel implements Refreshable {
    private static final long serialVersionUID = 1L;

    private final transient ClinicManager manager;
    private final MainWindow window;

    private final JTextField keywordField = new JTextField(16);
    private final JComboBox<SearchEngine.Field> fieldCombo = new JComboBox<>(SearchEngine.Field.values());
    private final JComboBox<SearchEngine.Algorithm> searchAlgoCombo = new JComboBox<>(SearchEngine.Algorithm.values());
    private final JLabel searchHint = new JLabel(" ");

    private final JComboBox<SortEngine.Algorithm> sortAlgoCombo = new JComboBox<>(SortEngine.Algorithm.values());
    private final JComboBox<PatientField> sortKeyCombo = new JComboBox<>(PatientField.values());
    private final JComboBox<String> orderCombo = new JComboBox<>(new String[] {"Ascending", "Descending"});
    private final JTextArea rationaleArea = new JTextArea(5, 30);

    private final PatientTableModel model = new PatientTableModel();
    private final JTable table = new JTable(model);
    private final JLabel resultLabel = new JLabel("Showing all patients.");
    private boolean showingSearchResults;

    SearchPanel(ClinicManager manager, MainWindow window) {
        super(new BorderLayout(6, 6));
        this.manager = manager;
        this.window = window;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new GridLayout(1, 2, 8, 0));     // two equal columns
        controls.add(buildSearchBox());
        controls.add(buildSortBox());
        add(controls, BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new CategoryCellRenderer(
                r -> model.getPatientAt(r) == null ? null : model.getPatientAt(r).getCategory(),
                PatientTableModel.PRIORITY_COLUMN));
        table.setDefaultRenderer(Integer.class, table.getDefaultRenderer(Object.class));
        JScrollPane scroll = new JScrollPane(table);
        GuiUtils.titled(scroll, "Results");
        add(scroll, BorderLayout.CENTER);

        resultLabel.setForeground(GuiUtils.PRIMARY);
        add(resultLabel, BorderLayout.SOUTH);

        // ---- events
        keywordField.addActionListener(e -> doSearch());                                  // Enter key
        keywordField.getDocument().addDocumentListener(GuiUtils.onTextChange(this::updateSearchHint));
        fieldCombo.addActionListener(e -> updateSearchHint());
        searchAlgoCombo.addActionListener(e -> updateSearchHint());
        sortAlgoCombo.addActionListener(e -> updateRationale());
        sortKeyCombo.addActionListener(e -> updateRationale());
        updateRationale();
    }

    private JPanel buildSearchBox() {
        JPanel box = new JPanel(new GridBagLayout());
        GuiUtils.titled(box, "Search Patients");
        keywordField.setToolTipText("ID (e.g. P0004), part of a name, or a category (General / Emergency / Follow-Up). Press Enter to search.");
        fieldCombo.setToolTipText("Which field to search");
        searchAlgoCombo.setToolTipText("Auto picks Binary Search when the data is sorted by the searched field");
        GuiUtils.addFormRow(box, 0, "Keyword:", keywordField, null);
        GuiUtils.addFormRow(box, 1, "Search by:", fieldCombo, null);
        GuiUtils.addFormRow(box, 2, "Algorithm:", searchAlgoCombo, null);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.add(GuiUtils.button("Search", "Run the search (Enter)", e -> doSearch()));
        buttons.add(GuiUtils.button("Show All", "Clear the search and list every patient", e -> showAll()));
        GuiUtils.addFormRow(box, 3, " ", buttons, null);

        searchHint.setForeground(GuiUtils.PRIMARY);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weighty = 1.0;                       // pushes the form to the top of the box
        c.insets = new Insets(4, 4, 4, 4);
        box.add(searchHint, c);
        return box;
    }

    private JPanel buildSortBox() {
        JPanel box = new JPanel(new GridBagLayout());
        GuiUtils.titled(box, "Sort Patient Records");
        sortAlgoCombo.setToolTipText("Choose the sorting algorithm - its rationale is shown below");
        sortKeyCombo.setToolTipText("Field to sort by");
        orderCombo.setToolTipText("Sort direction (binary search needs ascending order)");
        GuiUtils.addFormRow(box, 0, "Sort Algorithm:", sortAlgoCombo, null);
        GuiUtils.addFormRow(box, 1, "Sort By:", sortKeyCombo, null);
        GuiUtils.addFormRow(box, 2, "Order:", orderCombo, null);
        JButton sort = GuiUtils.button("SORT", "Sort all patient records with the chosen algorithm", e -> doSort());
        GuiUtils.addFormRow(box, 3, " ", sort, null);

        rationaleArea.setEditable(false);
        rationaleArea.setLineWrap(true);
        rationaleArea.setWrapStyleWord(true);
        rationaleArea.setBackground(box.getBackground());
        rationaleArea.setFont(resultLabel.getFont());
        JScrollPane rationaleScroll = new JScrollPane(rationaleArea);
        rationaleScroll.setBorder(BorderFactory.createTitledBorder("Why this algorithm?"));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 4, 4, 4);
        box.add(rationaleScroll, c);
        return box;
    }

    @Override
    public void refresh() {
        if (!showingSearchResults) {
            model.setPatients(manager.getDatabase().getPatients());
        }
        updateSearchHint();
        updateRationale();
    }

    void focusSearchField() {
        keywordField.requestFocusInWindow();
        keywordField.selectAll();
    }

    /** Explains which algorithm AUTO will use given the current state of the data. */
    private void updateSearchHint() {
        SearchEngine.Field field = (SearchEngine.Field) fieldCombo.getSelectedItem();
        SearchEngine.Algorithm requested = (SearchEngine.Algorithm) searchAlgoCombo.getSelectedItem();
        ClinicDatabase db = manager.getDatabase();
        SearchEngine.Algorithm actual = manager.getSearchEngine().chooseAlgorithm(db, field, requested);
        String state = db.getSortedBy() == null ? "records are unsorted"
                : "records are sorted by " + db.getSortedBy() + (db.isSortedAscending() ? "" : " (descending)");
        searchHint.setText("<html>Will use <b>" + actual + "</b> - " + state + "</html>");
    }

    private void updateRationale() {
        SortEngine.Algorithm algo = (SortEngine.Algorithm) sortAlgoCombo.getSelectedItem();
        if (algo == null) {
            return;
        }
        ClinicDatabase db = manager.getDatabase();
        boolean partlySorted = db.getSortedBy() != null && db.getSortedBy() == sortKeyCombo.getSelectedItem();
        rationaleArea.setText(algo + "  [" + algo.getComplexity() + "]\n" + algo.getRationale() + "\n\n"
                + SortEngine.recommend(db.size(), partlySorted));
        rationaleArea.setCaretPosition(0);
    }

    private void doSearch() {
        String keyword = keywordField.getText();
        SearchEngine.Field field = (SearchEngine.Field) fieldCombo.getSelectedItem();
        SearchEngine.Algorithm algo = (SearchEngine.Algorithm) searchAlgoCombo.getSelectedItem();
        SearchEngine.SearchResult[] holder = new SearchEngine.SearchResult[1];
        if (!GuiUtils.run(this, () -> holder[0] = manager.searchPatients(keyword, field, algo))) {
            return;
        }
        SearchEngine.SearchResult r = holder[0];
        showingSearchResults = true;
        model.setPatients(r.getMatches());
        if (!r.getMatches().isEmpty()) {
            manager.recordAccess(r.getMatches().get(0));
        }
        resultLabel.setText(String.format("<html>Found <b>%d</b> match(es) for '%s' using <b>%s</b>: %d comparisons, "
                + "%.3f ms.<br>%s</html>", r.getMatches().size(), keyword.trim(), r.getAlgorithmUsed(),
                r.getComparisons(), r.getElapsedNanos() / 1e6, r.getExplanation()));
        window.setStatus("Search complete - " + r.getMatches().size() + " match(es)");
        if (r.getMatches().isEmpty()) {
            GuiUtils.showInfo(this, "No Results", "No patient matched '" + keyword.trim() + "'.");
        }
    }

    private void showAll() {
        showingSearchResults = false;
        keywordField.setText("");
        model.setPatients(manager.getDatabase().getPatients());
        resultLabel.setText("Showing all " + model.getRowCount() + " patients.");
    }

    private void doSort() {
        SortEngine.Algorithm algo = (SortEngine.Algorithm) sortAlgoCombo.getSelectedItem();
        PatientField key = (PatientField) sortKeyCombo.getSelectedItem();
        boolean ascending = orderCombo.getSelectedIndex() == 0;
        if (manager.getDatabase().isEmpty()) {
            GuiUtils.showWarning(this, "There are no patient records to sort.");
            return;
        }
        showingSearchResults = false;
        SortEngine.SortResult r = manager.sortPatients(algo, key, ascending);
        model.setPatients(manager.getDatabase().getPatients());
        resultLabel.setText(String.format("<html>Sorted <b>%d</b> records by <b>%s</b> (%s) using <b>%s</b>: "
                + "%d comparisons, %d element moves, %.3f ms.</html>", r.getSize(), r.getField(),
                r.isAscending() ? "ascending" : "descending", r.getAlgorithm(), r.getComparisons(), r.getMoves(),
                r.getElapsedNanos() / 1e6));
        window.setStatus("Sorted by " + key + " using " + algo);
    }
}
