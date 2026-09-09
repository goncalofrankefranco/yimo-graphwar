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

test('returns a public bracket without exposing match codes', () => {
  const app = service();
  tournament(app, 5);
  const bracket: any = app.publicBracket('tournament-1');
  assert.equal(bracket.tournamentId, 'tournament-1');
  assert.equal(bracket.name, 'YIMO Test Cup');
  assert.equal(bracket.matches.length, 7);
  assert.equal(bracket.matches[0].playerA, 'Player 1');
  assert.equal(bracket.matches[0].playerB, 'Player 2');
  assert.ok(bracket.matches.some((match: any) => match.status === 'BYE'));
  assert.ok(bracket.matches.every((match: any) => !('matchCode' in match)));
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

test('registers participants idempotently and closes registration without check-in', () => {
  const app = service();
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'registration-test',
    name: 'Registration Test',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  throwsCode(() => app.registerParticipant(session.sessionToken, 'registration-test'), 'REGISTRATION_NOT_OPEN');
  app.openRegistration('admin-test-token', 'registration-test');
  const first = app.registerParticipant(session.sessionToken, 'registration-test');
  const duplicate = app.registerParticipant(session.sessionToken, 'registration-test');
  assert.deepEqual(duplicate, first);
  app.closeRegistration('admin-test-token', 'registration-test');
  throwsCode(() => app.registerParticipant(session.sessionToken, 'registration-test'), 'REGISTRATION_CLOSED');
  throwsCode(() => app.checkInParticipant(session.sessionToken, 'registration-test'), 'CHECK_IN_DISABLED');
  app.close();
});

test('supports an enabled check-in window and returns safe admin/player projections', () => {
  const app = service();
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'check-in-test',
    name: 'Check-in Test',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
    requireCheckIn: true,
    registrationOpenAt: clock,
    registrationCloseAt: clock + 100,
    checkInOpenAt: clock + 10,
    checkInCloseAt: clock + 200,
    startAt: clock + 300,
  });
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'check-in-test');
  const registered = app.registerParticipant(session.sessionToken, 'check-in-test');
  assert.equal(registered.entryStatus, 'REGISTERED');
  app.openCheckIn('admin-test-token', 'check-in-test');
  const checkedIn = app.checkInParticipant(session.sessionToken, 'check-in-test');
  assert.equal(checkedIn.entryStatus, 'CHECKED_IN');
  assert.deepEqual(app.checkInParticipant(session.sessionToken, 'check-in-test'), checkedIn);
  const admin: any = app.adminTournament('admin-test-token', 'check-in-test');
  assert.equal(admin.status, 'CHECK_IN');
  assert.equal(admin.counts.registered, 1);
  assert.equal(admin.counts.checkedIn, 1);
  assert.equal(admin.roster[0].participantId, 'p-1');
  const player: any = app.playerTournament(session.sessionToken, 'check-in-test');
  assert.equal(player.entry.entryStatus, 'CHECKED_IN');
  assert.equal(player.nextMatch, null);
  assert.ok(!JSON.stringify(player).includes('PARTICIPANT-1'));
  app.close();
});

test('rejects malformed tournament schedule values', () => {
  const app = service();
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'bad-schedule-1', name: 'Bad Schedule',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    registrationOpenAt: 100.5,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'bad-schedule-2', name: 'Bad Schedule',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    registrationOpenAt: 200, registrationCloseAt: 100,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'bad-schedule-3', name: 'Bad Schedule',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    autoStart: true,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  app.close();
});

test('starts a ready tournament once, freezes its roster, and blocks late entries', () => {
  const app = service();
  addParticipants(app, 4);
  app.createTournament('admin-test-token', {
    tournamentId: 'manual-start-test', name: 'Manual Start Test',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'manual-start-test');
  for (let index = 1; index <= 4; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `PARTICIPANT-${index}`, buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    });
    app.registerParticipant(session.sessionToken, 'manual-start-test');
  }
  app.closeRegistration('admin-test-token', 'manual-start-test');
  const started: any = app.startTournament('admin-test-token', 'manual-start-test');
  assert.equal(started.status, 'RUNNING');
  assert.equal(started.schedule.rosterFrozenAt, clock);
  assert.equal(started.bracket.matches.length, 3);
  const repeated: any = app.startTournament('admin-test-token', 'manual-start-test');
  assert.equal(repeated.status, 'RUNNING');
  assert.equal(repeated.bracket.matches.length, 3);
  const lateSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  throwsCode(() => app.registerParticipant(lateSession.sessionToken, 'manual-start-test'), 'ROSTER_FROZEN');
  app.close();
});

test('blocks a manual start with fewer than two eligible entries', () => {
  const app = service();
  addParticipants(app, 1);
  app.createTournament('admin-test-token', {
    tournamentId: 'blocked-start-test', name: 'Blocked Start Test',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'blocked-start-test');
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  app.registerParticipant(session.sessionToken, 'blocked-start-test');
  app.closeRegistration('admin-test-token', 'blocked-start-test');
  const first: any = app.startTournament('admin-test-token', 'blocked-start-test');
  const second: any = app.startTournament('admin-test-token', 'blocked-start-test');
  assert.equal(first.status, 'START_BLOCKED');
  assert.equal(second.status, 'START_BLOCKED');
  assert.equal(first.bracket.matches.length, 0);
  assert.equal(second.bracket.matches.length, 0);
  app.close();
});

test('shows the next assigned match and joins it without exposing a match code', () => {
  const app = service();
  addParticipants(app, 3);
  app.createTournament('admin-test-token', {
    tournamentId: 'assigned-join-test', name: 'Assigned Join Test',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'assigned-join-test');
  const sessions = [1, 2, 3].map((index) => app.createParticipantSession({
    participantCode: `PARTICIPANT-${index}`, buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  }));
  app.registerParticipant(sessions[0].sessionToken, 'assigned-join-test');
  app.registerParticipant(sessions[1].sessionToken, 'assigned-join-test');
  app.closeRegistration('admin-test-token', 'assigned-join-test');
  app.startTournament('admin-test-token', 'assigned-join-test');
  const player: any = app.playerTournament(sessions[0].sessionToken, 'assigned-join-test');
  assert.ok(player.nextMatch?.matchId);
  assert.ok(!JSON.stringify(player).includes('matchCode'));
  const input = {
    matchId: player.nextMatch.matchId,
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
  };
  const first = app.joinAssignedMatch({ ...input, sessionToken: sessions[0].sessionToken });
  const second = app.joinAssignedMatch({ ...input, sessionToken: sessions[1].sessionToken });
  assert.equal(second.roomSlot, first.roomSlot);
  assert.equal(second.matchId, first.matchId);
  assert.ok(first.roomToken);
  assert.throws(() => app.joinAssignedMatch({
    ...input, sessionToken: sessions[2].sessionToken,
  }), (error: any) => error?.code === 'PARTICIPANT_NOT_IN_MATCH');
  assert.throws(() => app.joinAssignedMatch({
    ...input, sessionToken: sessions[0].sessionToken, buildId: 'Graphwar-1.1', protocolVersion: 1,
  }), (error: any) => error?.code === 'VERSION_MISMATCH');
  app.close();
});
