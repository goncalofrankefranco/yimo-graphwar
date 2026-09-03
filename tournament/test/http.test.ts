import test from 'node:test';
import assert from 'node:assert/strict';
import { createTournamentHttpServer } from '../src/server.ts';
import { TournamentService } from '../src/service.ts';

test('serves health, admin, participant, match, room, and result routes', async () => {
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
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  const base = `http://127.0.0.1:${address.port}`;

  const request = async (path: string, init: any = {}) => {
    const response = await fetch(`${base}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    });
    const contentType = response.headers.get('content-type') ?? '';
    const body = contentType.includes('json') ? await response.json() : await response.text();
    return { response, body };
  };
  const post = (path: string, body: unknown, headers: Record<string, string> = {}) => request(path, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });

  try {
    const health = await request('/healthz');
    assert.equal(health.response.status, 200);
    assert.equal((health.body as any).buildId, 'YIMO-Graphwar-2.0.0');
    assert.match(await (await request('/admin')).body as string, /YIMO Tournament Admin/);
    assert.match(await (await request('/participant')).body as string, /YIMO Tournament/);

    const adminHeaders = { Authorization: 'Bearer admin-test-token' };
    const participantOne = await post('/api/v1/admin/participants', {
      participantId: 'p-1', displayName: 'Player 1', participantCode: 'PARTICIPANT-1',
    }, adminHeaders);
    const participantTwo = await post('/api/v1/admin/participants', {
      participantId: 'p-2', displayName: 'Player 2', participantCode: 'PARTICIPANT-2',
    }, adminHeaders);
    assert.equal(participantOne.response.status, 201);
    assert.equal(participantTwo.response.status, 201);

    const created = await post('/api/v1/admin/tournaments', {
      tournamentId: 'http-test', name: 'HTTP Test Cup',
      buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    }, adminHeaders);
    assert.equal(created.response.status, 201);
    const seeded = await post('/api/v1/admin/bracket/seed', {
      tournamentId: 'http-test', participantIds: ['p-1', 'p-2'],
    }, adminHeaders);
    assert.equal(seeded.response.status, 201);
    const openMatch: any = (seeded.body as any).matches.find((match: any) => match.status === 'OPEN');
    assert.ok(openMatch?.matchCode);

    const session = await post('/api/v1/participant-sessions', {
      participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    });
    assert.equal(session.response.status, 200);
    const joined = await post('/api/v1/matches/join', {
      sessionToken: (session.body as any).sessionToken, matchCode: openMatch.matchCode,
      buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    });
    assert.equal(joined.response.status, 200);
    assert.equal((joined.body as any).roomSlot, 30000);

    const matches = await request('/api/v1/player/matches', {
      headers: { Authorization: `Bearer ${(session.body as any).sessionToken}` },
    });
    assert.equal(matches.response.status, 200);
    assert.equal((matches.body as any).matches.length, 1);
    const heartbeat = await post('/api/v1/rooms/heartbeat', {
      roomToken: (joined.body as any).roomToken, state: 'IN_PROGRESS',
    });
    assert.equal(heartbeat.response.status, 200);
    const result = await post(`/api/v1/matches/${openMatch.matchId}/result`, {
      winnerParticipantId: 'p-1', loserParticipantId: 'p-2', reason: 'NORMAL',
      roomToken: (joined.body as any).roomToken,
    });
    assert.equal(result.response.status, 200);
    assert.equal((result.body as any).duplicate, false);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});
