import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Draws the SCAPMS UML class diagram to a PNG (no external tools required).
 * Run from the project folder:  java tools/UmlDiagram.java docs/class_diagram.png
 */
public class UmlDiagram {

    static final int W = 2400, H = 1480, LINE = 16;
    static final Color BORDER = new Color(60, 60, 60);
    static final Color MODEL = new Color(255, 250, 226), ABSTRACT = new Color(255, 236, 214),
            INTERFACE = new Color(238, 230, 255), CONTROLLER = new Color(228, 239, 255),
            GUI = new Color(230, 246, 230), UTIL = new Color(240, 240, 240), EXC = new Color(255, 232, 232);
    static final Stroke SOLID = new BasicStroke(1.4f);
    static final Stroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] {7f, 5f}, 0f);

    static Graphics2D g;
    static Font nameFont, textFont, italicFont, stereoFont, frameFont;
    static final List<Box> boxes = new ArrayList<>();
    static final List<Runnable> lines = new ArrayList<>();
    static final List<Runnable> heads = new ArrayList<>();

    static class Box {
        String stereo, name;
        String[] fields, methods;
        int x, y, w, h;
        Color fill;
        boolean italicName;

        int cx() { return x + w / 2; }
        int midY() { return y + h / 2; }
        int right() { return x + w; }
        int bottom() { return y + h; }
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "docs/class_diagram.png");
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        nameFont = new Font("SansSerif", Font.BOLD, 14);
        textFont = new Font("SansSerif", Font.PLAIN, 12);
        italicFont = new Font("SansSerif", Font.ITALIC, 12);
        stereoFont = new Font("SansSerif", Font.ITALIC, 11);
        frameFont = new Font("SansSerif", Font.BOLD, 14);

        g.setColor(new Color(25, 85, 150));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("SCAPMS - UML Class Diagram (Java implementation)", 24, 32);
        g.translate(0, 44);

        // ---------------------------------------------------------------- frames
        frame("package scapms.model", 20, 20, 1190, 720);
        frame("package scapms.controller", 1230, 20, 1150, 720);
        frame("package scapms.gui", 20, 780, 1190, 400);
        frame("package scapms.util", 20, 1200, 1190, 220);
        frame("package scapms.exception", 1230, 780, 1150, 640);

        // ---------------------------------------------------------------- model
        Box db = box(null, "ClinicDatabase", 40, 60, 330, MODEL,
                new String[] {"- patients : ArrayList<PatientRecord>", "- sortedBy : PatientField"},
                new String[] {"+ add(p : PatientRecord)", "+ remove(p) : int", "+ replace(old, new)",
                    "+ applyOrder(sorted[], key, asc)", "+ isSortedBy(field) : boolean"});
        Box cat = box("enumeration", "PatientCategory", 40, db.bottom() + 24, 160, MODEL,
                new String[] {"GENERAL", "EMERGENCY", "FOLLOW_UP"}, null);
        box("enumeration", "AppointmentStatus", 210, db.bottom() + 24, 160, MODEL,
                new String[] {"PENDING", "COMPLETED", "CANCELLED"}, null);
        box(null, "PatientFactory", 40, cat.bottom() + 20, 330, MODEL, null,
                new String[] {"+ create(category, id, ...) : PatientRecord", "+ extraFieldsFor(category) : List"});

        Box pr = box("abstract", "PatientRecord", 410, 60, 390, ABSTRACT,
                new String[] {"- patientId : String", "- name : String", "- age : int", "- phone : String",
                    "- email : String", "- registrationDate : LocalDate", "- appointments : List<Appointment>"},
                new String[] {"+ getCategory() : PatientCategory *", "+ getPriorityLevel() : int *",
                    "+ getPriorityLabel() : String *", "+ calculateConsultationFee() : double *",
                    "+ getTypeSpecificDetails() : String *", "+ getExtraAttributes() : Map *",
                    "+ getFullDetails() : String", "# validateNewAppointment(a)", "+ attachAppointment(a)"});
        pr.italicName = true;
        Box appt = box(null, "Appointment", 850, 60, 330, MODEL,
                new String[] {"- appointmentId : String", "- patientId : String", "- date : LocalDate",
                    "- time : LocalTime", "- doctor : String", "- reason : String",
                    "- status : AppointmentStatus", "- urgent : boolean"},
                new String[] {"+ markCompleted()", "+ cancel()", "+ getDateTime() : LocalDateTime"});
        Box appointable = box("interface", "Appointable", 850, appt.bottom() + 30, 330, INTERFACE, null,
                new String[] {"+ scheduleAppointment(a : Appointment) *", "+ cancelAppointment(id : String) *",
                    "+ getAppointments() : List *", "+ getBookingRule() : String *"});
        appointable.italicName = true;

        int subY = 560;
        Box gp = box(null, "GeneralPatient", 40, subY, 330, MODEL,
                new String[] {"- insuranceProvider : String"},
                new String[] {"+ scheduleAppointment(a)", "+ cancelAppointment(id)", "+ calculateConsultationFee()"});
        Box ep = box(null, "EmergencyPatient", 400, subY, 360, MODEL,
                new String[] {"- severityLevel : int", "- condition : String"},
                new String[] {"+ scheduleAppointment(a)", "+ cancelAppointment(id)", "# requiresClinicHours() : boolean"});
        Box fp = box(null, "FollowUpPatient", 790, subY, 390, MODEL,
                new String[] {"- lastVisitDate : LocalDate", "- referringDoctor : String", "- followUpIntervalDays : int"},
                new String[] {"+ scheduleAppointment(a)", "+ cancelAppointment(id)", "+ getRecommendedFollowUpDate()"});

        inheritanceTree(pr, subY - 40, gp, ep, fp);
        realization(appointable, subY - 18, gp, ep, fp);
        // PatientRecord 1 <>-- 0..* Appointment
        composition(pr.right(), pr.y + 60, appt.x, pr.y + 60, "1", "0..*", "has");
        // ClinicDatabase <>-- PatientRecord
        composition(db.right(), db.y + 60, pr.x, db.y + 60, "1", "0..*", "stores");

        // ----------------------------------------------------------- controller
        Box cm = box(null, "ClinicManager", 1250, 60, 410, CONTROLLER,
                new String[] {"- database : ClinicDatabase", "- queueManager : QueueManager",
                    "- undoManager : UndoManager", "- recentCache : RecentPatientCache",
                    "- searchEngine : SearchEngine", "- sortEngine : SortEngine",
                    "- appointmentController : AppointmentController", "- reportGenerator : ReportGenerator"},
                new String[] {"+ registerPatient(p)", "+ updatePatient(id, p)", "+ deletePatient(id)",
                    "+ undoLastOperation() : AdminOperation", "+ addToQueue(id) / callNextPatient()",
                    "+ sortPatients(...) / searchPatients(...)", "+ save(file) / load(file)"});
        int c2 = 1700, c2w = 320;
        Box se = box(null, "SearchEngine", c2, 60, c2w, CONTROLLER, null,
                new String[] {"+ search(db, query, field, algo)", "+ linearSearchById(list, id)",
                    "+ binarySearchById(sorted, id) : int", "+ binarySearchByNamePrefix(sorted, p)",
                    "+ recursiveSearchByName(list, kw, lo, hi, out)"});
        Box so = box(null, "SortEngine", c2, se.bottom() + 24, c2w, CONTROLLER,
                new String[] {"- comparisons, moves : long"},
                new String[] {"+ sort(db, algorithm, field, asc)", "~ selectionSort(a[])", "~ insertionSort(a[])",
                    "~ mergeSort(a[], tmp[], lo, hi)", "~ quickSort(a[], lo, hi)"});
        Box ac = box(null, "AppointmentController", c2, so.bottom() + 24, c2w, CONTROLLER, null,
                new String[] {"+ scheduleAppointment(...)", "+ cancelAppointment(id)", "+ completeAppointment(id)",
                    "+ removeAppointment(id)", "+ getUpcomingAppointments(...)"});
        Box rg = box(null, "ReportGenerator", c2, ac.bottom() + 24, c2w, CONTROLLER, null,
                new String[] {"+ mostFrequentlyAttended()", "+ patientsWithPendingAppointments()",
                    "+ categoryDistribution()", "+ dailySummary(date)", "+ triageSummary()"});
        int c3 = 2060, c3w = 300;
        Box qm = box(null, "QueueManager", c3, 60, c3w, CONTROLLER,
                new String[] {"- waitingQueue : Queue<QueueEntry>"},
                new String[] {"+ enqueue(id)", "+ callNext() : QueueEntry", "+ countWaitingLongerThan(min)"});
        Box um = box(null, "UndoManager", c3, qm.bottom() + 24, c3w, CONTROLLER,
                new String[] {"- history : Stack<AdminOperation>"},
                new String[] {"+ record(op)", "+ undoLast() : AdminOperation"});
        Box ao = box(null, "AdminOperation", c3, um.bottom() + 30, c3w, CONTROLLER,
                new String[] {"- type : Type", "- description : String", "- undoAction : UndoAction"},
                new String[] {"+ undo()"});
        Box rc = box(null, "RecentPatientCache", c3, ao.bottom() + 24, c3w, CONTROLLER,
                new String[] {"- recentPatients : PatientRecord[10]", "- accessCounts : int[10]"},
                new String[] {"+ recordAccess(p)", "+ remove(id)"});
        box("utility", "PatientProcessor", c3, rc.bottom() + 24, c3w, CONTROLLER, null,
                new String[] {"+ processPatient(p : PatientRecord)", "+ processAll(list)"});

        // ClinicManager composition bus
        int busX = 1680;
        int cmY = cm.y + 40;
        Box[] col2 = {se, so, ac, rg};
        Box[] col3 = {qm, um, rc};
        int busBottom = rg.midY();
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            g.drawLine(cm.right(), cmY, busX, cmY);
            g.drawLine(busX, cmY, busX, busBottom);
            for (Box t : col2) {
                g.drawLine(busX, Math.max(t.midY(), cmY), t.x, Math.max(t.midY(), cmY));
            }
            for (int i = 0; i < col3.length; i++) {
                Box t = col3[i];
                int gapY = (col2[i].bottom() + col2[i + 1].y) / 2;
                g.drawLine(busX, gapY, c3 - 15, gapY);
                g.drawLine(c3 - 15, gapY, c3 - 15, t.midY());
                g.drawLine(c3 - 15, t.midY(), t.x, t.midY());
            }
        });
        heads.add(() -> diamond(cm.right(), cmY, busX, cmY, true));
        // UndoManager <>-- AdminOperation
        composition(um.cx(), um.bottom(), um.cx(), ao.y, "1", "0..50", "stack of");
        // ClinicManager <>-- ClinicDatabase (routed around the model package)
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            int[] xs = {cm.x + 30, cm.x + 30, 28, 28, db.x};
            int[] ys = {cm.bottom(), 755, 755, db.midY(), db.midY()};
            g.drawPolyline(xs, ys, xs.length);
        });
        heads.add(() -> diamond(cm.x + 30, cm.bottom(), cm.x + 30, 755, true));

        // ---------------------------------------------------------------- GUI
        Box mw = box("JFrame", "MainWindow", 40, 820, 340, GUI,
                new String[] {"- manager : ClinicManager", "- tabs : JTabbedPane",
                    "- clockTimer, dashboardTimer : Timer", "- reminderTimer, waitingTimer : Timer"},
                new String[] {"+ setStatus(msg)", "~ undo()", "- checkAppointmentReminders()",
                    "- checkWaitingPatients()", "- buildMenuBar() : JMenuBar"});
        Box panels = box("JPanel, implements Refreshable", "Tab Panels", 430, 820, 330, GUI,
                new String[] {"DashboardPanel", "PatientPanel", "AppointmentPanel", "QueuePanel",
                    "SearchPanel", "AdminPanel", "ReportPanel"},
                new String[] {"+ refresh()"});
        Box dlg = box("JDialog", "PatientDialog", 810, 820, 380, GUI,
                new String[] {"- typeCombo : JComboBox", "- extraPanel : JPanel (dynamic fields)"},
                new String[] {"+ showAddDialog(...)", "+ showEditDialog(...)", "- rebuildExtraFields(category)",
                    "- validateIdLive()"});
        Box rend = box("DefaultTableCellRenderer", "Cell Renderers", 810, dlg.bottom() + 24, 380, GUI,
                new String[] {"CategoryCellRenderer  (Emergency / General / Follow-Up colours)",
                    "StatusCellRenderer  (Pending / Completed / Cancelled)"}, null);
        composition(mw.right(), mw.y + 70, panels.x, mw.y + 70, "1", "7", "tabs");
        association(panels.right(), dlg.y + 60, dlg.x, dlg.y + 60, "opens");
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            int sy = panels.bottom() - 20;
            int[] xs = {panels.right(), 785, 785, rend.x};
            int[] ys = {sy, sy, rend.midY(), rend.midY()};
            g.drawPolyline(xs, ys, xs.length);
            label("uses", 745, sy - 5);
        });
        heads.add(() -> openArrow(rend.x, rend.midY(), 785, rend.midY()));
        // MainWindow --> ClinicManager
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            int[] xs = {mw.cx(), mw.cx(), 1500, 1500};
            int[] ys = {mw.y, 768, 768, cm.bottom()};
            g.drawPolyline(xs, ys, xs.length);
            label("calls", 1508, 700);
        });
        heads.add(() -> openArrow(1500, cm.bottom(), 1500, 768));

        // ---------------------------------------------------------------- util
        int uy = 1240;
        Box idg = box(null, "IDGenerator", 40, uy, 260, UTIL, null,
                new String[] {"+ nextPatientId(db) : String", "+ peekAppointmentId()", "+ synchronise(db)"});
        Box fh = box(null, "FileHandler", idg.right() + 20, uy, 300, UTIL, null,
                new String[] {"+ save(file, patients, queue)", "+ load(file) : LoadedData", "+ exportPatientsCsv(file, list)"});
        Box va = box(null, "Validator", fh.right() + 20, uy, 290, UTIL, null,
                new String[] {"+ requireName(s) : String", "+ parseAge(s) : int", "+ requirePhone(s)",
                    "+ parseAppointmentDate(s)"});
        box(null, "SampleData", va.right() + 20, uy, 230, UTIL, null,
                new String[] {"+ createPatients() : List", "+ createQueue() : List"});

        // ----------------------------------------------------------- exceptions
        String[] kids = {"InvalidPatientException", "DuplicatePatientException", "InvalidAppointmentException",
            "FileLoadException", "InvalidSearchException", "PatientNotFoundException"};
        List<Box> kb = new ArrayList<>();
        int ky = 830;
        for (String k : kids) {
            Box b = box(null, k, 1640, ky, 270, EXC, null, null);
            kb.add(b);
            ky = b.bottom() + 14;
        }
        int midKids = (kb.get(0).midY() + kb.get(kb.size() - 1).midY()) / 2;
        Box ce = box("checked", "ClinicException", 1260, 0, 280, EXC,
                new String[] {"extends java.lang.Exception"}, null);
        ce.y = midKids - ce.h / 2;
        Box age = box(null, "InvalidAgeException", 2010, kb.get(0).y, 260, EXC, null, null);
        int ebus = 1595;
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            for (Box b : kb) {
                g.drawLine(b.x, b.midY(), ebus, b.midY());
            }
            g.drawLine(ebus, kb.get(0).midY(), ebus, kb.get(kb.size() - 1).midY());
            g.drawLine(ebus, ce.midY(), ce.right(), ce.midY());
            g.drawLine(age.x, age.midY(), kb.get(0).right(), age.midY());
        });
        heads.add(() -> hollowTriangle(ce.right(), ce.midY(), ebus, ce.midY()));
        heads.add(() -> hollowTriangle(kb.get(0).right(), age.midY(), age.x, age.midY()));

        legend(1260, 1240);

        // ---------------------------------------------------------------- render
        for (Runnable r : lines) {
            r.run();
        }
        for (Box b : boxes) {
            draw(b);
        }
        for (Runnable r : heads) {
            r.run();
        }
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Diagram written to " + out.getAbsolutePath());
    }

    // ===================================================================== boxes

    static Box box(String stereo, String name, int x, int y, int w, Color fill, String[] fields, String[] methods) {
        Box b = new Box();
        b.stereo = stereo;
        b.name = name;
        b.x = x;
        b.y = y;
        b.w = w;
        b.fill = fill;
        b.fields = fields;
        b.methods = methods;
        int h = 10 + (stereo != null ? LINE - 2 : 0) + 20;
        if (fields != null) {
            h += 6 + fields.length * LINE + 4;
        }
        if (methods != null) {
            h += 6 + methods.length * LINE + 4;
        }
        b.h = h;
        // warn if text would overflow
        FontMetrics fm = g.getFontMetrics(textFont);
        for (String[] arr : new String[][] {fields, methods}) {
            if (arr != null) {
                for (String s : arr) {
                    if (fm.stringWidth(s.replace(" *", "")) > w - 14) {
                        System.out.println("WARNING overflow in " + name + ": " + s);
                    }
                }
            }
        }
        if (g.getFontMetrics(nameFont).stringWidth(name) > w - 10) {
            System.out.println("WARNING name overflow: " + name);
        }
        boxes.add(b);
        return b;
    }

    static void draw(Box b) {
        g.setColor(new Color(0, 0, 0, 30));
        g.fillRect(b.x + 4, b.y + 4, b.w, b.h);              // shadow
        g.setColor(b.fill);
        g.fillRect(b.x, b.y, b.w, b.h);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.3f));
        g.drawRect(b.x, b.y, b.w, b.h);
        int ty = b.y + 6;
        if (b.stereo != null) {
            g.setFont(stereoFont);
            centered("«" + b.stereo + "»", b, ty + 11);
            ty += LINE - 2;
        }
        g.setFont(b.italicName ? nameFont.deriveFont(Font.BOLD | Font.ITALIC) : nameFont);
        centered(b.name, b, ty + 15);
        ty += 22;
        ty = compartment(b, b.fields, ty);
        compartment(b, b.methods, ty);
    }

    static int compartment(Box b, String[] items, int ty) {
        if (items == null) {
            return ty;
        }
        g.setColor(BORDER);
        g.drawLine(b.x, ty, b.right(), ty);
        ty += 6;
        for (String s : items) {
            boolean abs = s.endsWith(" *");
            g.setFont(abs ? italicFont : textFont);
            g.setColor(Color.BLACK);
            g.drawString(abs ? s.substring(0, s.length() - 2) : s, b.x + 8, ty + LINE - 4);
            ty += LINE;
        }
        return ty + 4;
    }

    static void centered(String s, Box b, int baseline) {
        g.setColor(Color.BLACK);
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, b.x + (b.w - sw) / 2, baseline);
    }

    static void frame(String title, int x, int y, int w, int h) {
        g.setColor(new Color(250, 250, 252));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(150, 160, 180));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setFont(frameFont);
        int tw = g.getFontMetrics().stringWidth(title) + 18;
        g.setColor(new Color(220, 228, 240));
        g.fillRect(x + 1, y + 1, tw, 22);
        g.setColor(new Color(150, 160, 180));
        g.drawLine(x + 1, y + 23, x + tw, y + 23);
        g.drawLine(x + tw, y + 1, x + tw, y + 23);
        g.setColor(new Color(40, 50, 80));
        g.drawString(title, x + 9, y + 17);
    }

    // ================================================================ relations

    /** Generalisation drawn as a tree: children up to a bus, bus up to the parent. */
    static void inheritanceTree(Box parent, int busY, Box... kids) {
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            int min = parent.cx();
            int max = parent.cx();
            for (Box k : kids) {
                g.drawLine(k.cx(), k.y, k.cx(), busY);
                min = Math.min(min, k.cx());
                max = Math.max(max, k.cx());
            }
            g.drawLine(min, busY, max, busY);
            g.drawLine(parent.cx(), busY, parent.cx(), parent.bottom());
        });
        heads.add(() -> hollowTriangle(parent.cx(), parent.bottom(), parent.cx(), busY));
        lines.add(() -> label("extends", parent.cx() + 6, busY - 8));
    }

    /** Interface realisation: dashed lines with a hollow triangle. */
    static void realization(Box iface, int busY, Box... kids) {
        lines.add(() -> {
            g.setStroke(DASHED);
            g.setColor(new Color(90, 60, 150));
            int min = iface.cx();
            int max = iface.cx();
            for (Box k : kids) {
                int kx = k.x + k.w * 4 / 5;
                g.drawLine(kx, k.y, kx, busY);
                min = Math.min(min, kx);
                max = Math.max(max, kx);
            }
            g.drawLine(min, busY, max, busY);
            g.drawLine(iface.cx(), busY, iface.cx(), iface.bottom());
            g.setStroke(SOLID);
            label("implements", iface.cx() + 6, busY - 6);
        });
        heads.add(() -> hollowTriangle(iface.cx(), iface.bottom(), iface.cx(), busY));
    }

    static void composition(int x1, int y1, int x2, int y2, String m1, String m2, String text) {
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            g.drawLine(x1, y1, x2, y2);
            if (y1 == y2) {
                label(m1, x1 + 22, y1 - 5);
                label(m2, x2 - 30, y2 - 5);
                label(text, (x1 + x2) / 2 - 12, y1 + 15);
            } else {
                label(m1, x1 + 6, y1 + 30);
                label(m2 + "  " + text, x2 + 6, y2 - 6);
            }
        });
        heads.add(() -> diamond(x1, y1, x2, y2, true));
    }

    static void association(int x1, int y1, int x2, int y2, String text) {
        lines.add(() -> {
            g.setStroke(SOLID);
            g.setColor(BORDER);
            g.drawLine(x1, y1, x2, y2);
            label(text, (x1 + x2) / 2 - 12, y1 - 5);
        });
        heads.add(() -> openArrow(x2, y2, x1, y1));
    }

    static void label(String s, int x, int y) {
        g.setFont(stereoFont);
        g.setColor(new Color(70, 70, 70));
        g.drawString(s, x, y);
    }

    static void hollowTriangle(int tipX, int tipY, int fromX, int fromY) {
        double dx = tipX - fromX;
        double dy = tipY - fromY;
        double len = Math.hypot(dx, dy);
        dx /= len;
        dy /= len;
        double bx = tipX - dx * 15;
        double by = tipY - dy * 15;
        Polygon p = new Polygon();
        p.addPoint(tipX, tipY);
        p.addPoint((int) Math.round(bx - dy * 9), (int) Math.round(by + dx * 9));
        p.addPoint((int) Math.round(bx + dy * 9), (int) Math.round(by - dx * 9));
        g.setColor(Color.WHITE);
        g.fillPolygon(p);
        g.setColor(BORDER);
        g.setStroke(SOLID);
        g.drawPolygon(p);
    }

    static void diamond(int tipX, int tipY, int towardX, int towardY, boolean filled) {
        double dx = towardX - tipX;
        double dy = towardY - tipY;
        double len = Math.hypot(dx, dy);
        dx /= len;
        dy /= len;
        double mx = tipX + dx * 10;
        double my = tipY + dy * 10;
        Polygon p = new Polygon();
        p.addPoint(tipX, tipY);
        p.addPoint((int) Math.round(mx - dy * 6), (int) Math.round(my + dx * 6));
        p.addPoint((int) Math.round(tipX + dx * 20), (int) Math.round(tipY + dy * 20));
        p.addPoint((int) Math.round(mx + dy * 6), (int) Math.round(my - dx * 6));
        g.setColor(filled ? BORDER : Color.WHITE);
        g.fillPolygon(p);
        g.setColor(BORDER);
        g.setStroke(SOLID);
        g.drawPolygon(p);
    }

    static void openArrow(int tipX, int tipY, int fromX, int fromY) {
        double dx = tipX - fromX;
        double dy = tipY - fromY;
        double len = Math.hypot(dx, dy);
        dx /= len;
        dy /= len;
        g.setColor(BORDER);
        g.setStroke(SOLID);
        g.drawLine(tipX, tipY, (int) (tipX - dx * 12 - dy * 7), (int) (tipY - dy * 12 + dx * 7));
        g.drawLine(tipX, tipY, (int) (tipX - dx * 12 + dy * 7), (int) (tipY - dy * 12 - dx * 7));
    }

    static void legend(int x, int y) {
        lines.add(() -> {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, 1090, 160);
            g.setColor(new Color(150, 160, 180));
            g.drawRect(x, y, 1090, 160);
            g.setFont(nameFont);
            g.setColor(Color.BLACK);
            g.drawString("Legend", x + 12, y + 22);
            g.setFont(textFont);
            int ly = y + 50;
            g.setStroke(SOLID);
            g.setColor(BORDER);
            g.drawLine(x + 20, ly, x + 90, ly);
            g.setColor(Color.BLACK);
            g.drawString("Inheritance (extends) - hollow triangle points to the superclass", x + 110, ly + 4);
            ly += 28;
            g.setStroke(DASHED);
            g.setColor(new Color(90, 60, 150));
            g.drawLine(x + 20, ly, x + 90, ly);
            g.setStroke(SOLID);
            g.setColor(Color.BLACK);
            g.drawString("Realisation (implements Appointable interface)", x + 110, ly + 4);
            ly += 28;
            g.setColor(BORDER);
            g.drawLine(x + 20, ly, x + 90, ly);
            g.setColor(Color.BLACK);
            g.drawString("Composition (filled diamond on the owner) / association (open arrow)", x + 110, ly + 4);
            ly += 28;
            g.setFont(italicFont);
            g.drawString("Italic = abstract class / abstract (pure virtual) method.", x + 20, ly + 4);
            g.drawString("Polymorphism: PatientProcessor, AppointmentController and the GUI call overridden methods "
                    + "through PatientRecord / Appointable references.", x + 20, ly + 22);
        });
        heads.add(() -> {
            int ly = y + 50;
            hollowTriangle(x + 90, ly, x + 20, ly);
            hollowTriangle(x + 90, ly + 28, x + 20, ly + 28);
            diamond(x + 20, ly + 56, x + 90, ly + 56, true);
            openArrow(x + 90, ly + 56, x + 20, ly + 56);
        });
    }
}
