# SCAPMS — Smart Clinic Appointment & Patient Management System
### COS202 Programming II — Lab Assessment Project Report (Java implementation)

**Name:** ____________________  **Matric No.:** ____________________  **Date:** ____________

---

## 1. Project Description
SCAPMS is a desktop application for a university clinic. Staff use it to register patients and keep their records up to date. They can also schedule and cancel appointments, run the consultation waiting queue, search and sort records, undo administrative mistakes and produce clinic reports. It is written in **Java 17** using the **Swing** GUI toolkit and has no external libraries. The brief specifies C++, so Java equivalents are used: **packages** replace namespaces, **`ArrayList`** replaces `std::vector`, `Queue`/`LinkedList` replace `std::queue`, `java.util.Stack` replaces `std::stack`, and **abstract methods** replace pure virtual functions. Data is stored in a pipe-delimited text file (`data/clinic_data.txt`).

## 2. System Features
- Register, edit (including changing the patient type) and delete patients, with live validation.
- Schedule, cancel, complete and remove appointments. Booking rules differ by patient type.
- A FIFO waiting queue with *Call Next Patient*, waiting times and alerts for long waits.
- Search by ID, name or category using linear, binary or recursive search. *Auto* mode picks the algorithm.
- Sort by name, ID, age or next appointment date using Selection, Insertion, Merge or Quick Sort.
- **Undo Last Administrative Operation**, implemented with a stack.
- A dashboard of statistic cards, today's schedule, a frequently-accessed-patients cache and alerts.
- Five reports: *Most Frequently Attended*, *Pending Appointments*, *Category Distribution*, *Daily Summary* and *Triage & Fee Summary*. Reports can be exported to `.txt`.
- Save/Load through file choosers, CSV export, keyboard shortcuts, tooltips, and timers for the clock, dashboard refresh, appointment reminders and waiting-time alerts.

## 3. Class Hierarchy and Program Organisation
The code is split into five packages so that the GUI, the business logic and the data handling stay separate:

| Package | Responsibility | Main classes |
|---|---|---|
| `scapms.model` | Data and domain rules | `PatientRecord` (abstract), `GeneralPatient`, `EmergencyPatient`, `FollowUpPatient`, `Appointable` (interface), `Appointment`, `ClinicDatabase`, `PatientFactory` |
| `scapms.controller` | Business logic | `ClinicManager`, `AppointmentController`, `SearchEngine`, `SortEngine`, `QueueManager`, `UndoManager`, `AdminOperation`, `RecentPatientCache`, `ReportGenerator`, `PatientProcessor` |
| `scapms.gui` | Presentation only | `MainWindow`, `DashboardPanel`, `PatientPanel`, `PatientDialog`, `AppointmentPanel`, `QueuePanel`, `SearchPanel`, `AdminPanel`, `ReportPanel`, cell renderers |
| `scapms.util` | Helpers | `IDGenerator`, `FileHandler`, `Validator`, `SampleData` |
| `scapms.exception` | Custom exceptions | `ClinicException` and 7 subclasses |

- **Abstract class:** `PatientRecord` holds the ID, name, age, phone, email and registration date. These fields are private and only change through validating setters (encapsulation). It declares six abstract ("pure virtual") methods: `getCategory()`, `getPriorityLevel()`, `getPriorityLabel()`, `calculateConsultationFee()`, `getTypeSpecificDetails()` and `getExtraAttributes()`.
- **Subclasses:** each one adds its own data and behaviour.
  - *General* patients have an insurance provider (20% fee discount), clinic hours only, and at most 3 pending appointments.
  - *Emergency* patients have a severity level 1–5 and a condition. They can be booked at any hour, must be seen within 3 days, their appointments are flagged URGENT, and the fee scales with severity.
  - *Follow-Up* patients have a last visit date, a referring doctor and a review interval. A booking must fall after the last visit, the doctor defaults to the referring doctor, and a recommended review date is calculated.
