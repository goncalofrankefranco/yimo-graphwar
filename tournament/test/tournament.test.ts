import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { TournamentService, verifyRoomToken } from '../src/service.ts';

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

function signRoomResult(secret: string, matchId: string, winnerId: string, loserId: string,
  reason: string, nonce: string) {
  return createHmac('sha256', secret)
    .update(`${matchId}|${winnerId}|${loserId}|${reason}|${nonce}`, 'utf8')
    .digest('base64url');
}

function tournament(app: TournamentService, count: number, tournamentId = 'tournament-1') {
  addParticipants(app, count);
  app.createTournament('admin-test-token', {
    tournamentId,
    name: 'YIMO Test Cup',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
    matchTimeoutSeconds: 900,
    roomPortStart: 31000,
    roomPortEnd: 31049,
  });
  const seeded = app.seedBracket('admin-test-token', {
    tournamentId,
    participantIds: Array.from({ length: count }, (_, index) => `p-${index + 1}`),
  });
  app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?").run(tournamentId);
  return seeded;
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
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  const secondSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-2',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  const thirdSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-3',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  throwsCode(() => app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'Graphwar-1.1',
    protocolVersion: 1,
  }), 'VERSION_MISMATCH');
  throwsCode(() => app.createParticipantSession({
    participantCode: 'NOT-A-REAL-CODE',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  }), 'INVALID_PARTICIPANT_CODE');
  const firstJoin = app.joinMatch({
    sessionToken: firstSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  assert.equal(firstJoin.roomSlot, 31000);
  assert.ok(firstJoin.roomToken);
  assert.equal(verifyRoomToken(firstJoin.roomToken, 'room-test-secret', clock * 1000)?.displayName,
    'Player 1', 'the signed room token must bind the organizer-canonical name');
  const secondJoin = app.joinMatch({
    sessionToken: secondSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  assert.equal(secondJoin.roomSlot, firstJoin.roomSlot);
  throwsCode(() => app.joinMatch({
    sessionToken: thirdSession.sessionToken,
    matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  }), 'PARTICIPANT_NOT_IN_MATCH');
  app.close();
});

test('rejects match-code joins before the tournament is running', () => {
  const app = service();
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'premature-join', name: 'Premature Join',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const seeded = app.seedBracket('admin-test-token', {
    tournamentId: 'premature-join', participantIds: ['p-1', 'p-2'],
  });
  const openMatch = seeded.matches.find((match: any) => match.status === 'OPEN') as any;
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  throwsCode(() => app.joinMatch({
    sessionToken: session.sessionToken, matchCode: openMatch.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  }), 'TOURNAMENT_NOT_RUNNING');
  app.close();
});

test('accepts an identical result retry but rejects a conflicting duplicate', () => {
  const app = service();
  const bracket = tournament(app, 2);
  const match = bracket.matches.find((entry) => entry.status === 'OPEN');
  assert.ok(match && match.matchCode);
  app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?").run('tournament-1');
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  const join = app.joinMatch({
    sessionToken: session.sessionToken,
    matchCode: match.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  app.heartbeat({ roomToken: join.roomToken, state: 'IN_PROGRESS' });
  const nonce = 'room-result-nonce-idempotent';
  const resultInput = {
    matchId: match.matchId,
    winnerParticipantId: 'p-1',
    loserParticipantId: 'p-2',
    reason: 'NORMAL',
    serverNonce: nonce,
    serverSignature: signRoomResult('room-test-secret', match.matchId, 'p-1', 'p-2', 'NORMAL', nonce),
  };
  const first = app.submitResult(resultInput);
  assert.equal(first.duplicate, false);
  const retry = app.submitResult(resultInput);
  assert.equal(retry.duplicate, true);
  throwsCode(() => app.submitResult({
    ...resultInput,
    winnerParticipantId: 'p-2',
    loserParticipantId: 'p-1',
    serverSignature: signRoomResult('room-test-secret', match.matchId, 'p-2', 'p-1', 'NORMAL', nonce),
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
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  }))));
  const joins = await Promise.all(sessions.map((entry) => Promise.resolve(app.joinMatch({
    sessionToken: entry.sessionToken,
    matchCode: match.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  }))));
  assert.equal(joins.length, 100);
  assert.ok(joins.every((entry) => entry.roomSlot === 31000));
  app.close();
});

test('accepts a result signed by the room server without trusting a player room token', () => {
  const app = service();
  const bracket = tournament(app, 2);
  const match = bracket.matches.find((entry) => entry.status === 'OPEN');
  assert.ok(match?.matchCode);
  app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?").run('tournament-1');
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const join = app.joinMatch({
    sessionToken: session.sessionToken, matchCode: match.matchCode,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.heartbeat({ roomToken: join.roomToken, state: 'IN_PROGRESS' });
  const nonce = 'room-result-nonce-0001';
  const result = {
    matchId: match.matchId,
    winnerParticipantId: 'p-1',
    loserParticipantId: 'p-2',
    reason: 'NORMAL',
    serverNonce: nonce,
    serverSignature: signRoomResult('room-test-secret', match.matchId, 'p-1', 'p-2', 'NORMAL', nonce),
  };

  assert.equal(app.submitResult(result).duplicate, false);
  assert.equal(app.publicBracket('tournament-1').matches[0].status, 'COMPLETED');
  assert.equal(app.activeTournament(), null, 'the finished tournament should stop being active');
  app.close();
});

test('only one tournament may enter an active lifecycle at a time', () => {
  const app = service();
  app.createTournament('admin-test-token', {
    tournamentId: 'active-one', name: 'Active One', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.createTournament('admin-test-token', {
    tournamentId: 'active-two', name: 'Active Two', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });

  app.openRegistration('admin-test-token', 'active-one');
  assert.equal(app.activeTournament().tournamentId, 'active-one');
  throwsCode(() => app.openRegistration('admin-test-token', 'active-two'), 'ACTIVE_TOURNAMENT_EXISTS');
  app.close();
});

test('new tournaments default to a 20-minute match limit', () => {
  const app = service();
  app.createTournament('admin-test-token', {
    tournamentId: 'twenty-minute-default', name: 'Twenty Minute Default',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });

  assert.equal(app.db.prepare('SELECT match_timeout_seconds FROM tournaments WHERE tournament_id = ?')
    .get('twenty-minute-default').match_timeout_seconds, 1200);
  app.close();
});

test('tournament match limits cannot exceed 20 minutes', () => {
  const app = service();
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'overlong-match', name: 'Overlong Match',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, matchTimeoutSeconds: 1201,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  app.close();
});

test('public and organizer views prefer a running tournament over a newer legacy start-blocked record', () => {
  const app = service();
  tournament(app, 2, 'running-event');
  app.createTournament('admin-test-token', {
    tournamentId: 'legacy-blocked-event', name: 'Legacy Blocked Event',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.db.prepare("UPDATE tournaments SET status = 'START_BLOCKED', updated_at = ? WHERE tournament_id = ?")
    .run(clock + 1, 'legacy-blocked-event');

  assert.equal(app.activeTournament()?.tournamentId, 'running-event');
  assert.equal(app.currentAdminTournament('admin-test-token')?.tournamentId, 'running-event');
  app.close();
});

test('start-blocked events stay recoverable for organizers but are not advertised as public active tournaments', () => {
  const app = service();
  app.createTournament('admin-test-token', {
    tournamentId: 'legacy-blocked-only', name: 'Legacy Blocked Only',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.db.prepare("UPDATE tournaments SET status = 'START_BLOCKED' WHERE tournament_id = ?")
    .run('legacy-blocked-only');

  assert.equal(app.activeTournament(), null);
  assert.equal(app.currentAdminTournament('admin-test-token')?.tournamentId, 'legacy-blocked-only');
  app.close();
});

test('a verified win advances the player to the next match and completes the tournament final', () => {
  const app = service();
  const seeded = tournament(app, 4);
  app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?").run('tournament-1');

  const submit = (matchId: string, winnerId: string, loserId: string, index: number) => {
    const session = app.createParticipantSession({
      participantCode: `PARTICIPANT-${winnerId.slice(2)}`,
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    const room = app.joinAssignedMatch({
      sessionToken: session.sessionToken, matchId,
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.heartbeat({ roomToken: room.roomToken, state: 'IN_PROGRESS' });
    const nonce = `room-result-nonce-${String(index).padStart(4, '0')}`;
    return app.submitResult({
      matchId,
      winnerParticipantId: winnerId,
      loserParticipantId: loserId,
      reason: 'NORMAL',
      serverNonce: nonce,
      serverSignature: signRoomResult('room-test-secret', matchId, winnerId, loserId, 'NORMAL', nonce),
    });
  };

  const firstRound = seeded.matches.filter((match: any) => match.round === 1);
  submit(firstRound[0].matchId, 'p-1', 'p-2', 1);
  const firstWinner = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  assert.equal((app.playerTournament(firstWinner.sessionToken, 'tournament-1') as any).nextMatch.status, 'PENDING');

  submit(firstRound[1].matchId, 'p-4', 'p-3', 2);
  const waiting = app.playerTournament(firstWinner.sessionToken, 'tournament-1') as any;
  assert.equal(waiting.nextMatch.status, 'OPEN');
  assert.equal(waiting.nextMatch.playerA, 'Player 1');
  assert.equal(waiting.nextMatch.playerB, 'Player 4');

  const finalMatch = app.db.prepare("SELECT match_id AS matchId FROM matches WHERE round = 2").get() as any;
  const result = submit(finalMatch.matchId, 'p-4', 'p-1', 3);
  assert.equal(result.duplicate, false);
  assert.equal(app.publicBracket('tournament-1').status, 'COMPLETED');
  assert.equal(app.activeTournament(), null);
  app.close();
});

test('the game can join an active assigned match using only the candidate code and room port', () => {
  const app = service();
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'game-join-test', name: 'Game Join Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'game-join-test');
  for (let index = 1; index <= 2; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `PARTICIPANT-${index}`, displayName: `Official Player ${index}`,
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.registerParticipant(session.sessionToken, 'game-join-test');
  }
  app.closeRegistration('admin-test-token', 'game-join-test');
  app.startTournament('admin-test-token', 'game-join-test');

  const joined = app.joinActiveMatch({
    participantCode: 'PARTICIPANT-1', displayName: 'Official Player 1', roomPort: 31000,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  assert.equal(joined.port, 31000);
  assert.ok(joined.roomToken);
  assert.throws(() => app.joinActiveMatch({
    participantCode: 'PARTICIPANT-2', displayName: 'Official Player 2', roomPort: 31001,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  }), (error: any) => error?.code === 'WRONG_TOURNAMENT_PORT');
  app.close();
});

test('registers participants idempotently and closes registration without check-in', () => {
  const app = service();
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'registration-test',
    name: 'Registration Test',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
  });
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
    requireCheckIn: true,
    registrationOpenAt: clock,
    registrationCloseAt: clock + 100,
    checkInOpenAt: clock + 10,
    checkInCloseAt: clock + 200,
    startAt: clock + 300,
  });
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    registrationOpenAt: 100.5,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'bad-schedule-2', name: 'Bad Schedule',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    registrationOpenAt: 200, registrationCloseAt: 100,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  assert.throws(() => app.createTournament('admin-test-token', {
    tournamentId: 'bad-schedule-3', name: 'Bad Schedule',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    autoStart: true,
  }), (error: any) => error?.code === 'INVALID_INPUT');
  app.close();
});

test('starts a ready tournament once, freezes its roster, and blocks late entries', () => {
  const app = service();
  addParticipants(app, 4);
  app.createTournament('admin-test-token', {
    tournamentId: 'manual-start-test', name: 'Manual Start Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'manual-start-test');
  for (let index = 1; index <= 4; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `PARTICIPANT-${index}`, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  throwsCode(() => app.registerParticipant(lateSession.sessionToken, 'manual-start-test'), 'ROSTER_FROZEN');
  app.close();
});

test('blocks a manual start with fewer than two eligible entries', () => {
  const app = service();
  addParticipants(app, 1);
  app.createTournament('admin-test-token', {
    tournamentId: 'blocked-start-test', name: 'Blocked Start Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'blocked-start-test');
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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

test('recovers a start-blocked tournament by reopening registration and closing check-in', () => {
  let now = 100;
  const app = service({ now: () => now });
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'recover-blocked', name: 'Recover Blocked',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    requireCheckIn: true, autoStart: true,
    registrationOpenAt: 100, registrationCloseAt: 200, checkInOpenAt: 200,
    checkInCloseAt: 300, startAt: 400,
  });
  app.openRegistration('admin-test-token', 'recover-blocked');
  const firstSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.registerParticipant(firstSession.sessionToken, 'recover-blocked');
  app.closeRegistration('admin-test-token', 'recover-blocked');
  app.checkInParticipant(firstSession.sessionToken, 'recover-blocked');
  app.closeCheckIn('admin-test-token', 'recover-blocked');
  assert.equal(app.startTournament('admin-test-token', 'recover-blocked').status, 'START_BLOCKED');

  const reopened: any = app.reopenRegistration('admin-test-token', 'recover-blocked');
  assert.equal(reopened.status, 'REGISTRATION_OPEN');
  assert.equal(reopened.schedule.autoStart, false);
  assert.equal(reopened.schedule.registrationCloseAt, null);
  assert.equal(reopened.schedule.checkInCloseAt, null);
  assert.equal(reopened.schedule.startAt, null);
  const secondSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-2', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.registerParticipant(secondSession.sessionToken, 'recover-blocked');
  app.closeRegistration('admin-test-token', 'recover-blocked');
  app.checkInParticipant(firstSession.sessionToken, 'recover-blocked');
  app.checkInParticipant(secondSession.sessionToken, 'recover-blocked');
  app.closeCheckIn('admin-test-token', 'recover-blocked');
  const started: any = app.startTournament('admin-test-token', 'recover-blocked');
  assert.equal(started.status, 'RUNNING');
  assert.equal(started.bracket.matches.length, 1);
  app.close();
});

test('organizers can renew open match codes but cannot extend an assigned room deadline', () => {
  let now = 100;
  const app = service({ now: () => now });
  const seeded = tournament(app, 2, 'renew-expired');
  const match = seeded.matches.find((entry: any) => entry.status === 'OPEN') as any;
  assert.ok(match);
  const oldCodeHash = app.db.prepare('SELECT match_code_hash FROM matches WHERE match_id = ?')
    .get(match.matchId).match_code_hash;
  app.db.prepare('UPDATE matches SET match_code_expires_at = ? WHERE match_id = ?').run(now, match.matchId);

  assert.equal(app.extendExpiredMatches('admin-test-token', 'renew-expired').extended, 1);
  const reopened: any = app.db.prepare('SELECT * FROM matches WHERE match_id = ?').get(match.matchId);
  assert.equal(reopened.status, 'OPEN');
  assert.notEqual(reopened.match_code_hash, oldCodeHash);
  assert.ok(reopened.match_code_expires_at > now);

  const firstSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const secondSession = app.createParticipantSession({
    participantCode: 'PARTICIPANT-2', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const firstJoin = app.joinAssignedMatch({
    sessionToken: firstSession.sessionToken, matchId: match.matchId,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.joinAssignedMatch({
    sessionToken: secondSession.sessionToken, matchId: match.matchId,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.db.prepare('UPDATE matches SET match_code_expires_at = ? WHERE match_id = ?').run(now - 1, match.matchId);
  app.db.prepare('UPDATE room_slots SET expires_at = ? WHERE match_id = ?').run(now - 1, match.matchId);
  assert.equal(app.extendExpiredMatches('admin-test-token', 'renew-expired').extended, 0);
  const active: any = app.db.prepare('SELECT * FROM matches WHERE match_id = ?').get(match.matchId);
  const room: any = app.db.prepare('SELECT * FROM room_slots WHERE match_id = ?').get(match.matchId);
  assert.equal(active.status, 'ASSIGNED');
  assert.equal(active.room_slot, firstJoin.roomSlot);
  assert.equal(room.state, 'ASSIGNED');
  assert.equal(active.match_code_expires_at, now - 1);
  assert.equal(room.expires_at, now - 1);
  app.close();
});

test('a match timer starts when its room is assigned, not when the bracket is seeded', () => {
  let now = 1_700_000_000;
  const app = service({ now: () => now });
  addParticipants(app, 2);
  app.createTournament('admin-test-token', {
    tournamentId: 'assignment-starts-clock', name: 'Assignment Starts Clock',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, matchTimeoutSeconds: 1200,
  });
  const bracket = app.seedBracket('admin-test-token', {
    tournamentId: 'assignment-starts-clock', participantIds: ['p-1', 'p-2'],
  });
  app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?")
    .run('assignment-starts-clock');
  const match: any = bracket.matches[0];
  const session = app.createParticipantSession({
    participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });

  now += 1300;
  app.joinAssignedMatch({
    sessionToken: session.sessionToken, matchId: match.matchId,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const row: any = app.db.prepare('SELECT status, match_code_expires_at FROM matches WHERE match_id = ?')
    .get(match.matchId);
  const room: any = app.db.prepare('SELECT state, assigned_at, expires_at FROM room_slots WHERE match_id = ?')
    .get(match.matchId);

  assert.equal(row.status, 'ASSIGNED');
  assert.equal(room.state, 'ASSIGNED');
  assert.equal(room.assigned_at, now);
  assert.equal(row.match_code_expires_at, now + 1200);
  assert.equal(room.expires_at, now + 1200);
  app.close();
});

test('an unfinished assigned match gets a server-random winner after its 20-minute deadline', () => {
  const previousClock = clock;
  const app = service();
  try {
    addParticipants(app, 2);
    app.createTournament('admin-test-token', {
      tournamentId: 'random-timeout', name: 'Random Timeout',
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, matchTimeoutSeconds: 1200,
    });
    const bracket = app.seedBracket('admin-test-token', {
      tournamentId: 'random-timeout', participantIds: ['p-1', 'p-2'],
    });
    app.db.prepare("UPDATE tournaments SET status = 'RUNNING' WHERE tournament_id = ?")
      .run('random-timeout');
    const match: any = bracket.matches[0];
    const session = app.createParticipantSession({
      participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.joinAssignedMatch({
      sessionToken: session.sessionToken, matchId: match.matchId,
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    const room: any = app.db.prepare('SELECT assigned_at FROM room_slots WHERE match_id = ?').get(match.matchId);

    clock = Number(room.assigned_at) + 1199;
    assert.deepEqual(app.processExpiredMatches(), []);
    clock += 1;
    assert.deepEqual(app.processExpiredMatches(), [match.matchId]);
    const result: any = app.db.prepare(`
      SELECT winner_id, loser_id, result_reason, status FROM matches WHERE match_id = ?
    `).get(match.matchId);
    assert.ok(['p-1', 'p-2'].includes(result.winner_id));
    assert.equal(result.loser_id, result.winner_id === 'p-1' ? 'p-2' : 'p-1');
    assert.equal(result.result_reason, 'TIMEOUT_RANDOM');
    assert.equal(result.status, 'COMPLETED');
    assert.equal(app.activeTournament(), null);
    assert.deepEqual(app.processExpiredMatches(), []);
  } finally {
    app.close();
    clock = previousClock;
  }
});

test('organizers can forfeit an expired match and advance the bracket', () => {
  let now = 100;
  const app = service({ now: () => now });
  const seeded = tournament(app, 2, 'forfeit-expired');
  const match = seeded.matches.find((entry: any) => entry.status === 'OPEN') as any;
  assert.ok(match);
  assert.throws(() => app.forfeitExpiredMatch('admin-test-token', match.matchId, 'B'),
    (error: any) => error?.code === 'MATCH_NOT_EXPIRED');
  app.db.prepare('UPDATE matches SET match_code_expires_at = ? WHERE match_id = ?').run(now, match.matchId);
  const result: any = app.forfeitExpiredMatch('admin-test-token', match.matchId, 'B');
  assert.equal(result.winnerParticipantId, 'p-2');
  assert.equal(result.loserParticipantId, 'p-1');
  assert.equal(result.reason, 'FORFEIT');
  assert.equal(app.publicBracket('forfeit-expired').matches[0].status, 'COMPLETED');
  assert.equal(app.activeTournament(), null);
  app.close();
});

test('shows the next assigned match and joins it without exposing a match code', () => {
  const app = service();
  addParticipants(app, 3);
  app.createTournament('admin-test-token', {
    tournamentId: 'assigned-join-test', name: 'Assigned Join Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'assigned-join-test');
  const sessions = [1, 2, 3].map((index) => app.createParticipantSession({
    participantCode: `PARTICIPANT-${index}`, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
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

test('a first-round bye does not hide the player’s next playable match', () => {
  const app = service();
  tournament(app, 3);
  const player = app.createParticipantSession({
    participantCode: 'PARTICIPANT-3', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const schedule: any = app.playerTournament(player.sessionToken, 'tournament-1');
  assert.equal(schedule.nextMatch.round, 2);
  assert.equal(schedule.nextMatch.status, 'PENDING');
  app.close();
});
