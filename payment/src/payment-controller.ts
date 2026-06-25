import { Router } from 'express';
import {
  CommandDispatcher,
  ConfirmPaymentCommand,
  JavaTypes,
  PaymentStatus,
  PaymentStatusNamedQueries,
  PaymentStatusValue,
  QueryDispatcher,
  RejectPaymentCommand,
} from '@bikerental/core-api';

const GET_STATUS = 'getStatus';

export function paymentRouter(
  queryDispatcher: QueryDispatcher,
  commandDispatcher: CommandDispatcher,
): Router {
  const router = Router();

  router.get('/status/:paymentId', async (req, res) => {
    res.json(await queryDispatcher.query<PaymentStatus>(GET_STATUS, req.params.paymentId, PaymentStatus.TYPE));
  });

  router.get('/findPayment', async (req, res) => {
    const paymentId = await queryDispatcher.query<string>(
      PaymentStatusNamedQueries.GET_PAYMENT_ID,
      String(req.query.reference ?? ''),
      JavaTypes.STRING,
    );
    res.type('text/plain').send(paymentId ?? '');
  });

  router.post('/acceptPayment', async (req, res) => {
    await commandDispatcher.send(new ConfirmPaymentCommand(String(req.query.id ?? '')));
    res.status(200).end();
  });

  router.post('/rejectPayment', async (req, res) => {
    await commandDispatcher.send(new RejectPaymentCommand(String(req.query.id ?? '')));
    res.status(200).end();
  });

  router.get('/status', async (req, res) => {
    const status = req.query.status ? (String(req.query.status) as PaymentStatusValue) : null;
    res.json(
      await queryDispatcher.queryMany<PaymentStatus>(
        PaymentStatusNamedQueries.GET_ALL_PAYMENTS,
        status,
        PaymentStatus.TYPE,
      ),
    );
  });

  return router;
}
