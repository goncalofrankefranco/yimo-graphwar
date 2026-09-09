import test from 'node:test';
import assert from 'node:assert/strict';
import { assertTransition, canStart, dueTransition } from '../src/lifecycle.ts';

test('scheduled lifecycle opens registration, closes to ready, and auto-starts', () => {
  assert.equal(dueTransition({
    status: 'DRAFT', requireCheckIn: false, autoStart: true,
    registrationOpenAt: 100, registrationCloseAt: 200,
    checkInOpenAt: null, checkInCloseAt: null, startAt: 300,
  }, 100), 'REGISTRATION_OPEN');
  assert.equal(dueTransition({
    status: 'REGISTRATION_OPEN', requireCheckIn: false, autoStart: true,
    registrationOpenAt: 100, registrationCloseAt: 200,
    checkInOpenAt: null, checkInCloseAt: null, startAt: 300,
  }, 200), 'READY');
  assert.equal(dueTransition({
    status: 'READY', requireCheckIn: false, autoStart: true,
    registrationOpenAt: 100, registrationCloseAt: 200,
    checkInOpenAt: null, checkInCloseAt: null, startAt: 300,
  }, 300), 'RUNNING');
  assert.equal(canStart('READY'), true);
});

test('scheduled check-in transitions through CHECK_IN before READY', () => {
  const row = {
    status: 'REGISTRATION_OPEN' as const, requireCheckIn: true, autoStart: false,
    registrationOpenAt: 100, registrationCloseAt: 200,
    checkInOpenAt: 200, checkInCloseAt: 250, startAt: 300,
  };
  assert.equal(dueTransition(row, 200), 'CHECK_IN');
  assert.equal(dueTransition({ ...row, status: 'CHECK_IN' }, 250), 'READY');
});

test('invalid lifecycle transitions expose a 409-compatible error', () => {
  assert.throws(() => assertTransition('RUNNING', 'DRAFT'), (error: any) =>
    error?.status === 409 && error?.code === 'INVALID_STATUS_TRANSITION');
  assert.throws(() => assertTransition('COMPLETED', 'RUNNING'), (error: any) =>
    error?.status === 409 && error?.code === 'INVALID_STATUS_TRANSITION');
});
