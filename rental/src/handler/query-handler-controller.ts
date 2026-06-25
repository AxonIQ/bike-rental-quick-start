import { Router } from 'express';
import type { AxonSerializer, Query, QueryResult } from '@bikerental/core-api';
import { BikeStatusProjection } from '../query/bike-status-projection.js';

/**
 * Receives point-to-point queries that Axon Server routes to this application and answers them from
 * the {@link BikeStatusProjection} read model. The (possibly collection) result is serialized into
 * the query result payload, which the dispatching side decodes as a single value or a list.
 */
export function queryHandlerRouter(
  projection: BikeStatusProjection,
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
