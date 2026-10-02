package dealership.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.order.infrastructure.messaging.OutboxClaimStore;
import dealership.order.infrastructure.messaging.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void marksSentOnlyAfterBrokerAcknowledgementAndKeepsStableEventId() throws Exception {
        OutboxClaimStore store = mock(OutboxClaimStore.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        UUID eventId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        var claim = new OutboxClaimStore.ClaimedEvent(eventId, orderId, "OrderSentForApproval",
                "{\"orderId\":\"" + orderId + "\"}", "trace", 1, token);
        when(store.claim(1, Duration.ofSeconds(30))).thenReturn(List.of(claim), List.of());
        when(kafka.send(eq("order.events"), eq(orderId), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxPublisher publisher = new OutboxPublisher(
                store, kafka, mapper, 10, Duration.ofSeconds(30), Duration.ofSeconds(1));

        publisher.publishPendingEvents();

        verify(store).markSent(eventId, token);
        verify(store, never()).markFailed(any(), any(), anyInt());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("order.events"), eq(orderId), message.capture());
        assertEquals(eventId.toString(), mapper.readTree(message.getValue()).get("eventId").asText());
    }

    @Test
    void failedAcknowledgementLeavesEventPendingWithBackoff() {
        OutboxClaimStore store = mock(OutboxClaimStore.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        UUID eventId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        var claim = new OutboxClaimStore.ClaimedEvent(eventId, orderId, "OrderSentForApproval",
                "{\"orderId\":\"" + orderId + "\"}", "trace", 3, token);
        when(store.claim(1, Duration.ofSeconds(30))).thenReturn(List.of(claim), List.of());
        when(kafka.send(eq("order.events"), eq(orderId), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        OutboxPublisher publisher = new OutboxPublisher(
                store, kafka, new ObjectMapper(), 10, Duration.ofSeconds(30), Duration.ofSeconds(1));

        publisher.publishPendingEvents();

        verify(store).markFailed(eventId, token, 3);
        verify(store, never()).markSent(any(), any());
    }
}
