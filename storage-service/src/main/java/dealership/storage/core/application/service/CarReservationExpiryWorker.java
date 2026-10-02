package dealership.storage.core.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CarReservationExpiryWorker {
    private static final Logger log = LoggerFactory.getLogger(CarReservationExpiryWorker.class);

    private final CarReservationService reservationService;
    private final int batchSize;

    public CarReservationExpiryWorker(CarReservationService reservationService,
                                      @Value("${reservation.recovery.batch-size:100}") int batchSize) {
        this.reservationService = reservationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${reservation.recovery.interval:PT10S}")
    public void expireReservations() {
        for (UUID orderId : reservationService.findExpiredCandidates(batchSize)) {
            try {
                reservationService.expireIfDue(orderId);
            } catch (RuntimeException exception) {
                log.error("Failed to expire reservation for order {}", orderId, exception);
            }
        }
    }
}
