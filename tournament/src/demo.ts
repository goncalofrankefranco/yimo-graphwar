import { TournamentService } from './service.ts';

export const DEMO_ADMIN_TOKEN = 'demo-admin-token';
export const DEMO_ROOM_SECRET = 'demo-room-secret';
export const DEMO_TOURNAMENT_ID = 'yimo-demo-2026';
export const DEMO_BUILD_ID = 'YIMO-Graphwar-2.0.0';
export const DEMO_PROTOCOL_VERSION = 2;

export interface DemoFixture {
  service: TournamentService;
  tournamentId: string;
  participantCodes: string[];
  schedule: { registrationOpenAt: number; registrationCloseAt: number; startAt: number };
  advanceTo(timestamp: number): number;
}

export function createDemoService(initialNow = Math.floor(Date.now() / 1000)): DemoFixture {
  let current = initialNow;
  const schedule = {
    registrationOpenAt: initialNow + 10,
    registrationCloseAt: initialNow + 20,
    startAt: initialNow + 30,
  };
  const participantCodes = Array.from({ length: 4 }, (_, index) => `DEMO-PARTICIPANT-${index + 1}`);
  const service = new TournamentService({
    dbPath: ':memory:',
    adminToken: DEMO_ADMIN_TOKEN,
    roomSecret: DEMO_ROOM_SECRET,
    buildId: DEMO_BUILD_ID,
    protocolVersion: DEMO_PROTOCOL_VERSION,
    now: () => current,
    rateLimitMax: 1000,
    participantScryptCost: 256,
  });
  for (let index = 1; index <= participantCodes.length; index += 1) {
    service.addParticipant(DEMO_ADMIN_TOKEN, {
      participantId: `demo-${index}`,
      displayName: `Demo Player ${index}`,
      participantCode: participantCodes[index - 1],
    });
  }
  service.createTournament(DEMO_ADMIN_TOKEN, {
    tournamentId: DEMO_TOURNAMENT_ID,
    name: 'YIMO Practice Cup',
    buildId: DEMO_BUILD_ID,
    protocolVersion: DEMO_PROTOCOL_VERSION,
    matchTimeoutSeconds: 900,
    roomPortStart: 30000,
    roomPortEnd: 30049,
    registrationOpenAt: schedule.registrationOpenAt,
    registrationCloseAt: schedule.registrationCloseAt,
    startAt: schedule.startAt,
    autoStart: true,
  });
  return {
    service,
    tournamentId: DEMO_TOURNAMENT_ID,
    participantCodes,
    schedule,
    advanceTo(timestamp: number) {
      if (!Number.isInteger(timestamp)) throw new TypeError('Demo time must be an integer.');
      current = timestamp;
      return service.processScheduledEvents();
    },
  };
}

export function registerDemoParticipants(demo: DemoFixture): void {
  demo.advanceTo(demo.schedule.registrationOpenAt);
  for (const code of demo.participantCodes) {
    const session = demo.service.createParticipantSession({
      participantCode: code,
      buildId: DEMO_BUILD_ID,
      protocolVersion: DEMO_PROTOCOL_VERSION,
    });
    demo.service.registerParticipant(session.sessionToken, demo.tournamentId);
  }
}

export function runDemoToStart(demo: DemoFixture): void {
  registerDemoParticipants(demo);
  demo.advanceTo(demo.schedule.registrationCloseAt);
  demo.advanceTo(demo.schedule.startAt);
}
