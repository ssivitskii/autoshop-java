package dealership.storage.infrastructure.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StorageInboxStore {
    private final JdbcTemplate jdbcTemplate;

    public StorageInboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(UUID eventId, String topic, int partition, long offset) {
        return jdbcTemplate.update("""
                INSERT INTO inbox_events(event_id, topic, partition_id, source_offset)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, topic, partition, offset) == 1;
    }
}
