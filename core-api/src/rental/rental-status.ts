// The rental lifecycle status of a bike. String-valued so its JSON form matches the Java enum name.
export enum RentalStatus {
  AVAILABLE = 'AVAILABLE',
  REQUESTED = 'REQUESTED',
  RENTED = 'RENTED',
}
