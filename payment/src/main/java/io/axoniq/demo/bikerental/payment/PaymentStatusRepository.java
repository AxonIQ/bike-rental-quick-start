package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentStatusRepository extends MongoRepository<PaymentStatus, String> {

    List<PaymentStatus> findAllByStatus(PaymentStatus.Status status);

    Optional<PaymentStatus> findByReferenceAndStatus(String reference, PaymentStatus.Status status);
}
