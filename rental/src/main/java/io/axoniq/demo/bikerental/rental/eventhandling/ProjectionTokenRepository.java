package io.axoniq.demo.bikerental.rental.eventhandling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectionTokenRepository extends JpaRepository<ProjectionToken, String> {

}
