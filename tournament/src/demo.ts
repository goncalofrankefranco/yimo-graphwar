import { TournamentService } from './service.ts';

export const DEMO_ADMIN_TOKEN = 'demo-admin-token';
export const DEMO_ROOM_SECRET = 'demo-room-secret';
export const DEMO_TOURNAMENT_ID = 'yimo-demo-2026';

export function createDemoService(): { service: TournamentService; matchCodes: Record<string, string> } {
  const service = new TournamentService({
    dbPath: ':memory:',
    adminToken: DEMO_ADMIN_TOKEN,
    roomSecret: DEMO_ROOM_SECRET,
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
    rateLimitMax: 1000,
  });
  for (let index = 1; index <= 8; index += 1) {
    service.addParticipant(DEMO_ADMIN_TOKEN, {
      participantId: `demo-${index}`,
      displayName: `Demo Player ${index}`,
      participantCode: `DEMO-PARTICIPANT-${index}`,
    });
  }
  service.createTournament(DEMO_ADMIN_TOKEN, {
    tournamentId: DEMO_TOURNAMENT_ID,
    name: 'YIMO Practice Cup',
    buildId: 'YIMO-Graphwar-2.0.0',
    protocolVersion: 2,
    matchTimeoutSeconds: 900,
    roomPortStart: 30000,
    roomPortEnd: 30049,
  });
  const seeded = service.seedBracket(DEMO_ADMIN_TOKEN, {
    tournamentId: DEMO_TOURNAMENT_ID,
    participantIds: Array.from({ length: 8 }, (_, index) => `demo-${index + 1}`),
  });
  const matchCodes: Record<string, string> = {};
  for (const match of seeded.matches) {
    if (match.matchCode) matchCodes[String(match.matchId)] = String(match.matchCode);
  }
  return { service, matchCodes };
}
