import test from 'node:test';
import assert from 'node:assert/strict';
import { createTournamentHttpServer } from '../src/server.ts';
import { createDemoService, DEMO_TOURNAMENT_ID } from '../src/demo.ts';

test('scheduled demo advances through registration and starts a public bracket', () => {
  const demo = createDemoService(100);
  assert.equal(demo.service.publicBracket(DEMO_TOURNAMENT_ID).status, 'DRAFT');
  assert.equal(demo.advanceTo(110), 1);
  for (const code of demo.participantCodes) {
    const session = demo.service.createParticipantSession({
      participantCode: code, buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    });
    demo.service.registerParticipant(session.sessionToken, DEMO_TOURNAMENT_ID);
  }
  assert.equal(demo.advanceTo(120), 1);
  assert.equal(demo.advanceTo(130), 1);
  assert.equal(demo.advanceTo(130), 0);
  const bracket: any = demo.service.publicBracket(DEMO_TOURNAMENT_ID);
  assert.equal(bracket.status, 'RUNNING');
  assert.equal(bracket.matches.length, 3);
  assert.deepEqual([...new Set(bracket.matches.map((match: any) => match.round))], [1, 2]);
  assert.ok(bracket.matches.every((match: any) => !('matchCode' in match)));
  demo.service.close();
});

test('scheduled demo supports HTTP registration, assigned join, and result advancement', async () => {
  const demo = createDemoService(100);
  const server = createTournamentHttpServer(demo.service);
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
  try {
    assert.equal((await request(`/participant?tournament=${DEMO_TOURNAMENT_ID}`)).response.status, 200);
    demo.advanceTo(110);
    const sessions: any[] = [];
    for (const code of demo.participantCodes) {
      const session = await post('/api/v1/participant-sessions', {
        participantCode: code, buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
      });
      assert.equal(session.response.status, 200);
      sessions.push(session.body);
      const registered = await post(`/api/v1/tournaments/${DEMO_TOURNAMENT_ID}/register`, {}, {
        Authorization: `Bearer ${session.body.sessionToken}`,
      });
      assert.equal(registered.response.status, 200);
    }
    demo.advanceTo(130);
    const schedule = await request(`/api/v1/player/tournaments/${DEMO_TOURNAMENT_ID}`, {
      headers: { Authorization: `Bearer ${sessions[0].sessionToken}` },
    });
    assert.equal(schedule.response.status, 200);
    const matchId = (schedule.body as any).nextMatch.matchId;
    const joined = await post(`/api/v1/matches/${matchId}/join-assigned`, {
      buildId: 'YIMO-Graphwar-2.0.0', protocolVersion: 2,
    }, { Authorization: `Bearer ${sessions[0].sessionToken}` });
    assert.equal(joined.response.status, 200);
    const result = await post(`/api/v1/matches/${matchId}/result`, {
      winnerParticipantId: 'demo-1', loserParticipantId: 'demo-2', reason: 'DEMO',
      roomToken: joined.body.roomToken,
    });
    assert.equal(result.response.status, 200);
    const after = await request(`/api/v1/player/tournaments/${DEMO_TOURNAMENT_ID}`, {
      headers: { Authorization: `Bearer ${sessions[0].sessionToken}` },
    });
    assert.equal(after.response.status, 200);
    assert.equal((after.body as any).nextMatch.status, 'PENDING');
    assert.ok(!(JSON.stringify(after.body).includes('roomToken')));
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    demo.service.close();
  }
});
