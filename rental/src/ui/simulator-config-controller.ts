import { Router } from 'express';
import { Simulator } from './simulator.js';

/** Lets the running simulator's inventory- and rental-generation parameters be reconfigured. */
export function simulatorConfigRouter(simulator: Simulator): Router {
  const router = Router();

  router.post('/inventoryGenerationConfig', (req, res) => {
    simulator.updateInventoryCreationConfiguration(Number(req.query.size), String(req.query.bikeType));
    res.status(200).end();
  });

  router.post('/rentalGenerationConfig', (req, res) => {
    simulator.updateRentalGenerationConfiguration(
      String(req.query.rentalBikeType),
      Number(req.query.loops),
      req.query.concurrency ? Number(req.query.concurrency) : 1,
      req.query.abandonPaymentFactor ? Number(req.query.abandonPaymentFactor) : 100,
      req.query.delay ? Number(req.query.delay) : 0,
    );
    res.status(200).end();
  });

  return router;
}
