package io.axoniq.demo.bikerental.coreapi.rental;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

//tag::BikeStatusEntity[]
@Entity //<.>
//end::BikeStatusEntity[]
//tag::BikeStatusClass[]
public class BikeStatus {

    //tag::Fields[]
    //tag::PersistenceIdAnnotation[]
    @Id //<.>
    //end::PersistenceIdAnnotation[]
    private String bikeId;
    private String bikeType;
    private String location;
    private String renter;
    private RentalStatus status;

    public BikeStatus() {
    }

    //end::Fields[]
    //tag::Constructor[]
    public BikeStatus(String bikeId, String bikeType, String location) {
        this.bikeId = bikeId;
        this.bikeType = bikeType;
        this.location = location;
        this.status = RentalStatus.AVAILABLE;
    }

    //end::Constructor[]
    //tag::Methods[]
    //tag::Accessors[]
    // Accessor methods
    public String getBikeId() {
        return bikeId;
    }

    public String getBikeType() {
        return bikeType;
    }

    public String getLocation() {
        return location;
    }

    public String getRenter() {
        return renter;
    }

    public RentalStatus getStatus() {
        return status;
    }

    public String description() {
        switch (status) {
            case RENTED:
                return String.format("Bike %s was rented by %s in %s", bikeId, renter, location);
            case AVAILABLE:
                return String.format("Bike %s is available for rental in %s.", bikeId, location);
            case REQUESTED:
                return String.format("Bike %s is requested by %s in %s", bikeId, renter, location);
            default:
                return "Status unknown";
        }
    }

    //end::Accessors[]
    //tag::Modifiers[]
    public void returnedAt(String location) {
        this.location = location;
        this.status = RentalStatus.AVAILABLE;
        this.renter = null;
    }


    public void requestedBy(String renter) {
        this.renter = renter;
        this.status = RentalStatus.REQUESTED;
    }

    public void rentedBy(String renter) {
        this.renter = renter;
        this.status = RentalStatus.RENTED;
    }
    //end:Modifiers[]
    //end::Methods[]
}
//end::BikeStatusClass[]