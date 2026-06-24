package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;
import io.axoniq.demo.bikerental.payment.support.CommandDispatcher;
import io.axoniq.demo.bikerental.payment.support.QueryDispatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@RestController
public class PaymentController {

    private static final String GET_STATUS = "getStatus";

    private final QueryDispatcher queryDispatcher;
    private final CommandDispatcher commandDispatcher;

    public PaymentController(QueryDispatcher queryDispatcher, CommandDispatcher commandDispatcher) {
        this.queryDispatcher = queryDispatcher;
        this.commandDispatcher = commandDispatcher;
    }

    @GetMapping("/status/{paymentId}")
    public CompletableFuture<PaymentStatus> getStatus(@PathVariable("paymentId") String paymentId) {
        return queryDispatcher.query(GET_STATUS, paymentId, PaymentStatus.class);
    }

    @GetMapping("/findPayment")
    public CompletableFuture<String> findPaymentId(@RequestParam("reference") String paymentReference) {
        return queryDispatcher.query(PaymentStatusNamedQueries.GET_PAYMENT_ID, paymentReference, String.class);
    }

    @PostMapping("/acceptPayment")
    public CompletableFuture<Void> confirmPayment(@RequestParam("id") String paymentId) {
        return commandDispatcher.send(new ConfirmPaymentCommand(paymentId));
    }

    @PostMapping("/rejectPayment")
    public CompletableFuture<Void> rejectPayment(@RequestParam("id") String paymentId) {
        return commandDispatcher.send(new RejectPaymentCommand(paymentId));
    }

    @GetMapping("/status")
    public Flux<PaymentStatus> getStatus(@RequestParam(value = "status", required = false) PaymentStatus.Status status) {
        return Mono.fromFuture(queryDispatcher.queryMany(PaymentStatusNamedQueries.GET_ALL_PAYMENTS,
                                                         status, PaymentStatus.class))
                   .flatMapMany(Flux::fromIterable);
    }
}
