import { Router } from 'express';

/**
 * The health endpoint Axon Server polls (per the registered Integration endpoint) to decide whether
 * the payment service is available to receive commands, queries and events.
 */
export function healthRouter(): Router {
  const router = Router();
  router.get('/axon/health', (_req, res) => {
    res.status(200).end();
  });
  return router;
}
