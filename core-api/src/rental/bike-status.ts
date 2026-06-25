import { RentalStatus } from './rental-status.js';

/**
 * The bike read-model entity. Formerly a JPA `@Entity`; here a plain object persisted by the
 * {@link BikeStatusRepository}. Its JSON form (the fields below) is what the read-model queries and
 * the live UI updates return.
 */
export class BikeStatus {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.BikeStatus';

  bikeId!: string;
  bikeType!: string;
  location!: string;
  renter: string | null = null;
  status!: RentalStatus;

  constructor(bikeId?: string, bikeType?: string, location?: string) {
    if (bikeId !== undefined) {
      this.bikeId = bikeId;
      this.bikeType = bikeType!;
      this.location = location!;
      this.status = RentalStatus.AVAILABLE;
    }
  }

  getBikeId(): string {
    return this.bikeId;
  }

  getBikeType(): string {
    return this.bikeType;
  }

  getLocation(): string {
    return this.location;
  }

  getRenter(): string | null {
    return this.renter;
  }

  getStatus(): RentalStatus {
    return this.status;
  }

  description(): string {
    switch (this.status) {
      case RentalStatus.RENTED:
        return `Bike ${this.bikeId} was rented by ${this.renter} in ${this.location}`;
      case RentalStatus.AVAILABLE:
        return `Bike ${this.bikeId} is available for rental in ${this.location}.`;
      case RentalStatus.REQUESTED:
        return `Bike ${this.bikeId} is requested by ${this.renter} in ${this.location}`;
      default:
        return 'Status unknown';
    }
  }

  returnedAt(location: string): void {
    this.location = location;
    this.status = RentalStatus.AVAILABLE;
    this.renter = null;
  }

  requestedBy(renter: string): void {
    this.renter = renter;
    this.status = RentalStatus.REQUESTED;
  }

  rentedBy(renter: string): void {
    this.renter = renter;
    this.status = RentalStatus.RENTED;
  }
}
