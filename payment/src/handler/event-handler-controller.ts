import { Router } from 'express';
import { AxonSerializer, createLogger, eventType, type Event } from '@bikerental/core-api';
import { PaymentStatusProjection } from '../payment-status-projection.js';

const logger = createLogger('EventHandlerController');

/**
 * Receives the event batches Axon Server pushes from the persistent stream backing the payment read
 * model's event handler. Events whose payload type is unknown here are skipped (the stream still
 * advances), so foreign events in the shared store don't stall the projection.
 */
export function eventHandlerRouter(
  projection: PaymentStatusProjection,
  serializer: AxonSerializer,
): Router {
  const router = Router();
  router.post('/axon/events/payment-status', (req, res) => {
    for (const event of parseEvents(req.body)) {
      let payload: unknown;
      try {
        payload = serializer.fromJson(event.payload, eventType(event));
      } catch {
        logger.debug(`Skipping event ${event.id} of unknown type ${eventType(event)}`);
        continue;
      }
      projection.on(payload);
    }
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
