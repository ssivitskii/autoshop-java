package dealership.storage.infrastructure.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class StorageOutboxClaimStore {

    private final JdbcTemplate jdbcTemplate;

    public StorageOutboxClaimStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedEvent> claim(int limit, Duration leaseDuration) {
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT candidate.id
                      FROM outbox_events candidate
                     WHERE candidate.sent = FALSE
                       AND candidate.next_attempt_at <= clock_timestamp()
                       AND (candidate.claim_until IS NULL OR candidate.claim_until <= clock_timestamp())
                       AND NOT EXISTS (
                           SELECT 1
                             FROM outbox_events earlier
                            WHERE earlier.sent = FALSE
                              AND earlier.aggregate_id = candidate.aggregate_id
                              AND (earlier.created_at, earlier.id) < (candidate.created_at, candidate.id)
                       )
                     ORDER BY candidate.next_attempt_at, candidate.created_at, candidate.id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events event
                   SET claim_token = ?,
                       claim_until = clock_timestamp() + make_interval(secs => ?),
                       attempt_count = event.attempt_count + 1
                  FROM candidates
                 WHERE event.id = candidates.id
                RETURNING event.id, event.aggregate_id, event.event_type, event.payload,
                          event.trace_id, event.attempt_count, event.claim_token
                """, this::mapClaim, limit, claimToken, leaseDuration.toSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(UUID eventId, UUID claimToken) {
        return jdbcTemplate.update("""
                UPDATE outbox_events
                   SET sent = TRUE, sent_at = clock_timestamp(), claim_token = NULL, claim_until = NULL
                 WHERE id = ? AND sent = FALSE AND claim_token = ?
                """, eventId, claimToken) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(UUID eventId, UUID claimToken, int attemptCount) {
        long backoffSeconds = Math.min(60, 1L << Math.min(Math.max(attemptCount - 1, 0), 6));
        return jdbcTemplate.update("""
                UPDATE outbox_events
                   SET claim_token = NULL,
                       claim_until = NULL,
                       next_attempt_at = clock_timestamp() + make_interval(secs => ?)
                 WHERE id = ? AND sent = FALSE AND claim_token = ?
                """, backoffSeconds, eventId, claimToken) == 1;
    }

    private ClaimedEvent mapClaim(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ClaimedEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("aggregate_id"),
                resultSet.getString("event_type"),
                resultSet.getString("payload"),
                resultSet.getString("trace_id"),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("claim_token", UUID.class));
    }

    public record ClaimedEvent(UUID id, String aggregateId, String eventType, String payload,
                               String traceId, int attemptCount, UUID claimToken) {
    }
}
