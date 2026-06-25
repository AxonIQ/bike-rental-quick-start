/**
 * Keeps track of active in-memory subscriptions to a projection and pushes updates to them — the
 * stand-in for Axon Framework's `QueryUpdateEmitter` (and the subscription-query support the HTTP
 * query API does not offer). Because the read model lives in this process and is fed by an
 * event-handler callback, live UI updates can be served locally.
 *
 * A subscription has an optional filter: a `null` filter receives every emit (used for "find all"); a
 * non-null filter only receives emits whose key equals it (used for "find one" by id).
 */
export type Listener<T> = (value: T) => void;

interface Subscription<T> {
  filter: string | null;
  listener: Listener<T>;
}

export class SubscriptionRegistry<T> {
  private readonly subscriptions = new Set<Subscription<T>>();

  /** Registers a listener and returns a function that unsubscribes it. */
  register(filter: string | null, listener: Listener<T>): () => void {
    const subscription: Subscription<T> = { filter, listener };
    this.subscriptions.add(subscription);
    return () => {
      this.subscriptions.delete(subscription);
    };
  }

  emit(key: string, value: T): void {
    for (const subscription of this.subscriptions) {
      if (subscription.filter === null || subscription.filter === key) {
        subscription.listener(value);
      }
    }
  }
}
