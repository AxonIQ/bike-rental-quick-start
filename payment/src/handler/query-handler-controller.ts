import { Router } from 'express';
import type { AxonSerializer, Query, QueryResult } from '@bikerental/core-api';
import { PaymentStatusProjection } from '../payment-status-projection.js';

/**
 * Receives point-to-point queries that Axon Server routes to the payment service and answers them
 * from the {@link PaymentStatusProjection} read model.
 */
export function queryHandlerRouter(
  projection: PaymentStatusProjection,
  serializer: AxonSerializer,
): Router {
  const router = Router();
  router.post('/axon/query', (req, res) => {
    const query = req.body as Query;
    try {
      const result = projection.handle(query);
      const payload = result === null || result === undefined ? null : serializer.toJson(result);
      res.json({ id: query.id, payload } satisfies QueryResult);
    } catch (error) {
      res.status(500).json({ message: String((error as Error)?.message) });
    }
  });
  return router;
}
