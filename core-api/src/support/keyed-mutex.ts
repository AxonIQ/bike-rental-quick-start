/**
 * Serializes async work by key. This plays the role Axon Framework's aggregate locking used to: it
 * makes the load → decide → append cycle for one aggregate id run to completion before the next
 * command for the same id starts, so sequence numbers never collide within this instance. Work for
 * different keys still runs concurrently.
 */
export class KeyedMutex {
  private readonly tails = new Map<string, Promise<void>>();

  run<T>(key: string, task: () => Promise<T> | T): Promise<T> {
    const previous = this.tails.get(key) ?? Promise.resolve();
    const result = previous.then(() => task());
    // The tail never rejects, so the next task always runs regardless of this one's outcome.
    const tail = result.then(
      () => undefined,
      () => undefined,
    );
    this.tails.set(key, tail);
    void tail.then(() => {
      if (this.tails.get(key) === tail) {
        this.tails.delete(key);
      }
    });
    return result;
  }
}
