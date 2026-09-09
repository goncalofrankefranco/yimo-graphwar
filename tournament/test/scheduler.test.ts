import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { TournamentService } from '../src/service.ts';

const options = (now: () => number, dbPath = ':memory:') => ({
  dbPath,
  adminToken: 'admin-test-token',
  roomSecret: 'room-test-secret',
  buildId: 'YIMO-Graphwar-2.0.0',
  protocolVersion: 2,
  now,
  participantScryptCost: 256,
  rateLimitMax: 1000,
});

function addParticipants(app: TournamentService, count: number) {
  for (let index = 1; index <= count; index += 1) {
    app.addParticipant('admin-test-token', {
      participantId: `scheduled-${index}`,
      displayName: `Scheduled Player ${index}`,
      participantCode: `SCHEDULED-${index}`,
    });
  }
}

function registerAll(app: TournamentService, count: number, tournamentId = 'scheduled-test') {
  for (let index = 1; index <= count; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `SCHEDULED-${index}`,
      buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    });
    app.registerParticipant(session.sessionToken, tournamentId);
  }
}

test('processes scheduled boundaries idempotently and starts the bracket', () => {
  let now = 100;
  const app = new TournamentService(options(() => now));
  addParticipants(app, 4);
  app.createTournament('admin-test-token', {
    tournamentId: 'scheduled-test', name: 'Scheduled Test',
    buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    registrationOpenAt: 100, registrationCloseAt: 200, startAt: 300, autoStart: true,
  });
  assert.equal(app.processScheduledEvents(), 1);
  assert.equal(app.processScheduledEvents(), 0);
  registerAll(app, 4);
  now = 200;
  assert.equal(app.processScheduledEvents(), 1);
  assert.equal(app.processScheduledEvents(), 0);
  now = 300;
  assert.equal(app.processScheduledEvents(), 1);
  assert.equal(app.processScheduledEvents(), 0);
  const bracket: any = app.publicBracket('scheduled-test');
  assert.equal(bracket.status, 'RUNNING');
  assert.equal(bracket.matches.length, 3);
  app.close();
});

test('recovers a started tournament after service restart without duplicating matches', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'yimo-scheduler-'));
  const dbPath = join(directory, 'tournament.sqlite');
  let now = 100;
  try {
    const first = new TournamentService(options(() => now, dbPath));
    addParticipants(first, 2);
    first.createTournament('admin-test-token', {
      tournamentId: 'restart-test', name: 'Restart Test',
      buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
      registrationOpenAt: 100, registrationCloseAt: 200, startAt: 300, autoStart: true,
    });
    first.processScheduledEvents();
    registerAll(first, 2, 'restart-test');
    now = 300;
    assert.equal(first.processScheduledEvents(), 2);
    assert.equal(first.publicBracket('restart-test').matches.length, 1);
    first.close();

    const second = new TournamentService(options(() => now, dbPath));
    assert.equal(second.processScheduledEvents(), 0);
    assert.equal(second.publicBracket('restart-test').matches.length, 1);
    second.close();
  } finally {
    await new Promise((resolve) => setTimeout(resolve, 50));
    try {
      rmSync(directory, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
    } catch {
      // Windows can release SQLite WAL handles just after DatabaseSync.close().
    }
  }
});
