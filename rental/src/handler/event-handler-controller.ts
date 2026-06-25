import { Router } from 'express';
import { AxonSerializer, createLogger, eventType, type Event } from '@bikerental/core-api';
import { BikeStatusProjection } from '../query/bike-status-projection.js';
import { PaymentSaga } from '../paymentsaga/payment-saga.js';

const logger = createLogger('EventHandlerController');

/**
 * Receives the event batches that Axon Server pushes from the persistent streams backing this
 * application's two event handlers: one feeds the bike read model, the other drives the payment saga.
 * The stream position is tracked by Axon Server, so there is no token to persist here. Events whose
 * payload type is unknown to this application are skipped (the stream still advances).
 */
export function eventHandlerRouter(
  projection: BikeStatusProjection,
  saga: PaymentSaga,
  serializer: AxonSerializer,
): Router {
  const router = Router();

  const process = (body: unknown, handler: (payload: unknown) => void) => {
    for (const event of parseEvents(body)) {
      let payload: unknown;
      try {
        payload = serializer.fromJson(event.payload, eventType(event));
      } catch {
        logger.debug(`Skipping event ${event.id} of unknown type ${eventType(event)}`);
        continue;
      }
      handler(payload);
    }
  };

  router.post('/axon/events/bike-projection', (req, res) => {
    process(req.body, (payload) => projection.on(payload));
    res.status(200).end();
  });

  router.post('/axon/events/payment-saga', (req, res) => {
    process(req.body, (payload) => saga.accept(payload));
    res.status(200).end();
  });

  return router;
}

/** Accepts either a bare JSON array of events or an object wrapping them under `events`. */
function parseEvents(body: unknown): Event[] {
  if (Array.isArray(body)) {
    return body as Event[];
  }
  const events = (body as { events?: unknown } | null)?.events;
  return Array.isArray(events) ? (events as Event[]) : [];
}
