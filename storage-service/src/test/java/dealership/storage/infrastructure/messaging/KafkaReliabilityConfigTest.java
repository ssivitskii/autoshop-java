package dealership.storage.infrastructure.messaging;

import dealership.common.event.PermanentMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaReliabilityConfigTest {

    @Test
    void failedDltAcknowledgementIsPropagated() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("DLT unavailable")));
        var recoverer = new KafkaReliabilityConfig().deadLetterPublishingRecoverer(kafkaTemplate);
        recoverer.setVerifyPartition(false);
        ConsumerRecord<String, String> source = new ConsumerRecord<>(
                "order.events", 0, 5, "order", "malformed");

        assertThrows(RuntimeException.class,
                () -> recoverer.accept(source, null, new PermanentMessageException("invalid")));
    }
}
