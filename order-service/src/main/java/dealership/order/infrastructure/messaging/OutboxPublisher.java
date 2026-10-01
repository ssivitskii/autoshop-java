package dealership.order.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "order.events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRateString = "${outbox.scheduler.rate:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findBySentFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload());
                event.setSent(true);
                outboxRepository.save(event);
                log.info("Published outbox event: type={}, aggregateId={}, traceId={}",
                        event.getEventType(), event.getAggregateId(), event.getTraceId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
            }
        }
    }
}
