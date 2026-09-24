import test from 'node:test';
import assert from 'node:assert/strict';
import { TournamentService } from '../src/service.ts';

test('self-registers a locally generated player identity without an organizer code', () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
    now: () => 1_700_000_000,
    participantScryptCost: 256,
    rateLimitMax: 1000,
  });
  app.createTournament('admin-test-token', {
    tournamentId: 'self-register-test',
    name: 'Self Register Test',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'self-register-test');

  const first: any = app.selfRegisterParticipant('self-register-test', {
    playerId: 'yimo-local-player-1234567890',
    displayName: 'Local Player',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  assert.equal(first.participantId, 'yimo-local-player-1234567890');
  assert.equal(first.entry.entryStatus, 'REGISTERED');
  assert.ok(first.sessionToken);
  assert.equal(app.countParticipants(), 1);

  const second: any = app.selfRegisterParticipant('self-register-test', {
    playerId: 'yimo-local-player-1234567890',
    displayName: 'Local Player',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
  });
  assert.equal(second.participantId, first.participantId);
  assert.equal(app.countParticipants(), 1);
  app.close();
});
