package dealership.storage.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.common.event.PermanentMessageException;
import dealership.storage.core.domain.enums.AssemblyStatus;
import dealership.storage.infrastructure.persistence.entity.AssemblyOrderJpaEntity;
import dealership.storage.infrastructure.persistence.repository.AssemblyOrderJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class StorageOrderMessageTransactions {
    private final StorageInboxStore inboxStore;
    private final AssemblyOrderJpaRepository assemblyOrders;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StorageOrderMessageTransactions(StorageInboxStore inboxStore,
                                           AssemblyOrderJpaRepository assemblyOrders,
                                           JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper) {
        this.inboxStore = inboxStore;
        this.assemblyOrders = assemblyOrders;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(UUID eventId, String topic, int partition, long offset,
                        OrderSentForApprovalEvent event) {
        if (!inboxStore.insert(eventId, topic, partition, offset)) {
            return;
        }

        AssemblyOrderJpaEntity assembly = assemblyOrders.findBySourceOrderIdForUpdate(event.getOrderId())
                .map(existing -> reconcileExisting(existing, event))
                .orElseGet(() -> createAssembly(event));
        ensureResponse(eventId, event, assembly);
    }

    private AssemblyOrderJpaEntity reconcileExisting(AssemblyOrderJpaEntity assembly,
                                                      OrderSentForApprovalEvent event) {
        if (assembly.isRemoved()) {
            throw new PermanentMessageException("Assembly order was removed: " + event.getOrderId());
        }
        if (!assembly.getOrderType().equals(event.getOrderType())) {
            throw new PermanentMessageException("Assembly order type conflicts with the event");
        }
        if (assembly.getStatus() == AssemblyStatus.CREATED) {
            assembly.setStatus(AssemblyStatus.ASSEMBLED);
            return assemblyOrders.save(assembly);
        }
        return assembly;
    }

    private AssemblyOrderJpaEntity createAssembly(OrderSentForApprovalEvent event) {
        AssemblyOrderJpaEntity assembly = new AssemblyOrderJpaEntity();
        assembly.setId(UUID.randomUUID());
        assembly.setSourceOrderId(event.getOrderId());
        assembly.setOrderType(event.getOrderType());
        assembly.setTraceId(event.getTraceId());
        assembly.setStatus(AssemblyStatus.ASSEMBLED);
        return assemblyOrders.save(assembly);
    }

    private void ensureResponse(UUID eventId, OrderSentForApprovalEvent event, AssemblyOrderJpaEntity assembly) {
        String traceId = event.getTraceId() == null || event.getTraceId().isBlank()
                ? eventId.toString() : event.getTraceId();
        try {
            String responseType;
            String payload;
            if (assembly.getStatus() == AssemblyStatus.ASSEMBLED) {
                responseType = "OrderApproved";
                payload = objectMapper.writeValueAsString(new OrderApprovedEvent(
                        event.getOrderId(), event.getOrderType(), traceId, assembly.getId().toString()));
            } else if (assembly.getStatus() == AssemblyStatus.FAIL) {
                responseType = "OrderRejected";
                payload = objectMapper.writeValueAsString(new OrderRejectedEvent(
                        event.getOrderId(), event.getOrderType(), traceId, "Сборка заказа отклонена"));
            } else {
                throw new PermanentMessageException("Assembly order has unsupported status: " + assembly.getStatus());
            }
            jdbcTemplate.update("""
                    INSERT INTO outbox_events(
                        id, aggregate_type, aggregate_id, event_type, payload, trace_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (aggregate_id) DO NOTHING
                    """, UUID.randomUUID(), event.getOrderType(), event.getOrderId(), responseType,
                    payload, traceId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize storage response", exception);
        }
    }
}
