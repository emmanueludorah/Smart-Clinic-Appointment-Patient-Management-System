package scapms.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** One patient waiting in the consultation queue. */
public final class QueueEntry {
    private final String patientId;
    private final LocalDateTime joinedAt;

    public QueueEntry(String patientId, LocalDateTime joinedAt) {
        this.patientId = patientId;
        this.joinedAt = joinedAt;
    }

    public String getPatientId() {
        return patientId;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public long getWaitingMinutes(LocalDateTime now) {
        return Math.max(0, Duration.between(joinedAt, now).toMinutes());
    }
}