- **Interface:** `Appointable` declares `scheduleAppointment()`, `cancelAppointment()`, `getAppointments()` and `getBookingRule()`. All three subclasses implement it.
- **Polymorphism:** `PatientProcessor.processPatient(PatientRecord p)` takes a base-class reference. At run time Java calls the subclass's overridden priority and fee methods, which the Triage report shows. `AppointmentController` calls `((Appointable) patient).scheduleAppointment(a)` without knowing the patient type, and `getFullDetails()` is a template method built on the abstract methods.
- **Composition:** `ClinicDatabase` owns the patient collection. Each `PatientRecord` owns its list of `Appointment` objects (one patient → many appointments). `ClinicManager` is made up of the database, queue, undo stack, cache, search and sort engines.

## 4. Data Structures Used
| Structure | Where | Purpose |
|---|---|---|
| `ArrayList<PatientRecord>` (vector) | `ClinicDatabase` | Registered patients; index access is needed for binary search and sorting. |
| `Queue<QueueEntry>` (`LinkedList`) | `QueueManager` | Waiting queue: `offer()` at the back, `poll()` from the front, so the first to arrive is the first served. |
| `Stack<AdminOperation>` | `UndoManager` | Undo history. The most recent operation is on top and is reversed first (LIFO). Each operation stores the code that reverses it. |
| `PatientRecord[10]` fixed array | `RecentPatientCache` | Frequently accessed patients. An access moves the record to slot 0 by shifting elements; when the array is full, the least-recently-used record in slot 9 is dropped. |

## 5. Searching Algorithms (all hand-written in `SearchEngine`)
- **Linear search** (by ID, name or category), O(n). It works on unsorted data, which is the normal state because new patients are appended.
- **Binary search** (by ID; by name prefix), O(log n). It is only allowed when `ClinicDatabase.isSortedBy(field)` is true. For names it finds the first name ≥ the prefix (lower bound) and then collects the neighbouring matches.
- **Recursive search** (by name). This is a divide-and-conquer recursion: the range is split in half until it holds a single record (the base case), and matching names are collected left-to-right. The recursion depth is only O(log n).
- **Choosing by data state:** in *Auto* mode the engine picks binary search if the records are currently sorted by the searched field. Otherwise it uses linear search for IDs and recursive search for names. The GUI shows which one will be used before the search runs.

## 6. Sorting Algorithms (all hand-written in `SortEngine`) and Why
The user chooses the algorithm, the key and the direction from combo boxes. The *"Why this algorithm?"* box explains the choice, and the result line reports comparisons, moves and time.
- **Selection Sort**, O(n²). It makes the fewest swaps (≤ n−1), and the code is simple and predictable. This is adequate for a clinic list of tens to hundreds of records.
- **Insertion Sort**, O(n) on nearly-sorted data. It is the best choice after a few patients are added to an already-sorted list. It is stable. A copy of it also orders appointments by date.
- **Merge Sort** (recursive), guaranteed O(n log n) and stable. Sorting by age after sorting by name keeps patients of the same age in name order. It is best for large data.
- **Quick Sort** (recursive), O(n log n) on average and in place. It uses a middle-element pivot to avoid the O(n²) worst case on already-sorted lists.

The default recommendation is **Insertion Sort** for this clinic's small, mostly-ordered data, and **Merge Sort** when a stable O(n log n) result is needed.

## 7. Recursive Algorithms
`recursiveSearchByName()` (divide and conquer), `mergeSort()` and `quickSort()` are all recursive. Each has a clear base case: an empty range or a single element.

## 8. GUI Design and Layout Management
`MainWindow` contains a menu bar (File: New/Load/Save/Save As/Export/Exit; Edit: Undo/Add/Find; View; Help), seven tabs and a status bar with a live clock and queue alerts. Several layout managers are used, each for a reason:
- **`BorderLayout`** builds the top-level structure of every tab: toolbar at the top (NORTH), table in the middle (CENTER), status at the bottom (SOUTH). The table automatically takes all spare space when the window is resized.
- **`GridBagLayout`** is used for the registration and scheduling forms. It aligns labels and fields in columns like a form layout, lets the fields stretch, and puts live validation hints next to them.
- **`GridLayout`** holds the equal-sized dashboard statistic cards and the two equal Search/Sort columns.
- **`FlowLayout`** arranges rows of buttons. **`BoxLayout`** stacks the Consultation Room panel vertically. **`JSplitPane`** lets the user resize the table and details areas.

