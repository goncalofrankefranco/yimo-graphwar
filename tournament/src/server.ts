import { createServer } from 'node:http';
import { isIP } from 'node:net';
import { ADMIN_PAGE } from './pages.ts';
import { HOME_PAGE } from './home-page.ts';
import { PARTICIPANT_PAGE } from './participant-page.ts';
import { ServiceError, TournamentService } from './service.ts';

function send(response: any, status: number, body: unknown, contentType = 'application/json; charset=utf-8'): void {
  response.writeHead(status, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  response.end(contentType.startsWith('application/json') ? JSON.stringify(body) : String(body));
}

function sponsoredPage(html: string): string {
  const footer = '<footer style="width:100%;margin:24px auto 0;padding:16px 24px;color:#687570;font:13px system-ui,sans-serif;text-align:center"><a href="https://cloudzy.com/" rel="noopener noreferrer" target="_blank" style="color:inherit">Powered by Cloudzy</a></footer>';
  return html.replace('</main>', `${footer}</main>`);
}

function bearer(request: any): string | undefined {
  const value = [request.headers.authorization, request.headers['x-yimo-api-authorization']]
    .find((header) => typeof header === 'string' && header.startsWith('Bearer '));
  return typeof value === 'string' && value.startsWith('Bearer ') ? value.slice(7) : undefined;
}

function clientAddress(request: any): string {
  const remote = String(request.socket?.remoteAddress ?? 'unknown');
  const loopbackProxy = remote === '127.0.0.1' || remote === '::1' || remote === '::ffff:127.0.0.1';
  const forwarded = request.headers['x-forwarded-for'];
  if (loopbackProxy && typeof forwarded === 'string' && isIP(forwarded.trim())) return forwarded.trim();
  return remote;
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
      if (String(request.headers['content-type'] ?? '').startsWith('application/x-www-form-urlencoded')) {
        resolve(Object.fromEntries(new URLSearchParams(data)));
        return;
      }
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

export function createTournamentHttpServer(service: TournamentService,
  ensureRoom: (matchId: string, port: number, round: number) => Promise<void> | void = () => {},
  releaseRoom: (matchId: string) => Promise<boolean> | boolean = () => false): any {
  const prepareRoom = async (matchId: string, port: number) => {
    try {
      await ensureRoom(matchId, port, service.roomRound(matchId));
    } catch {
      throw new ServiceError(503, 'ROOM_START_FAILED', 'The assigned tournament room is not ready. Try again shortly.');
    }
  };
  return createServer(async (request: any, response: any) => {
    if (request.method === 'OPTIONS') {
      response.writeHead(204, { 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', 'Access-Control-Allow-Headers': 'Authorization,X-YIMO-API-Authorization,Content-Type' });
      response.end();
      return;
    }
    const url = new URL(request.url ?? '/', 'http://localhost');
    try {
      if (request.method === 'GET' && url.pathname === '/healthz') {
        send(response, 200, { ok: true, buildId: service.buildId, protocolVersion: service.protocolVersion });
        return;
      }
      if (request.method === 'GET' && url.pathname === '/') {
        send(response, 200, HOME_PAGE, 'text/html; charset=utf-8');
        return;
      }
      if (request.method === 'GET' && url.pathname === '/admin') {
        send(response, 200, sponsoredPage(ADMIN_PAGE), 'text/html; charset=utf-8');
        return;
      }
      if (request.method === 'GET' && url.pathname === '/participant') {
        send(response, 200, sponsoredPage(PARTICIPANT_PAGE), 'text/html; charset=utf-8');
        return;
      }
      const isBracketRoute = request.method === 'GET' && new RegExp(`^/api/v1/tournaments/${idPattern}/bracket$`).test(url.pathname);
      const isAdminTournamentRoute = new RegExp(`^/api/v1/admin/tournaments/${idPattern}$`).test(url.pathname);
      const isAdminDeleteTournamentRoute = request.method === 'DELETE' && isAdminTournamentRoute;
      const isCurrentAdminTournamentRoute = request.method === 'GET'
        && url.pathname === '/api/v1/admin/tournament/current';
      const isAdminLifecycleRoute = new RegExp(`^/api/v1/admin/tournaments/${idPattern}/(registration/open|registration/close|registration/reopen|check-in/open|check-in/close|matches/extend-expired|start)$`).test(url.pathname);
      const isParticipantActionRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/tournaments/${idPattern}/(register|check-in)$`).test(url.pathname);
      const isPlayerTournamentRoute = request.method === 'GET'
        && new RegExp(`^/api/v1/player/tournaments/${idPattern}$`).test(url.pathname);
      const isActiveTournamentRoute = request.method === 'GET' && url.pathname === '/api/v1/tournaments/active';
      const isAssignedJoinRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/matches/${idPattern}/join-assigned$`).test(url.pathname);
      const isGameJoinRoute = request.method === 'POST' && url.pathname === '/api/v1/game/join';
      const isAdminReleaseRoomRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/admin/matches/${idPattern}/release-room$`).test(url.pathname);
      const isAdminForfeitRoute = request.method === 'POST'
        && new RegExp(`^/api/v1/admin/matches/${idPattern}/forfeit-expired$`).test(url.pathname);
      if (request.method !== 'POST' && !(request.method === 'GET' && url.pathname === '/api/v1/player/matches')
        && !isBracketRoute && !(request.method === 'GET' && isAdminTournamentRoute)
        && !isAdminDeleteTournamentRoute && !isPlayerTournamentRoute
        && !isActiveTournamentRoute && !isCurrentAdminTournamentRoute) {
        throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      }

      const body = request.method === 'POST' ? await readJson(request) : {};
      const clientKey = clientAddress(request);
      if (isAdminForfeitRoute) {
        const matchId = url.pathname.split('/')[5];
        const result = service.forfeitExpiredMatch(bearer(request), matchId, body.winnerSide);
        let roomReleased = result.roomSlot === null;
        if (result.roomSlot !== null) {
          try { roomReleased = await releaseRoom(matchId); } catch { roomReleased = false; }
        }
        send(response, 200, { ...result, roomReleased });
      } else if (isAdminReleaseRoomRoute) {
        const matchId = url.pathname.split('/')[5];
        service.confirmCompletedMatch(bearer(request), matchId);
        send(response, 200, { matchId, released: await releaseRoom(matchId) });
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/participants') {
        send(response, 201, service.addParticipant(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/tournaments') {
        send(response, 201, service.createTournament(bearer(request), body));
      } else if (isAdminDeleteTournamentRoute) {
        const deletion = service.deleteTournament(bearer(request), url.pathname.split('/')[5]);
        const roomStops = deletion.roomMatchIds.map((matchId) => {
          try {
            return Promise.resolve(releaseRoom(matchId));
          } catch (error) {
            return Promise.reject(error);
          }
        });
        const stopResults = await Promise.allSettled(roomStops);
        const roomsStopped = stopResults.filter((result) =>
          result.status === 'fulfilled' && result.value === true).length;
        const roomStopFailures = stopResults.length - roomsStopped;
        for (let index = 0; index < stopResults.length; index += 1) {
          const result = stopResults[index];
          if (result.status === 'rejected') {
            console.error(`Failed to stop tournament room ${deletion.roomMatchIds[index]} during tournament deletion.`, result.reason);
          } else if (!result.value) {
            console.error(`Could not confirm tournament room ${deletion.roomMatchIds[index]} stopped during tournament deletion.`);
          }
        }
        const responseBody: Record<string, unknown> = {
          tournamentId: deletion.tournamentId,
          deleted: true,
        };
        if (deletion.roomMatchIds.length > 0) {
          responseBody.roomsStopped = roomsStopped;
          responseBody.roomStopFailures = roomStopFailures;
        }
        send(response, 200, responseBody);
      } else if (request.method === 'GET' && isAdminTournamentRoute) {
        send(response, 200, service.adminTournament(bearer(request), url.pathname.split('/')[5]));
      } else if (isCurrentAdminTournamentRoute) {
        send(response, 200, service.currentAdminTournament(bearer(request)));
      } else if (request.method === 'POST' && isAdminLifecycleRoute) {
        const parts = url.pathname.split('/');
        const tournamentId = parts[5];
        const action = parts.slice(6).join('/');
        if (action === 'registration/open') send(response, 200, service.openRegistration(bearer(request), tournamentId));
        else if (action === 'registration/close') send(response, 200, service.closeRegistration(bearer(request), tournamentId));
        else if (action === 'registration/reopen') send(response, 200, service.reopenRegistration(bearer(request), tournamentId));
        else if (action === 'check-in/open') send(response, 200, service.openCheckIn(bearer(request), tournamentId));
        else if (action === 'check-in/close') send(response, 200, service.closeCheckIn(bearer(request), tournamentId));
        else if (action === 'matches/extend-expired') send(response, 200, service.extendExpiredMatches(bearer(request), tournamentId));
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
        send(response, 200, service.playerTournament(bearer(request) ?? url.searchParams.get('sessionToken') ?? '', url.pathname.split('/')[5]));
      } else if (isActiveTournamentRoute) {
        send(response, 200, service.activeTournament());
      } else if (isAssignedJoinRoute) {
        const joined = service.joinAssignedMatch({
          ...body,
          sessionToken: bearer(request) ?? body.sessionToken ?? '',
          matchId: url.pathname.split('/')[4],
          clientKey,
        });
        await prepareRoom(joined.matchId, joined.port);
        send(response, 200, joined);
      } else if (isGameJoinRoute) {
        try {
          const joined = service.joinActiveMatch({ ...body, clientKey });
          await prepareRoom(joined.matchId, joined.port);
          const displayName = Buffer.from(joined.displayName, 'utf8').toString('base64url');
          send(response, 200, `YIMO_ROOM&${joined.port}&${joined.roomToken}&${displayName}`, 'text/plain; charset=utf-8');
        } catch (error: any) {
          if (!(error instanceof ServiceError)) throw error;
          send(response, error.status,
            `ERROR&${error.code}&${encodeURIComponent(error.message)}`, 'text/plain; charset=utf-8');
        }
      } else if (request.method === 'POST' && url.pathname === '/api/v1/participant-sessions') {
        send(response, 200, service.createParticipantSession(body, clientKey));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/matches/join') {
        const joined = service.joinMatch({ ...body, clientKey });
        await prepareRoom(joined.matchId, joined.port);
        send(response, 200, joined);
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
