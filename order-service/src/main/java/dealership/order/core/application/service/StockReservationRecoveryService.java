package dealership.order.core.application.service;

import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StockReservationRecoveryService {
    private final StockCarReservationGateway reservationGateway;
    private final StockOrderTransactions transactions;

    public StockReservationRecoveryService(StockCarReservationGateway reservationGateway,
                                           StockOrderTransactions transactions) {
        this.reservationGateway = reservationGateway;
        this.transactions = transactions;
    }

    public StockOrder execute(StockOrderTransactions.Work work) {
        return switch (work.action()) {
            case NONE -> work.order();
            case CONFIRM -> confirm(work.order());
            case RELEASE -> release(work.order());
        };
    }

    public void processPending(String orderId) {
        execute(transactions.claimPendingWork(orderId));
    }

    public void processExpiredHold(String orderId) {
        execute(transactions.expireLocalHold(orderId));
    }

    private StockOrder confirm(StockOrder order) {
        try {
            reservationGateway.confirm(order.getCarId(), order.getId());
        } catch (CarReservationConflictException | EntityNotFoundException exception) {
            transactions.failConfirmationTerminal(order.getId());
            throw exception;
        }
        return transactions.completeConfirmation(order.getId());
    }

    private StockOrder release(StockOrder order) {
        reservationGateway.release(order.getCarId(), order.getId());
        return transactions.completeRelease(order.getId());
    }
}
