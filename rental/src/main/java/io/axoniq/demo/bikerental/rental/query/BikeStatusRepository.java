package io.axoniq.demo.bikerental.rental.query;

import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import io.axoniq.demo.bikerental.coreapi.rental.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

//tag::InterfaceDefinition[]
@Repository //<.>
public interface BikeStatusRepository
        extends JpaRepository<BikeStatus, String> { //<.>

    //tag::QueryMethods[]
    List<BikeStatus> findAllByBikeTypeAndStatus(String bikeType, RentalStatus status);
    long countBikeStatusesByBikeType(String bikeType);
    //end::QueryMethods[]

}
//end::InterfaceDefinition[]