package scapms.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import scapms.exception.ClinicException;
import scapms.model.QueueEntry;

/**
 * Manages patients waiting for consultation using a FIFO {@link Queue}:
 * the patient who joined first is served first.
 */
public class QueueManager {

    private final Queue<QueueEntry> waitingQueue = new LinkedList<>();

    public void enqueue(String patientId) throws ClinicException {
        if (contains(patientId)) {
            throw new ClinicException("Patient " + patientId + " is already in the waiting queue (position "
                    + (positionOf(patientId) + 1) + ").");
        }
        waitingQueue.offer(new QueueEntry(patientId, LocalDateTime.now().withNano(0)));
    }

    /** Removes and returns the patient at the front of the queue. */
    public QueueEntry callNext() throws ClinicException {
        if (waitingQueue.isEmpty()) {
            throw new ClinicException("The waiting queue is empty - there is no patient to call.");
        }
        return waitingQueue.poll();
    }

    public QueueEntry peek() {
        return waitingQueue.peek();
    }

    public int size() {
        return waitingQueue.size();
    }

    public boolean isEmpty() {
        return waitingQueue.isEmpty();
    }

    public boolean contains(String patientId) {
        return positionOf(patientId) >= 0;
    }

    /** @return zero-based position in the queue, or -1. */
    public int positionOf(String patientId) {
        int i = 0;
        for (QueueEntry e : waitingQueue) {
            if (e.getPatientId().equals(patientId)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** Removes a patient (e.g. when their record is deleted). @return the removed entry or null. */
    public QueueEntry remove(String patientId) {
        for (QueueEntry e : waitingQueue) {
            if (e.getPatientId().equals(patientId)) {
                waitingQueue.remove(e);
                return e;
            }
        }
        return null;
    }

    /** Puts an entry back at a given position (used when a patient deletion is undone). */
    public void insertAt(int position, QueueEntry entry) {
        List<QueueEntry> items = new ArrayList<>(waitingQueue);
        items.add(Math.max(0, Math.min(position, items.size())), entry);
        waitingQueue.clear();
        waitingQueue.addAll(items);
    }

    /** @return a copy of the queue in service order (front first). */
    public List<QueueEntry> snapshot() {
        return new ArrayList<>(waitingQueue);
    }

    public void restore(List<QueueEntry> entries) {
        waitingQueue.clear();
        waitingQueue.addAll(entries);
    }

    public long getLongestWaitMinutes() {
        QueueEntry front = waitingQueue.peek();     // the front has waited longest
        return front == null ? 0 : front.getWaitingMinutes(LocalDateTime.now());
    }

    public int countWaitingLongerThan(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (QueueEntry e : waitingQueue) {
            if (e.getWaitingMinutes(now) >= minutes) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        waitingQueue.clear();
    }
}
