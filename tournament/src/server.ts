import { createServer } from 'node:http';
import { ADMIN_PAGE, PARTICIPANT_PAGE } from './pages.ts';
import { ServiceError, TournamentService } from './service.ts';

function send(response: any, status: number, body: unknown, contentType = 'application/json; charset=utf-8'): void {
  response.writeHead(status, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  response.end(contentType.startsWith('application/json') ? JSON.stringify(body) : String(body));
}

function bearer(request: any): string | undefined {
  const value = request.headers.authorization;
  return typeof value === 'string' && value.startsWith('Bearer ') ? value.slice(7) : undefined;
}

function readJson(request: any): Promise<any> {
  return new Promise((resolve, reject) => {
    let data = '';
    request.on('data', (chunk: Buffer) => {
      data += chunk.toString('utf8');
      if (data.length > 1_000_000) reject(new ServiceError(413, 'PAYLOAD_TOO_LARGE', 'Request body is too large.'));
    });
    request.on('end', () => {
      if (!data.trim()) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch {
        reject(new ServiceError(400, 'INVALID_JSON', 'Request body must be valid JSON.'));
      }
    });
    request.on('error', reject);
  });
}

const idPattern = '[A-Za-z0-9_-]+';

export function createTournamentHttpServer(service: TournamentService): any {
  return createServer(async (request: any, response: any) => {
    if (request.method === 'OPTIONS') {
      response.writeHead(204, { 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', 'Access-Control-Allow-Headers': 'Authorization,Content-Type' });
      response.end();
      return;
    }
    const url = new URL(request.url ?? '/', 'http://localhost');
    try {
      if (request.method === 'GET' && url.pathname === '/healthz') {
        send(response, 200, { ok: true, buildId: service.buildId, protocolVersion: service.protocolVersion });
        return;
      }
      if (request.method === 'GET' && url.pathname === '/admin') {
        send(response, 200, ADMIN_PAGE, 'text/html; charset=utf-8');
        return;
      }
      if (request.method === 'GET' && url.pathname === '/participant') {
        send(response, 200, PARTICIPANT_PAGE, 'text/html; charset=utf-8');
        return;
      }
      const isBracketRoute = request.method === 'GET' && new RegExp(`^/api/v1/tournaments/${idPattern}/bracket$`).test(url.pathname);
      const isAdminTournamentRoute = new RegExp(`^/api/v1/admin/tournaments/${idPattern}$`).test(url.pathname);
      const isAdminLifecycleRoute = new RegExp(`^/api/v1/admin/tournaments/${idPattern}/(registration/open|registration/close|check-in/open|start)$`).test(url.pathname);
      const isParticipantActionRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/tournaments/${idPattern}/(register|check-in)$`).test(url.pathname);
      const isPlayerTournamentRoute = request.method === 'GET'
        && new RegExp(`^/api/v1/player/tournaments/${idPattern}$`).test(url.pathname);
      const isAssignedJoinRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/matches/${idPattern}/join-assigned$`).test(url.pathname);
      if (request.method !== 'POST' && !(request.method === 'GET' && url.pathname === '/api/v1/player/matches')
        && !isBracketRoute && !(request.method === 'GET' && isAdminTournamentRoute) && !isPlayerTournamentRoute) {
        throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      }

      const body = request.method === 'POST' ? await readJson(request) : {};
      const clientKey = request.socket?.remoteAddress ?? 'unknown';
      if (request.method === 'POST' && url.pathname === '/api/v1/admin/participants') {
        send(response, 201, service.addParticipant(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/tournaments') {
        send(response, 201, service.createTournament(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/bracket/seed') {
        send(response, 201, service.seedBracket(bearer(request), body));
      } else if (request.method === 'GET' && isAdminTournamentRoute) {
        send(response, 200, service.adminTournament(bearer(request), url.pathname.split('/')[5]));
      } else if (request.method === 'POST' && isAdminLifecycleRoute) {
        const parts = url.pathname.split('/');
        const tournamentId = parts[5];
        const action = parts.slice(6).join('/');
        if (action === 'registration/open') send(response, 200, service.openRegistration(bearer(request), tournamentId));
        else if (action === 'registration/close') send(response, 200, service.closeRegistration(bearer(request), tournamentId));
        else if (action === 'check-in/open') send(response, 200, service.openCheckIn(bearer(request), tournamentId));
        else if (action === 'start') send(response, 200, service.startTournament(bearer(request), tournamentId));
        else throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      } else if (isParticipantActionRoute) {
        const parts = url.pathname.split('/');
        const tournamentId = parts[4];
        const sessionToken = bearer(request) ?? body.sessionToken ?? '';
        if (parts[5] === 'register') send(response, 200, service.registerParticipant(sessionToken, tournamentId));
        else if (parts[5] === 'check-in') send(response, 200, service.checkInParticipant(sessionToken, tournamentId));
        else throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      } else if (isPlayerTournamentRoute) {
        send(response, 200, service.playerTournament(bearer(request) ?? url.searchParams.get('sessionToken') ?? '', url.pathname.split('/')[4]));
      } else if (isAssignedJoinRoute) {
        send(response, 200, service.joinAssignedMatch({
          ...body,
          sessionToken: bearer(request) ?? body.sessionToken ?? '',
          matchId: url.pathname.split('/')[4],
          clientKey,
        }));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/participant-sessions') {
        send(response, 200, service.createParticipantSession(body, clientKey));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/matches/join') {
        send(response, 200, service.joinMatch({ ...body, clientKey }));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/rooms/heartbeat') {
        send(response, 200, service.heartbeat(body));
      } else if (request.method === 'POST' && new RegExp(`^/api/v1/matches/${idPattern}/result$`).test(url.pathname)) {
        const matchId = url.pathname.split('/')[4];
        send(response, 200, service.submitResult({ ...body, matchId }));
      } else if (request.method === 'GET' && url.pathname === '/api/v1/player/matches') {
        send(response, 200, { matches: service.playerMatches(bearer(request) ?? url.searchParams.get('sessionToken') ?? '') });
      } else if (isBracketRoute) {
        send(response, 200, service.publicBracket(url.pathname.split('/')[4]));
      } else {
        throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      }
    } catch (error: any) {
      if (error instanceof ServiceError) {
        send(response, error.status, { error: error.code, message: error.message, details: error.details });
      } else {
        send(response, 500, { error: 'INTERNAL_ERROR', message: 'The tournament service failed to process the request.' });
      }
    }
  });
}
