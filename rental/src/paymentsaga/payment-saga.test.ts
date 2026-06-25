import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import {
  ApproveRequestCommand,
  BikeRequestedEvent,
  type CommandDispatcher,
  PaymentConfirmedEvent,
  PaymentRejectedEvent,
  PreparePaymentCommand,
  RejectRequestCommand,
} from '@bikerental/core-api';
import { PaymentSaga } from './payment-saga.js';

const BIKE_ID = 'bikeId';
const RENTER = 'rider';
const REFERENCE = 'rentalReference';

let sent: object[];
let saga: PaymentSaga;

function recordingDispatcher(): CommandDispatcher {
  return {
    send: (payload: object) => {
      sent.push(payload);
      return Promise.resolve();
    },
  } as unknown as CommandDispatcher;
}

beforeEach(() => {
  sent = [];
  saga = new PaymentSaga(recordingDispatcher());
});

test('bikeRequestPreparesPayment', () => {
  saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));

  assert.ok(sent.some((c) => deepEquals(c, new PreparePaymentCommand(10, REFERENCE))));
});

test('confirmedPaymentApprovesRequest', () => {
  saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));
  saga.on(new PaymentConfirmedEvent('paymentId', REFERENCE));

  assert.ok(sent.some((c) => deepEquals(c, new ApproveRequestCommand(BIKE_ID, RENTER))));
});

test('rejectedPaymentRejectsRequest', () => {
  saga.on(new BikeRequestedEvent(BIKE_ID, RENTER, REFERENCE));
  saga.on(new PaymentRejectedEvent('paymentId', REFERENCE));

  assert.ok(sent.some((c) => deepEquals(c, new RejectRequestCommand(BIKE_ID, RENTER))));
});

function deepEquals(a: object, b: object): boolean {
  return (
    Object.getPrototypeOf(a) === Object.getPrototypeOf(b) &&
    JSON.stringify(a) === JSON.stringify(b)
  );
}
