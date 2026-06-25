import {
  AxonSerializer,
  BikeInUseEvent,
  BikeRegisteredEvent,
  BikeRequestedEvent,
  BikeReturnedEvent,
  BikeStatus,
  BikeStatusNamedQueries,
  CountOfBikesByTypeQuery,
  JavaTypes,
  RentalStatus,
  RequestRejectedEvent,
  type Query,
} from '@bikerental/core-api';
import { BikeStatusRepository } from './bike-status-repository.js';
import { SubscriptionRegistry, type Listener } from './subscription-registry.js';

/**
 * The read model for bikes. It answers point-to-point queries (invoked by the query handler
 * controller when Axon Server routes a query here) and is fed events by the event handler controller.
 * Because the read model lives in this process, live UI updates are served directly via a
 * {@link SubscriptionRegistry}.
 */
export class BikeStatusProjection {
  private readonly subscriptions = new SubscriptionRegistry<BikeStatus>();

  constructor(
    private readonly repository: BikeStatusRepository,
    private readonly serializer: AxonSerializer,
  ) {}

  // -------------------------------------------------------------------------------------------
  // Query handling (point-to-point)
  // -------------------------------------------------------------------------------------------

  handle(query: Query): unknown {
    switch (query.name) {
      case BikeStatusNamedQueries.FIND_ALL:
        return this.repository.findAll();
      case BikeStatusNamedQueries.FIND_AVAILABLE:
        return this.repository.findAllByBikeTypeAndStatus(
          this.payloadAsString(query),
          RentalStatus.AVAILABLE,
        );
      case BikeStatusNamedQueries.FIND_ONE:
        return this.repository.findById(this.payloadAsString(query)) ?? null;
      case CountOfBikesByTypeQuery.TYPE: {
        const payload = this.serializer.fromJson<CountOfBikesByTypeQuery>(
          query.payload,
          CountOfBikesByTypeQuery.TYPE,
        );
        return this.repository.countBikeStatusesByBikeType(payload!.bikeType);
      }
      default:
        throw new Error(`No handler for query ${query.name}`);
    }
  }

  // -------------------------------------------------------------------------------------------
  // Live updates (local subscriptions, served to the UI)
  // -------------------------------------------------------------------------------------------

  subscribeAll(onNext: Listener<BikeStatus>): () => void {
    for (const status of this.repository.findAll()) {
      onNext(status);
    }
    return this.subscriptions.register(null, onNext);
  }

  subscribeOne(bikeId: string, onNext: Listener<BikeStatus>): () => void {
    const initial = this.repository.findById(bikeId);
    if (initial) {
      onNext(initial);
    }
    return this.subscriptions.register(bikeId, onNext);
  }

  // -------------------------------------------------------------------------------------------
  // Event handling (called by the event handler controller)
  // -------------------------------------------------------------------------------------------

  on(event: unknown): void {
    if (event instanceof BikeRegisteredEvent) {
      const status = new BikeStatus(event.bikeId, event.bikeType, event.location);
      this.emit(this.repository.save(status));
    } else if (event instanceof BikeRequestedEvent) {
      this.update(event.bikeId, (status) => status.requestedBy(event.renter));
    } else if (event instanceof BikeInUseEvent) {
      this.update(event.bikeId, (status) => status.rentedBy(event.renter));
    } else if (event instanceof BikeReturnedEvent) {
      this.update(event.bikeId, (status) => status.returnedAt(event.location));
    } else if (event instanceof RequestRejectedEvent) {
      this.update(event.bikeId, (status) => status.returnedAt(status.getLocation()));
    }
  }

  private update(bikeId: string, mutation: (status: BikeStatus) => void): void {
    const status = this.repository.findById(bikeId);
    if (status) {
      mutation(status);
      this.emit(this.repository.save(status));
    }
  }

  private emit(status: BikeStatus): void {
    this.subscriptions.emit(status.getBikeId(), status);
  }

  private payloadAsString(query: Query): string {
    return this.serializer.fromJson<string>(query.payload, JavaTypes.STRING)!;
  }
}
