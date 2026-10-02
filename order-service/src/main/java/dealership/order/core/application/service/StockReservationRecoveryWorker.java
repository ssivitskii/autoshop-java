package dealership.order.core.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StockReservationRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(StockReservationRecoveryWorker.class);

    private final StockOrderTransactions transactions;
    private final StockReservationRecoveryService recoveryService;
    private final int batchSize;

    public StockReservationRecoveryWorker(StockOrderTransactions transactions,
                                          StockReservationRecoveryService recoveryService,
                                          @Value("${reservation.recovery.batch-size:100}") int batchSize) {
        this.transactions = transactions;
        this.recoveryService = recoveryService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${reservation.recovery.interval:PT10S}")
    public void recoverReservations() {
        for (UUID orderId : transactions.findExpiredHoldCandidates(batchSize)) {
            try {
                recoveryService.processExpiredHold(orderId.toString());
            } catch (RuntimeException exception) {
                log.error("Failed to recover expired hold for order {}", orderId, exception);
            }
        }
        for (UUID orderId : transactions.findPendingCandidates(batchSize)) {
            try {
                recoveryService.processPending(orderId.toString());
            } catch (RuntimeException exception) {
                log.error("Failed to recover pending reservation work for order {}", orderId, exception);
            }
        }
    }
}
