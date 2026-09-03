import test from 'node:test';
import assert from 'node:assert/strict';
import { TournamentService } from '../src/service.ts';

let clock = 1_700_000_000;

function service(options: Record<string, unknown> = {}) {
  return new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    now: () => clock,
    participantScryptCost: 1024,
    rateLimitMax: 1000,
    ...options,
  });
}

function addParticipants(app: TournamentService, count: number) {
  for (let i = 1; i <= count; i += 1) {
    app.addParticipant('admin-test-token', {
      participantId: `p-${i}`,
      displayName: `Player ${i}`,
      participantCode: `PARTICIPANT-${i}`,
    });
  }
}

function throwsCode(action: () => unknown, code: string) {
  assert.throws(action, (error: any) => error?.code === code);
}

function tournament(app: TournamentService, count: number, tournamentId = 'tournament-1') {
  addParticipants(app, count);
  app.createTournament('admin-test-token', {
    tournamentId,
    name: 'YIMO Test Cup',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
    matchTimeoutSeconds: 900,
    roomPortStart: 30000,
    roomPortEnd: 30049,
  });
  return app.seedBracket('admin-test-token', {
    tournamentId,
    participantIds: Array.from({ length: count }, (_, index) => `p-${index + 1}`),
  });
}

test('seeds a non-power-of-two bracket with automatic byes', () => {
  const app = service();
  const result = tournament(app, 5);
  assert.equal(result.matches.length, 7, 'five players require a complete eight-slot bracket');
  assert.ok(result.matches.some((match) => match.status === 'BYE'), 'the bracket must contain byes');
  for (const match of result.matches) {
    if (match.matchCode) {
      assert.match(match.matchCode, /^[A-HJ-NP-Z2-9]{10}$/);
    }
  }
  app.close();
});

test('rejects wrong-build and wrong-participant joins and assigns a signed room', () => {
  const app = service();
  const bracket = tournament(app, 3);
  const openMatch = bracket.matches.find((match) => match.status === 'OPEN');
  assert.ok(openMatch && openMatch.matchCode);
  const firstSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  const secondSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-2',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  const thirdSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-3',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  throwsCode(() => app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'Graphwar-1.1',
    protocolVersion: 1,
  }), 'VERSION_MISMATCH');
  throwsCode(() => app.createParticipantSession({
    participantCode: 'NOT-A-REAL-CODE',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  }), 'INVALID_PARTICIPANT_CODE');
  const firstJoin = app.joinMatch({
    sessionToken: firstSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  assert.equal(firstJoin.roomSlot, 30000);
  assert.ok(firstJoin.roomToken);
  const secondJoin = app.joinMatch({
    sessionToken: secondSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  assert.equal(secondJoin.roomSlot, firstJoin.roomSlot);
  throwsCode(() => app.joinMatch({
    sessionToken: thirdSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  }), 'PARTICIPANT_NOT_IN_MATCH');
  app.close();
});

test('accepts an identical result retry but rejects a conflicting duplicate', () => {
  const app = service();
  const bracket = tournament(app, 2);
  const match = bracket.matches.find((entry) => entry.status === 'OPEN');
  assert.ok(match && match.matchCode);
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  const join = app.joinMatch({
    sessionToken: session.sessionToken,
    matchCode: match.matchCode,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  const resultInput = {
    matchId: match.matchId,
    winnerParticipantId: 'p-1',
    loserParticipantId: 'p-2',
    reason: 'NORMAL',
    roomToken: join.roomToken,
  };
  const first = app.submitResult(resultInput);
  assert.equal(first.duplicate, false);
  const retry = app.submitResult(resultInput);
  assert.equal(retry.duplicate, true);
  throwsCode(() => app.submitResult({
    ...resultInput,
    winnerParticipantId: 'p-2',
    loserParticipantId: 'p-1',
  }), 'RESULT_ALREADY_SUBMITTED');
  app.close();
});

test('stores 5000 participant records without storing their raw codes', () => {
  const app = service({ participantScryptCost: 256 });
  addParticipants(app, 5000);
  assert.equal(app.countParticipants(), 5000);
  assert.equal(app.rawParticipantCodeCount(), 0);
  app.close();
});

test('handles 100 concurrent session and join requests under the configured limit', async () => {
  const app = service({ rateLimitMax: 200 });
  const bracket = tournament(app, 2);
  const match = bracket.matches.find((entry) => entry.status === 'OPEN');
  assert.ok(match && match.matchCode);
  const sessions = await Promise.all(Array.from({ length: 100 }, () => Promise.resolve(app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  }))));
  const joins = await Promise.all(sessions.map((entry) => Promise.resolve(app.joinMatch({
    sessionToken: entry.sessionToken,
    matchCode: match.matchCode,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  }))));
  assert.equal(joins.length, 100);
  assert.ok(joins.every((entry) => entry.roomSlot === 30000));
  app.close();
});
