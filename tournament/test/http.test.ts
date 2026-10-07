import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { createTournamentHttpServer } from '../src/server.ts';
import { TournamentService } from '../src/service.ts';

test('advertises the v2.2.0 build ID when the tournament service uses defaults', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const address: any = server.address();
    const response = await fetch(`http://127.0.0.1:${address.port}/healthz`);
    const body = await response.json() as any;
    assert.equal(body.buildId, 'YIMO-Graphwar-2.2.0');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('rate limits distinct competitors by their forwarded client IP behind nginx', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    participantScryptCost: 256, rateLimitMax: 1,
  });
  app.addParticipant('admin-test-token', {
    participantId: 'proxy-p1', displayName: 'Proxy One', participantCode: 'PROXY-CODE-1',
  });
  app.addParticipant('admin-test-token', {
    participantId: 'proxy-p2', displayName: 'Proxy Two', participantCode: 'PROXY-CODE-2',
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  const base = `http://127.0.0.1:${address.port}`;
  try {
    const login = (participantCode: string, ip: string) => fetch(`${base}/api/v1/participant-sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ip },
      body: JSON.stringify({ participantCode, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2 }),
    });
    const first = await login('PROXY-CODE-1', '203.0.113.10');
    const second = await login('PROXY-CODE-2', '203.0.113.11');
    assert.equal(first.status, 200);
    assert.equal(second.status, 200, 'one competitor must not consume another competitor’s IP limit');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('returns the active public tournament without requiring a tournament ID', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const base = `http://127.0.0.1:${address.port}`;
    assert.equal(await fetch(`${base}/api/v1/tournaments/active`).then((response) => response.json()), null);
    app.createTournament('admin-test-token', {
      tournamentId: 'active-http', name: 'Open Cup', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.openRegistration('admin-test-token', 'active-http');
    const response = await fetch(`${base}/api/v1/tournaments/active`);
    const active = await response.json() as any;
    assert.equal(response.status, 200);
    assert.equal(active.tournamentId, 'active-http');
    assert.equal(active.status, 'REGISTRATION_OPEN');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('organizers can resume the current tournament without entering its ID', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
  });
  app.createTournament('admin-test-token', {
    tournamentId: 'admin-current', name: 'Current Cup', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/admin/tournament/current`, {
      headers: { Authorization: 'Bearer admin-test-token' },
    });
    const current = await response.json() as any;
    assert.equal(response.status, 200);
    assert.equal(current.tournamentId, 'admin-current');
    assert.equal(current.status, 'DRAFT');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('does not expose the legacy manual bracket-seed route', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    participantScryptCost: 256,
  });
  app.addParticipant('admin-test-token', {
    participantId: 'seed-route-p1', displayName: 'Player One', participantCode: 'SEED-ROUTE-1',
  });
  app.addParticipant('admin-test-token', {
    participantId: 'seed-route-p2', displayName: 'Player Two', participantCode: 'SEED-ROUTE-2',
  });
  app.createTournament('admin-test-token', {
    tournamentId: 'seed-route-test', name: 'Seed Route Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/admin/bracket/seed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer admin-test-token' },
      body: JSON.stringify({ tournamentId: 'seed-route-test', participantIds: ['seed-route-p1', 'seed-route-p2'] }),
    });
    assert.equal(response.status, 404);
    assert.equal(app.currentAdminTournament('admin-test-token')?.status, 'DRAFT');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('authorizes the desktop tournament-room flow from a participant code', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    participantScryptCost: 256, rateLimitMax: 100,
  });
  for (let index = 1; index <= 2; index += 1) {
    app.addParticipant('admin-test-token', {
      participantId: `desktop-${index}`, displayName: `Desktop ${index}`, participantCode: `DESKTOP-CODE-${index}`,
    });
  }
  app.createTournament('admin-test-token', {
    tournamentId: 'desktop-join', name: 'Desktop Join', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'desktop-join');
  for (let index = 1; index <= 2; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `DESKTOP-CODE-${index}`, displayName: `Official Desktop ${index}`,
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.registerParticipant(session.sessionToken, 'desktop-join');
  }
  app.closeRegistration('admin-test-token', 'desktop-join');
  app.startTournament('admin-test-token', 'desktop-join');
  const launchedRooms: Array<[string, number]> = [];
  const server = createTournamentHttpServer(app, (matchId, port) => { launchedRooms.push([matchId, port]); });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/game/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        participantCode: 'DESKTOP-CODE-1', displayName: 'Official Desktop 1', roomPort: '31000',
        buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: '2',
      }),
    });
    const body = await response.text();
    assert.equal(response.status, 200);
    assert.match(body, /^YIMO_ROOM&31000&[A-Za-z0-9_.-]+&[A-Za-z0-9_-]+$/);
    assert.equal(Buffer.from(body.split('&')[3], 'base64url').toString('utf8'), 'Official Desktop 1');
    assert.deepEqual(launchedRooms, [['desktop-join-r1-m1', 31000]]);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('serves health, admin, participant, match, room, and result routes', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0',
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
    assert.equal((health.body as any).buildId, 'YIMO-Graphwar-2.2.0');
    const home = await request('/', { redirect: 'manual' });
    assert.equal(home.response.status, 200);
    assert.match(home.body as string, /YIMO Graphwar/);
    assert.match(home.body as string, /href="\/participant"/);
    assert.match(home.body as string, /href="\/admin"/);
    assert.match(home.body as string, /https:\/\/github\.com\/goncalofrankefranco\/yimo-graphwar/);
    assert.match(home.body as string, /Powered by Cloudzy/);
    const adminPage = await request('/admin');
    assert.equal(adminPage.response.status, 200, 'the public admin page must not have a site-password gate');
    assert.match(adminPage.body as string, /YIMO Tournament Admin/);
    assert.match(adminPage.body as string, /X-YIMO-API-Authorization/);
    assert.doesNotMatch(adminPage.body as string, /id="tournamentId"/);
    assert.match(adminPage.body as string, /api\/v1\/tournaments\/active/);
    assert.match(adminPage.body as string, /input\{[^}]*width:100%/);
    assert.match(adminPage.body as string, /Powered by Cloudzy/);
    const participantPage = await request('/participant');
    assert.equal(participantPage.response.status, 200, 'the competitor page must not have a site-password gate');
    assert.match(participantPage.body as string, /YIMO Tournament/);
    assert.match(participantPage.body as string, /Authorization:'Bearer '\+sessionToken/);
    assert.match(participantPage.body as string, /Powered by Cloudzy/);
    assert.match(participantPage.body as string, /Candidate code/);
    assert.match(participantPage.body as string, /id="primaryAction"/);
    assert.match(participantPage.body as string, /Join Room/);
    assert.match(participantPage.body as string, /window\.setInterval\(\(\)=>refresh\(false\),7000\)/);
    assert.doesNotMatch(participantPage.body as string, /Local player ID|Generate player ID|id="tournament"/);

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
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    }, adminHeaders);
    assert.equal(created.response.status, 201);

    const opened = await post('/api/v1/admin/tournaments/http-test/registration/open', {}, adminHeaders);
    assert.equal(opened.response.status, 200);
    const session = await post('/api/v1/participant-sessions', {
      participantCode: 'PARTICIPANT-1', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    const secondSession = await post('/api/v1/participant-sessions', {
      participantCode: 'PARTICIPANT-2', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    assert.equal(session.response.status, 200);
    assert.equal(secondSession.response.status, 200);
    const registrationOne = await post('/api/v1/tournaments/http-test/register', {}, {
      Authorization: `Bearer ${(session.body as any).sessionToken}`,
    });
    const registrationTwo = await post('/api/v1/tournaments/http-test/register', {}, {
      Authorization: `Bearer ${(secondSession.body as any).sessionToken}`,
    });
    assert.equal(registrationOne.response.status, 200);
    assert.equal(registrationTwo.response.status, 200);
    const closed = await post('/api/v1/admin/tournaments/http-test/registration/close', {}, adminHeaders);
    assert.equal(closed.response.status, 200);
    const started = await post('/api/v1/admin/tournaments/http-test/start', {}, adminHeaders);
    assert.equal(started.response.status, 200);
    assert.equal((started.body as any).status, 'RUNNING');
    const openMatch: any = (started.body as any).bracket.matches.find((match: any) => match.status === 'OPEN');
    assert.ok(openMatch?.matchId);

    const bracket = await request('/api/v1/tournaments/http-test/bracket');
    assert.equal(bracket.response.status, 200);
    assert.equal((bracket.body as any).matches[0].playerA, 'Player 1');
    assert.equal((bracket.body as any).matches[0].playerB, 'Player 2');
    assert.ok(!(Object.prototype.hasOwnProperty.call((bracket.body as any).matches[0], 'matchCode')));

    const joined = await post(`/api/v1/matches/${openMatch.matchId}/join-assigned`, {
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    }, { Authorization: `Bearer ${(session.body as any).sessionToken}` });
    assert.equal(joined.response.status, 200);
    assert.equal((joined.body as any).roomSlot, 31000);

    const matches = await request('/api/v1/player/matches', {
      headers: { Authorization: `Bearer ${(session.body as any).sessionToken}` },
    });
    assert.equal(matches.response.status, 200);
    assert.equal((matches.body as any).matches.length, 1);
    const heartbeat = await post('/api/v1/rooms/heartbeat', {
      roomToken: (joined.body as any).roomToken, state: 'IN_PROGRESS',
    });
    assert.equal(heartbeat.response.status, 200);
    const nonce = 'room-result-nonce-http';
    const result = await post(`/api/v1/matches/${openMatch.matchId}/result`, {
      winnerParticipantId: 'p-1', loserParticipantId: 'p-2', reason: 'NORMAL',
      serverNonce: nonce,
      serverSignature: createHmac('sha256', 'room-test-secret')
        .update(`${openMatch.matchId}|p-1|p-2|NORMAL|${nonce}`, 'utf8').digest('base64url'),
    });
    assert.equal(result.response.status, 200);
    assert.equal((result.body as any).duplicate, false);
    assert.equal((await request('/api/v1/tournaments/http-test/bracket')).body.matches[0].status, 'COMPLETED');
    assert.equal(await request('/api/v1/tournaments/active').then((entry) => entry.body), null);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('organizers can release a room after its result is recorded', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, participantScryptCost: 256,
  });
  for (let index = 1; index <= 2; index += 1) {
    app.addParticipant('admin-test-token', {
      participantId: `release-${index}`, displayName: `Release ${index}`, participantCode: `RELEASE-${index}`,
    });
  }
  app.createTournament('admin-test-token', {
    tournamentId: 'release-room-test', name: 'Release Room Test',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'release-room-test');
  const sessions = [];
  for (let index = 1; index <= 2; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `RELEASE-${index}`, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    sessions.push(session);
    app.registerParticipant(session.sessionToken, 'release-room-test');
  }
  app.closeRegistration('admin-test-token', 'release-room-test');
  app.startTournament('admin-test-token', 'release-room-test');
  const matchId = app.publicBracket('release-room-test').matches[0].matchId as string;
  const join = app.joinAssignedMatch({
    sessionToken: sessions[0].sessionToken, matchId,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.heartbeat({ roomToken: join.roomToken, state: 'IN_PROGRESS' });
  assert.throws(() => app.confirmCompletedMatch('admin-test-token', matchId),
    (error: any) => error?.code === 'MATCH_NOT_COMPLETED');
  const nonce = 'room-result-release-test';
  app.submitResult({
    matchId, winnerParticipantId: 'release-1', loserParticipantId: 'release-2', reason: 'NORMAL',
    serverNonce: nonce,
    serverSignature: createHmac('sha256', 'room-test-secret')
      .update(`${matchId}|release-1|release-2|NORMAL|${nonce}`, 'utf8').digest('base64url'),
  });

  const released: string[] = [];
  const server = createTournamentHttpServer(app, () => {}, async (id) => { released.push(id); return true; });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/admin/matches/${matchId}/release-room`, {
      method: 'POST', headers: { Authorization: 'Bearer admin-test-token' },
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json() as any).released, true);
    assert.deepEqual(released, [matchId]);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('organizer forfeit records a winner and releases its expired room', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, participantScryptCost: 256,
  });
  for (let index = 1; index <= 2; index += 1) {
    app.addParticipant('admin-test-token', {
      participantId: `forfeit-${index}`, displayName: `Forfeit ${index}`, participantCode: `FORFEIT-${index}`,
    });
  }
  app.createTournament('admin-test-token', {
    tournamentId: 'forfeit-http', name: 'Forfeit HTTP',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'forfeit-http');
  const sessions = [];
  for (let index = 1; index <= 2; index += 1) {
    const session = app.createParticipantSession({
      participantCode: `FORFEIT-${index}`, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    sessions.push(session);
    app.registerParticipant(session.sessionToken, 'forfeit-http');
  }
  app.closeRegistration('admin-test-token', 'forfeit-http');
  app.startTournament('admin-test-token', 'forfeit-http');
  const matchId = app.publicBracket('forfeit-http').matches[0].matchId as string;
  app.joinAssignedMatch({
    sessionToken: sessions[0].sessionToken, matchId,
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  const now = Math.floor(Date.now() / 1000);
  app.db.prepare('UPDATE matches SET match_code_expires_at = ? WHERE match_id = ?').run(now - 1, matchId);
  app.db.prepare('UPDATE room_slots SET expires_at = ? WHERE match_id = ?').run(now - 1, matchId);
  const released: string[] = [];
  const server = createTournamentHttpServer(app, () => {}, async (id) => { released.push(id); return true; });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/admin/matches/${matchId}/forfeit-expired`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer admin-test-token' },
      body: JSON.stringify({ winnerSide: 'B' }),
    });
    const result = await response.json() as any;
    assert.equal(response.status, 200);
    assert.equal(result.winnerParticipantId, 'forfeit-2');
    assert.equal(result.reason, 'FORFEIT');
    assert.equal(result.roomReleased, true);
    assert.deepEqual(released, [matchId]);
    assert.equal(app.publicBracket('forfeit-http').matches[0].status, 'COMPLETED');
    assert.equal(app.activeTournament(), null);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('serves authenticated admin lifecycle controls and status projections', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0',
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
    method: 'POST', headers, body: JSON.stringify(body),
  });
  const adminHeaders = { Authorization: 'Bearer admin-test-token' };

  try {
    const page = await request('/admin');
    assert.equal(page.response.status, 200);
    assert.match(page.body as string, /Start tournament/);
    assert.match(page.body as string, /Open registration/);
    assert.match(page.body as string, /data-action="registration\/reopen"/);
    assert.match(page.body as string, /data-action="check-in\/close"/);
    assert.match(page.body as string, /data-action="matches\/extend-expired"/);
    assert.match(page.body as string, /forfeit-expired/);
    assert.match(page.body as string, /Confirm the opponent is a no-show/);
    assert.match(page.body as string, /registrationOpenAt/);
    assert.match(page.body as string, /grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
    assert.match(page.body as string, /\.form-grid \.field\{min-width:0\}/);

    const created = await post('/api/v1/admin/tournaments', {
      tournamentId: 'admin-lifecycle', name: 'Admin Lifecycle',
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2, requireCheckIn: true,
    }, adminHeaders);
    assert.equal(created.response.status, 201);
    const unauthorized = await request('/api/v1/admin/tournaments/admin-lifecycle', {
      headers: { Authorization: 'Bearer wrong-token' },
    });
    assert.equal(unauthorized.response.status, 401);
    const opened = await post('/api/v1/admin/tournaments/admin-lifecycle/registration/open', {}, adminHeaders);
    assert.equal(opened.response.status, 200);
    assert.equal((opened.body as any).status, 'REGISTRATION_OPEN');
    const closed = await post('/api/v1/admin/tournaments/admin-lifecycle/registration/close', {}, adminHeaders);
    assert.equal(closed.response.status, 200);
    assert.equal((closed.body as any).status, 'CHECK_IN');
    const checkInClosed = await post('/api/v1/admin/tournaments/admin-lifecycle/check-in/close', {}, adminHeaders);
    assert.equal(checkInClosed.response.status, 200);
    assert.equal((checkInClosed.body as any).status, 'READY');
    const started = await post('/api/v1/admin/tournaments/admin-lifecycle/start', {}, adminHeaders);
    assert.equal(started.response.status, 200);
    assert.equal((started.body as any).status, 'START_BLOCKED');
    const invalidExtension = await post('/api/v1/admin/tournaments/admin-lifecycle/matches/extend-expired', {}, adminHeaders);
    assert.equal(invalidExtension.response.status, 409);
    assert.equal((invalidExtension.body as any).error, 'TOURNAMENT_NOT_RUNNING');
    const details = await request('/api/v1/admin/tournaments/admin-lifecycle', { headers: adminHeaders });
    assert.equal(details.response.status, 200);
    assert.equal((details.body as any).counts.total, 0);
    const reopened = await post('/api/v1/admin/tournaments/admin-lifecycle/registration/reopen', {}, adminHeaders);
    assert.equal(reopened.response.status, 200);
    assert.equal((reopened.body as any).status, 'REGISTRATION_OPEN');
    assert.equal((reopened.body as any).schedule.autoStart, false);
    assert.equal((reopened.body as any).schedule.startAt, null);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('does not expose the retired local-player-ID self-registration route', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0',
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
    const body = await response.json();
    return { response, body };
  };
  const post = (path: string, body: unknown, headers: Record<string, string> = {}) => request(path, {
    method: 'POST', headers, body: JSON.stringify(body),
  });
  const admin = { Authorization: 'Bearer admin-test-token' };
  try {
    await post('/api/v1/admin/tournaments', {
      tournamentId: 'self-http', name: 'Self HTTP', buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    }, admin);
    await post('/api/v1/admin/tournaments/self-http/registration/open', {}, admin);
    const response = await post('/api/v1/tournaments/self-http/self-register', {
      playerId: 'yimo-local-http-1234567890', displayName: 'HTTP Player',
      buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    assert.equal(response.response.status, 404);
    assert.equal((response.body as any).error, 'NOT_FOUND');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('accepts the organizer bearer token in the app-specific authorization header', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
    now: () => 1_700_000_000,
    participantScryptCost: 256,
    rateLimitMax: 1000,
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  const base = `http://127.0.0.1:${address.port}`;
  try {
    const created = await fetch(`${base}/api/v1/admin/tournaments`, {
      method: 'POST',
      headers: { Authorization: 'Bearer admin-test-token', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tournamentId: 'separate-auth-header', name: 'Separate Auth Header',
        buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
      }),
    });
    assert.equal(created.status, 201);
    const details = await fetch(`${base}/api/v1/admin/tournaments/separate-auth-header`, {
      headers: {
        Authorization: 'Basic eWltbzpzaXRlLXBhc3M=',
        'X-YIMO-API-Authorization': 'Bearer admin-test-token',
      },
    });
    assert.equal(details.status, 200);
    assert.equal((await details.json() as any).tournamentId, 'separate-auth-header');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('organizer can delete a non-running tournament through the authenticated admin route', async () => {
  const app = new TournamentService({
    dbPath: ':memory:',
    adminToken: 'admin-test-token',
    roomSecret: 'room-test-secret',
    buildId: 'YIMO-Graphwar-2.2.0',
    protocolVersion: 2,
    now: () => 1_700_000_000,
    participantScryptCost: 256,
    rateLimitMax: 1000,
  });
  const server = createTournamentHttpServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  const base = `http://127.0.0.1:${address.port}`;
  const admin = { Authorization: 'Bearer admin-test-token' };
  try {
    const page = await fetch(`${base}/admin`);
    assert.match(await page.text(), /id="deleteTournament"/);
    const created = await fetch(`${base}/api/v1/admin/tournaments`, {
      method: 'POST', headers: { ...admin, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tournamentId: 'delete-via-http', name: 'Delete via HTTP',
        buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
      }),
    });
    assert.equal(created.status, 201);

    const unauthorized = await fetch(`${base}/api/v1/admin/tournaments/delete-via-http`, { method: 'DELETE' });
    assert.equal(unauthorized.status, 401);

    const deleted = await fetch(`${base}/api/v1/admin/tournaments/delete-via-http`, {
      method: 'DELETE', headers: admin,
    });
    assert.equal(deleted.status, 200);
    assert.deepEqual(await deleted.json(), { tournamentId: 'delete-via-http', deleted: true });
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});

test('deleting a running tournament stops its active room before confirming deletion', async () => {
  const app = new TournamentService({
    dbPath: ':memory:', adminToken: 'admin-test-token', roomSecret: 'room-test-secret',
    participantScryptCost: 256, rateLimitMax: 1000,
  });
  app.addParticipant('admin-test-token', {
    participantId: 'running-delete-p1', displayName: 'Player One', participantCode: 'RUNNING-DELETE-1',
  });
  app.addParticipant('admin-test-token', {
    participantId: 'running-delete-p2', displayName: 'Player Two', participantCode: 'RUNNING-DELETE-2',
  });
  app.createTournament('admin-test-token', {
    tournamentId: 'running-delete-http', name: 'Running Delete HTTP',
    buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
  });
  app.openRegistration('admin-test-token', 'running-delete-http');
  for (const [participantCode, participantId] of [
    ['RUNNING-DELETE-1', 'running-delete-p1'],
    ['RUNNING-DELETE-2', 'running-delete-p2'],
  ]) {
    const session = app.createParticipantSession({
      participantCode, buildId: 'YIMO-Graphwar-2.2.0', protocolVersion: 2,
    });
    app.registerParticipant(session.sessionToken, 'running-delete-http');
  }
  app.closeRegistration('admin-test-token', 'running-delete-http');
  app.startTournament('admin-test-token', 'running-delete-http');
  const match: any = app.db.prepare(
    "SELECT match_id FROM matches WHERE tournament_id = ? AND status = 'OPEN'",
  ).get('running-delete-http');
  app.db.prepare("UPDATE room_slots SET state = 'IN_PROGRESS', match_id = ? WHERE tournament_id = ? AND room_slot = 31000")
    .run(match.match_id, 'running-delete-http');

  const released: string[] = [];
  let roomShutdownConfirmed = false;
  const server = createTournamentHttpServer(app, () => {}, async (matchId) => {
    released.push(matchId);
    await new Promise((resolve) => setTimeout(resolve, 25));
    roomShutdownConfirmed = true;
    return true;
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address: any = server.address();
  const base = `http://127.0.0.1:${address.port}`;
  const admin = { Authorization: 'Bearer admin-test-token' };
  try {
    const page = await fetch(`${base}/admin`);
    const html = await page.text();
    assert.match(html, />Delete tournament</);
    assert.match(html, /any active matches will end and their rooms will be stopped/i);

    const unauthorized = await fetch(`${base}/api/v1/admin/tournaments/running-delete-http`, { method: 'DELETE' });
    assert.equal(unauthorized.status, 401);
    assert.deepEqual(released, []);

    const deleted = await fetch(`${base}/api/v1/admin/tournaments/running-delete-http`, {
      method: 'DELETE', headers: admin,
    });
    assert.equal(deleted.status, 200);
    assert.deepEqual(await deleted.json(), {
      tournamentId: 'running-delete-http', deleted: true, roomsStopped: 1, roomStopFailures: 0,
    });
    assert.deepEqual(released, [match.match_id]);
    assert.equal(roomShutdownConfirmed, true);
    assert.equal(app.db.prepare('SELECT COUNT(*) AS count FROM tournaments WHERE tournament_id = ?')
      .get('running-delete-http').count, 0);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    app.close();
  }
});