Advanced techniques implemented:
- **Custom table rendering:** Emergency rows are red and show *High Priority*, General rows are green (*Normal*) and Follow-Up rows are blue (*Scheduled*). Appointment rows are coloured by status.
- **Dynamic components:** the type-specific fields in the patient form are removed and rebuilt when the type changes.
- **File dialogs:** load, save, CSV export and report export.
- **Timers:** clock, 5-second dashboard refresh, appointment reminders and waiting-time alerts.
- **Input-validation dialogs** (for example "Error: Patient age must be between 0 and 120.").
- **Keyboard shortcuts:** Ctrl+S, Ctrl+F, Ctrl+Q, Ctrl+Z and others.
- **Tooltips** on all important controls.

Events are handled with `ActionListener`s (buttons, menus, combo boxes), `DocumentListener`s (text-field changes: ID/age/date validation, quick filter, search hint), `ListSelectionListener`/`MouseListener` (table selection and double-click), `ChangeListener` (tabs), `WindowListener` (unsaved-changes prompt) and `javax.swing.Timer`. The controller notifies the GUI of data changes through an observer callback (`ClinicManager.addChangeListener`).

## 9. Exception Handling Approach
All custom exceptions extend the checked base class **`ClinicException`**:
- `InvalidPatientException`, and its subclass `InvalidAgeException`
- `DuplicatePatientException`
- `InvalidAppointmentException`
- `FileLoadException`
- `InvalidSearchException`
- `PatientNotFoundException`

The model and controller **throw** them with a meaningful message. The GUI wraps every action in `try { … } catch (ClinicException e) { … } catch (Exception e) { … }` (`GuiUtils.run`) and shows an error dialog, so the program keeps running after any user error. Some notable cases:
- In the patient dialog, specific catch blocks move the focus to the field at fault.
- `FileHandler.load()` validates the whole file first, so a corrupt file never replaces the current data.
- Saving writes to a temporary file first.
- A default uncaught-exception handler reports any unexpected error instead of terminating.

## 10. Challenges Encountered and How They Were Resolved
1. **Making binary search safe.** Binary search is only correct on sorted data, but edits and insertions break the order. *Resolution:* `ClinicDatabase` records which field it is sorted by. Any add, insert or replace clears that flag, `SearchEngine` refuses to binary-search unsorted data, and *Auto* falls back to linear or recursive search.
2. **Undoing operations correctly.** Reversing a delete must put the patient back in its original position, including its place in the waiting queue. Undoing an edit must not lose appointments booked afterwards. *Resolution:* each `AdminOperation` captures exactly the state it needs (index, queue position, original object) in a small closure. Undo resolves patients by ID at undo time and moves the current appointment list back to the restored object.
3. **Keeping the array cache valid** when records are edited (replaced) or deleted. *Resolution:* `RecentPatientCache.replace()` and `remove()` are called by `ClinicManager` at every change.
4. **A responsive GUI without threading bugs.** *Resolution:* `javax.swing.Timer` was used, because it fires on the event-dispatch thread. The reminder dialog is non-modal, so it never blocks the user.
5. **Different booking rules per patient type without `if/else` chains in the controller.** *Resolution:* each subclass implements `Appointable.scheduleAppointment()` with its own rules (polymorphism).
6. **Testing.** *Resolution:* a self-checking test program (`test/scapms/LogicTest.java`, 205 checks) verifies every sort algorithm and key, compares binary, recursive and linear search results, and covers undo, the queue, the cache, validation and the save/load round-trip.
