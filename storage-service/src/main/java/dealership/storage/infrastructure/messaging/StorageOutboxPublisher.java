package dealership.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class StorageOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(StorageOutboxPublisher.class);
    private static final String TOPIC = "order.responses";

    private final StorageOutboxClaimStore claimStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration claimDuration;
    private final Duration sendTimeout;

    public StorageOutboxPublisher(StorageOutboxClaimStore claimStore,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${outbox.publisher.batch-size:100}") int batchSize,
                                  @Value("${outbox.publisher.claim-duration:PT30S}") Duration claimDuration,
                                  @Value("${outbox.publisher.send-timeout:PT10S}") Duration sendTimeout) {
        this.claimStore = claimStore;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.claimDuration = claimDuration;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval:${outbox.scheduler.rate:5000}}")
    public void publishPendingEvents() {
        for (int published = 0; published < batchSize; published++) {
            var claimed = claimStore.claim(1, claimDuration);
            if (claimed.isEmpty()) {
                return;
            }
            publish(claimed.getFirst());
        }
    }

    private void publish(StorageOutboxClaimStore.ClaimedEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                    event.id(), event.eventType(), EventEnvelope.CURRENT_VERSION,
                    event.aggregateId(), event.traceId(), payload);
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, event.aggregateId(), message)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (claimStore.markSent(event.id(), event.claimToken())) {
                log.info("Published storage response: type={}, aggregateId={}, eventId={}",
                        event.eventType(), event.aggregateId(), event.id());
            } else {
                log.warn("Broker acknowledged response {}, but its database lease is no longer current", event.id());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            claimStore.markFailed(event.id(), event.claimToken(), event.attemptCount());
            log.warn("Interrupted while publishing storage response {}", event.id());
        } catch (Exception exception) {
            claimStore.markFailed(event.id(), event.claimToken(), event.attemptCount());
            log.warn("Failed to publish storage response {}; it remains pending", event.id(), exception);
        }
    }
}
