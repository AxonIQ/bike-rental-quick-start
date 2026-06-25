import { randomUUID } from 'node:crypto';
import { Router, type Response } from 'express';
import {
  BikeStatus,
  BikeStatusNamedQueries,
  CommandDispatcher,
  ConfirmPaymentCommand,
  JavaTypes,
  PaymentStatus,
  PaymentStatusNamedQueries,
  PaymentStatusValue,
  QueryDispatcher,
  RegisterBikeCommand,
  RequestBikeCommand,
  ReturnBikeCommand,
} from '@bikerental/core-api';
import { BikeStatusProjection } from '../query/bike-status-projection.js';
import { BikeRentalDataGenerator } from './bike-rental-data-generator.js';

function openSse(res: Response): void {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();
}

function str(value: unknown): string {
  return value === undefined ? '' : String(value);
}

export function rentalRouter(
  commandDispatcher: CommandDispatcher,
  queryDispatcher: QueryDispatcher,
  projection: BikeStatusProjection,
  dataGenerator: BikeRentalDataGenerator,
): Router {
  const router = Router();

  router.post('/bikes', async (req, res) => {
    const bikeId = randomUUID();
    await commandDispatcher.send(
      new RegisterBikeCommand(bikeId, str(req.query.bikeType), str(req.query.location)),
    );
    res.type('text/plain').send(bikeId);
  });

  router.post('/bikes/batch', async (req, res) => {
    await dataGenerator.generateBikes(Number(req.query.count), str(req.query.type));
    res.status(200).end();
  });

  router.get('/bikes', async (_req, res) => {
    res.json(await queryDispatcher.queryMany<BikeStatus>(BikeStatusNamedQueries.FIND_ALL, null, BikeStatus.TYPE));
  });

  router.get('/bikeUpdates', (req, res) => {
    openSse(res);
    const unsubscribe = projection.subscribeAll((status) => res.write(`data:${status.description()}\n\n`));
    req.on('close', unsubscribe);
  });

  router.get('/bikeUpdatesJson', (req, res) => {
    openSse(res);
    const unsubscribe = projection.subscribeAll((status) => res.write(`data:${JSON.stringify(status)}\n\n`));
    req.on('close', unsubscribe);
  });

  router.get('/bikeUpdates/:bikeId', (req, res) => {
    openSse(res);
    const unsubscribe = projection.subscribeOne(req.params.bikeId, (status) =>
      res.write(`data:${status.description()}\n\n`),
    );
    req.on('close', unsubscribe);
  });

  router.post('/requestBike', async (req, res) => {
    const renter = req.query.renter ? str(req.query.renter) : dataGenerator.randomRenter();
    const paymentRef = await commandDispatcher.send<string>(
      new RequestBikeCommand(str(req.query.bikeId), renter),
      JavaTypes.STRING,
    );
    res.type('text/plain').send(paymentRef ?? '');
  });

  router.post('/returnBike', async (req, res) => {
    await commandDispatcher.send(new ReturnBikeCommand(str(req.query.bikeId), dataGenerator.randomLocation()));
    res.status(200).end();
  });

  router.get('/findPayment', async (req, res) => {
    const paymentId = await queryDispatcher.pollFor<string>(
      PaymentStatusNamedQueries.GET_PAYMENT_ID,
      str(req.query.reference),
      JavaTypes.STRING,
      (result) => result !== null,
    );
    res.type('text/plain').send(paymentId);
  });

  router.get('/pendingPayments', async (_req, res) => {
    res.json(
      await queryDispatcher.queryMany<PaymentStatus>(
        PaymentStatusNamedQueries.GET_ALL_PAYMENTS,
        PaymentStatusValue.PENDING,
        PaymentStatus.TYPE,
      ),
    );
  });

  router.post('/acceptPayment', async (req, res) => {
    await commandDispatcher.send(new ConfirmPaymentCommand(str(req.query.id)));
    res.status(200).end();
  });

  router.get('/watch', (req, res) => {
    openSse(res);
    const unsubscribe = projection.subscribeAll((bs) =>
      res.write(`data:${bs.getBikeId()} -> ${bs.description()}\n\n`),
    );
    req.on('close', unsubscribe);
  });

  router.get('/watch/:bikeId', (req, res) => {
    openSse(res);
    const unsubscribe = projection.subscribeOne(req.params.bikeId, (bs) =>
      res.write(`data:${bs.getBikeId()} -> ${bs.description()}\n\n`),
    );
    req.on('close', unsubscribe);
  });

  router.post('/generateRentals', async (req, res) => {
    res.type('text/plain');
    await dataGenerator.generateRentals(
      str(req.query.bikeType),
      Number(req.query.loops),
      req.query.concurrency ? Number(req.query.concurrency) : 1,
      req.query.abandonPaymentFactor ? Number(req.query.abandonPaymentFactor) : 100,
      req.query.delay ? Number(req.query.delay) : 0,
      (line) => res.write(line),
    );
    res.end();
  });

  router.get('/bikes/:bikeId', async (req, res) => {
    res.json(await queryDispatcher.query<BikeStatus>(BikeStatusNamedQueries.FIND_ONE, req.params.bikeId, BikeStatus.TYPE));
  });

  return router;
}
