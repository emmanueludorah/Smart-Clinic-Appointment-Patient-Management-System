package scapms.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import scapms.exception.ClinicException;

/**
 * A reversible administrative operation stored on the undo stack.
 * Each operation carries the code needed to reverse itself.
 */
public class AdminOperation {

    public enum Type {
        REGISTER_PATIENT("Register Patient"),
        MODIFY_PATIENT("Modify Patient"),
        DELETE_PATIENT("Delete Patient"),
        SCHEDULE_APPOINTMENT("Schedule Appointment"),
        CANCEL_APPOINTMENT("Cancel Appointment"),
        COMPLETE_APPOINTMENT("Complete Appointment"),
        REMOVE_APPOINTMENT("Remove Appointment");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The code that reverses the operation. */
    @FunctionalInterface
    public interface UndoAction {
        void undo() throws ClinicException;
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Type type;
    private final String description;
    private final LocalDateTime performedAt;
    private final UndoAction undoAction;

    public AdminOperation(Type type, String description, UndoAction undoAction) {
        this.type = type;
        this.description = description;
        this.undoAction = undoAction;
        this.performedAt = LocalDateTime.now();
    }

    public void undo() throws ClinicException {
        undoAction.undo();
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    @Override
    public String toString() {
        return "[" + performedAt.format(TIME) + "] " + type + " - " + description;
    }
}
