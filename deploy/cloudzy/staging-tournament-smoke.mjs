import { readFileSync } from 'node:fs';

const base = process.env.YIMO_STAGING_BASE ?? 'http://127.0.0.1';
const admin = readFileSync('/root/yimo-admin-token.txt', 'utf8').trim();
const buildId = 'YIMO-Graphwar-2.0.0';
const protocolVersion = 2;
const adminHeaders = { Authorization: `Bearer ${admin}` };
const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function request(path, options = {}) {
  const response = await fetch(base + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
  });
  const raw = await response.text();
  let body = {};
  try {
    body = raw ? JSON.parse(raw) : {};
  } catch {
    body = { message: raw };
  }
  if (!response.ok) throw new Error(`${path} -> ${response.status} ${body.message ?? body.error ?? raw}`);
  return body;
}

async function post(path, body, headers = {}) {
  return request(path, { method: 'POST', body: JSON.stringify(body), headers });
}

const stamp = Date.now();
const tournamentId = `stage8-${stamp}`;
const participants = [];
for (let index = 1; index <= 4; index += 1) {
  const participantId = `s8p-${stamp}-${index}`;
  const participantCode = `S8-CODE-${stamp}-${index}`;
  await post('/api/v1/admin/participants', {
    participantId,
    displayName: `Stage 8 Player ${index}`,
    participantCode,
  }, adminHeaders);
  participants.push({ participantId, participantCode });
}

const now = Math.floor(Date.now() / 1000);
await post('/api/v1/admin/tournaments', {
  tournamentId,
  name: 'Stage 8 Disposable Cup',
  buildId,
  protocolVersion,
  matchTimeoutSeconds: 60,
  registrationOpenAt: now + 2,
  registrationCloseAt: now + 6,
  startAt: now + 11,
  autoStart: true,
}, adminHeaders);
await post(`/api/v1/admin/tournaments/${tournamentId}/registration/open`, {}, adminHeaders);

await sleep(1000);
const sessions = [];
for (const participant of participants) {
  const session = await post('/api/v1/participant-sessions', {
    participantCode: participant.participantCode,
    buildId,
    protocolVersion,
  });
  await post(`/api/v1/tournaments/${tournamentId}/register`, {}, {
    Authorization: `Bearer ${session.sessionToken}`,
  });
  sessions.push({ participantId: participant.participantId, sessionToken: session.sessionToken });
}

await sleep(16000);
const schedule = await request(`/api/v1/player/tournaments/${tournamentId}`, {
  headers: { Authorization: `Bearer ${sessions[0].sessionToken}` },
});
if (schedule.status !== 'RUNNING' || !schedule.nextMatch) throw new Error('scheduled start did not produce a next match');
const matchId = schedule.nextMatch.matchId;
const joined = await post(`/api/v1/matches/${matchId}/join-assigned`, {
  buildId,
  protocolVersion,
}, { Authorization: `Bearer ${sessions[0].sessionToken}` });
await post(`/api/v1/matches/${matchId}/result`, {
  winnerParticipantId: sessions[0].participantId,
  loserParticipantId: sessions[1].participantId,
  reason: 'STAGE8',
  roomToken: joined.roomToken,
});
const bracket = await request(`/api/v1/tournaments/${tournamentId}/bracket`);
if (!bracket.matches.some((match) => match.matchId === matchId && match.status === 'COMPLETED')) {
  throw new Error('result did not advance the public bracket');
}
console.log('staging-tournament-check: PASS');
console.log('staging-match-result: PASS');
