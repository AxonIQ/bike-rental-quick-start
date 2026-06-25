// The rental read-model queries. The "named" queries are addressed by a plain string name (as in the
// Framework branch's @QueryHandler(queryName=...)); the count query is addressed by its type name.

export const BikeStatusNamedQueries = {
  FIND_ALL: 'findAll',
  FIND_ONE: 'findOne',
  FIND_AVAILABLE: 'findAvailable',
} as const;

export class CountOfBikesByTypeQuery {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.CountOfBikesByTypeQuery';

  constructor(public readonly bikeType: string) {}
}
