# SCAPMS — Smart Clinic Appointment & Patient Management System (Java)

COS202 Programming II lab assessment, implemented in **Java 17 + Swing** (no external libraries).

## Build & run
Requires a JDK 17 or newer (`java -version`).

| Windows | Linux / macOS / Git Bash |
|---|---|
| `build.bat` then `run.bat` | `./build.sh` then `java -jar SCAPMS.jar` |

Run from this folder so `data/clinic_data.txt` loads on startup. If that file is missing, sample data is created and **Ctrl+S** saves it there. Use *File → Load Sample Data* to reset the demo data, which is generated relative to today's date.

## Project layout
```
src/scapms/
  Main.java                     entry point
  model/        PatientRecord (abstract), GeneralPatient, EmergencyPatient, FollowUpPatient,
                Appointable (interface), Appointment, ClinicDatabase, PatientFactory,
                PatientCategory, AppointmentStatus, PatientField, QueueEntry
  controller/   ClinicManager, AppointmentController, SearchEngine, SortEngine, QueueManager,
                UndoManager, AdminOperation, RecentPatientCache, ReportGenerator, PatientProcessor
  gui/          MainWindow, DashboardPanel, PatientPanel, PatientDialog, AppointmentPanel,
                QueuePanel, SearchPanel, AdminPanel, ReportPanel, table models & renderers
  util/         IDGenerator, FileHandler, Validator, SampleData
  exception/    ClinicException + 7 custom exceptions
data/clinic_data.txt            data file required to run (pipe-delimited text)
docs/REPORT.md                  2-3 page project report
docs/class_diagram.png          UML class diagram
docs/screenshots/               screenshots of every part of the GUI
test/                           LogicTest (205 checks) and ScreenshotTool
tools/UmlDiagram.java           regenerates the class diagram
```

## Requirement → where it is implemented
| Requirement | Location |
|---|---|
| Abstract class + pure virtual functions | `model/PatientRecord.java` (6 abstract methods) |
| 3 subclasses with own behaviour | `GeneralPatient`, `EmergencyPatient`, `FollowUpPatient` |
| Interface `Appointable` | `model/Appointable.java` |
| Runtime polymorphism | `controller/PatientProcessor.processPatient(PatientRecord)`, `AppointmentController.scheduleAppointment` |
| Encapsulation / composition | `ClinicDatabase`, `PatientRecord` ↔ `Appointment`, `ClinicManager` |
| Namespaces | Java packages `scapms.model / controller / gui / util / exception` |
| vector / queue / stack / array | `ClinicDatabase` (ArrayList), `QueueManager` (Queue), `UndoManager` (Stack), `RecentPatientCache` (`PatientRecord[10]`) |
| Linear / binary / recursive search | `controller/SearchEngine.java` |
| Selection / insertion / merge / quick sort | `controller/SortEngine.java` |
| Event handling, timers, shortcuts, tooltips | `gui/MainWindow.java` and the panels |
| Custom rendering, dynamic fields | `CategoryCellRenderer`, `StatusCellRenderer`, `PatientDialog.rebuildExtraFields` |
| Exceptions | `scapms.exception`, `GuiUtils.run`, `FileHandler.load` |
| Reports (5) | `controller/ReportGenerator.java` |

## Tests and documentation tools
```
javac --release 17 -d out <all src files>      (or build.bat)
javac -cp out -d test-out test/scapms/LogicTest.java test/scapms/gui/ScreenshotTool.java
java -cp "out;test-out" scapms.LogicTest                 # 205 logic checks
java -cp "out;test-out" scapms.gui.ScreenshotTool        # regenerates docs/screenshots
java tools/UmlDiagram.java docs/class_diagram.png        # regenerates the UML diagram
```
(On Linux/macOS use `:` instead of `;` in the classpath.)

## Keyboard shortcuts
Ctrl+N New · Ctrl+O Load · Ctrl+S Save · Ctrl+Shift+S Save As · Ctrl+E Export CSV · Ctrl+Q Exit · Ctrl+Z Undo · Ctrl+P Add patient · Ctrl+F Find · Ctrl+1…7 switch tabs · Delete removes the selected patient · F1 shows help
