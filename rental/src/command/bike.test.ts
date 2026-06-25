import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ApproveRequestCommand,
  BikeInUseEvent,
  BikeRegisteredEvent,
  BikeRequestedEvent,
  BikeReturnedEvent,
  RegisterBikeCommand,
  RejectRequestCommand,
  RequestBikeCommand,
  RequestRejectedEvent,
  ReturnBikeCommand,
} from '@bikerental/core-api';
import { Bike } from './bike.js';

const REFERENCE = 'rentalId';

const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

function given(...events: object[]): Bike {
  const bike = new Bike();
  for (const event of events) {
    bike.apply(event);
  }
  return bike;
}

// decideOnRegister carries a deliberate time-based "taint"; retry across the 5s window.
async function registerWithRetry(command: RegisterBikeCommand): Promise<object[]> {
  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      return new Bike().decideOnRegister(command);
    } catch {
      await delay(1100);
    }
  }
  throw new Error('Could not register a bike within the retry window');
}

test('canRegisterBike', async () => {
  const events = await registerWithRetry(new RegisterBikeCommand('bikeId-1234', 'city-bike', 'Amsterdam'));
  assert.deepStrictEqual(events, [new BikeRegisteredEvent('bikeId-1234', 'city-bike', 'Amsterdam')]);
});

test('cannotRegisterExistingBike', () => {
  const bike = given(new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'));
  assert.throws(() => bike.decideOnRegister(new RegisterBikeCommand('bikeId', 'city', 'Amsterdam')));
});

test('canRequestAvailableBike', () => {
  const bike = given(new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'));
  const events = bike.decideOnRequest(new RequestBikeCommand('bikeId', 'rider'), REFERENCE);
  assert.equal(events.length, 1);
  const event = events[0] as BikeRequestedEvent;
  assert.ok(event instanceof BikeRequestedEvent);
  assert.equal(event.bikeId, 'bikeId');
  assert.equal(event.renter, 'rider');
});

test('cannotRequestAlreadyRequestedBike', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
  );
  assert.throws(() => bike.decideOnRequest(new RequestBikeCommand('bikeId', 'rider'), REFERENCE));
});

test('canApproveRequestedBike', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
  );
  assert.deepStrictEqual(bike.decideOnApprove(new ApproveRequestCommand('bikeId', 'rider')), [
    new BikeInUseEvent('bikeId', 'rider'),
  ]);
});

test('canRejectRequestedBike', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
  );
  assert.deepStrictEqual(bike.decideOnReject(new RejectRequestCommand('bikeId', 'rider')), [
    new RequestRejectedEvent('bikeId'),
  ]);
});

test('cannotRejectRequestForWrongRequester', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
  );
  assert.equal(bike.decideOnReject(new RejectRequestCommand('bikeId', 'otherRider')).length, 0);
});

test('cannotApproveRequestForAnotherRider', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
  );
  assert.equal(bike.decideOnApprove(new ApproveRequestCommand('bikeId', 'otherRider')).length, 0);
});

test('canReturnBikeInUse', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
    new BikeInUseEvent('bikeId', 'rider'),
  );
  assert.deepStrictEqual(bike.decideOnReturn(new ReturnBikeCommand('bikeId', 'NewLocation')), [
    new BikeReturnedEvent('bikeId', 'NewLocation'),
  ]);
});

test('cannotRequestBikeInUse', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
    new BikeInUseEvent('bikeId', 'rider'),
  );
  assert.throws(() => bike.decideOnRequest(new RequestBikeCommand('bikeId', 'otherRenter'), REFERENCE));
});

test('canRequestReturnedBike', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
    new BikeInUseEvent('bikeId', 'rider'),
    new BikeReturnedEvent('bikeId', 'NewLocation'),
  );
  const events = bike.decideOnRequest(new RequestBikeCommand('bikeId', 'newRider'), REFERENCE);
  const event = events[0] as BikeRequestedEvent;
  assert.ok(event instanceof BikeRequestedEvent);
  assert.equal(event.renter, 'newRider');
});

test('canRequestRejectedBike', () => {
  const bike = given(
    new BikeRegisteredEvent('bikeId', 'city', 'Amsterdam'),
    new BikeRequestedEvent('bikeId', 'rider', REFERENCE),
    new RequestRejectedEvent('bikeId'),
  );
  const events = bike.decideOnRequest(new RequestBikeCommand('bikeId', 'newRider'), REFERENCE);
  const event = events[0] as BikeRequestedEvent;
  assert.ok(event instanceof BikeRequestedEvent);
  assert.equal(event.renter, 'newRider');
});
