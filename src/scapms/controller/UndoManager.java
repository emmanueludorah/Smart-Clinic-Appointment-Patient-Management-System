package scapms.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import scapms.exception.ClinicException;

/**
 * Implements "Undo Last Administrative Operation" with a {@link Stack} (LIFO):
 * the most recent operation is always the first one reversed.
 */
public class UndoManager {

    public static final int MAX_HISTORY = 50;

    private final Stack<AdminOperation> history = new Stack<>();

    public void record(AdminOperation operation) {
        history.push(operation);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);              // discard the oldest (bottom of the stack)
        }
    }

    /**
     * Pops and reverses the most recent operation.
     * @return the operation that was undone
     */
    public AdminOperation undoLast() throws ClinicException {
        if (history.isEmpty()) {
            throw new ClinicException("There is no administrative operation to undo.");
        }
        AdminOperation operation = history.pop();
        try {
            operation.undo();
        } catch (ClinicException e) {
            throw new ClinicException("Could not undo '" + operation.getDescription() + "': " + e.getMessage(), e);
        }
        return operation;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public AdminOperation peek() {
        return history.isEmpty() ? null : history.peek();
    }

    public int size() {
        return history.size();
    }

    /** @return the operations, most recent (top of stack) first. */
    public List<AdminOperation> getHistory() {
        List<AdminOperation> list = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            list.add(history.get(i));
        }
        return list;
    }

    public void clear() {
        history.clear();
    }
}
