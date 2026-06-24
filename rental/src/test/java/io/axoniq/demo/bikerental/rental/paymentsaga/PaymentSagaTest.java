package io.axoniq.demo.bikerental.rental.paymentsaga;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.rental.support.CommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the in-memory {@link PaymentSaga}, driven by feeding it events and verifying the
 * commands it dispatches through a mocked {@link CommandDispatcher}.
 */
class PaymentSagaTest {

    private static final String BIKE_ID = "bikeId";
    private static final String RENTER = "rider";
    private static final String REFERENCE = "rentalReference";

    private CommandDispatcher commandDispatcher;
    private PaymentSaga saga;

    @BeforeEach
    void setUp() {
        commandDispatcher = mock(CommandDispatcher.class);
        when(commandDispatcher.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        saga = new PaymentSaga(commandDispatcher);
    }

    @Test
    void bikeRequestPreparesPayment() {
        saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));

        verify(commandDispatcher).send(new PreparePaymentCommand(10, REFERENCE));
    }

    @Test
    void confirmedPaymentApprovesRequest() {
        saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));

        saga.on(new PaymentConfirmedEvent("paymentId", REFERENCE));

        verify(commandDispatcher).send(new ApproveRequestCommand(BIKE_ID, RENTER));
    }

    @Test
    void rejectedPaymentRejectsRequest() {
        saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));

        saga.on(new PaymentRejectedEvent("paymentId", REFERENCE));

        verify(commandDispatcher).send(new RejectRequestCommand(BIKE_ID, RENTER));
    }
}
