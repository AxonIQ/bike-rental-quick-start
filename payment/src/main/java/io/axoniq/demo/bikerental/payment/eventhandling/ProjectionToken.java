package io.axoniq.demo.bikerental.payment.eventhandling;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Persists how far the payment projection has consumed the Axon Server event stream - the minimal
 * stand-in for Axon Framework's token store.
 */
@Entity
public class ProjectionToken {

    @Id
    private String processorName;
    private long position;

    protected ProjectionToken() {
    }

    public ProjectionToken(String processorName, long position) {
        this.processorName = processorName;
        this.position = position;
    }

    public String getProcessorName() {
        return processorName;
    }

    public long getPosition() {
        return position;
    }
}
