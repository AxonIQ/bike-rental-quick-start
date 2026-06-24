package io.axoniq.demo.bikerental.rental.eventhandling;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Persists how far a projection has consumed the Axon Server event stream. This is the minimal
 * stand-in for Axon Framework's token store: a single row holding the last processed global token,
 * so the projection resumes where it left off after a restart instead of replaying from scratch.
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
