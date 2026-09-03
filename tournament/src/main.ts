import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { createTournamentHttpServer } from './server.ts';
import { TournamentService } from './service.ts';

const adminToken = process.env.YIMO_ADMIN_TOKEN;
const roomSecret = process.env.YIMO_ROOM_HMAC_SECRET;
if (!adminToken || !roomSecret) {
  console.error('Set YIMO_ADMIN_TOKEN and YIMO_ROOM_HMAC_SECRET before starting the tournament service.');
  process.exitCode = 1;
} else {
  const dbPath = process.env.YIMO_TOURNAMENT_DB ?? './data/tournament.sqlite';
  mkdirSync(dbPath === ':memory:' ? '.' : dirname(dbPath), { recursive: true });
  const service = new TournamentService({
    dbPath,
    adminToken,
    roomSecret,
    buildId: process.env.YIMO_BUILD_ID ?? 'YIMO-Graphwar-2.0.0',
    protocolVersion: Number(process.env.YIMO_PROTOCOL_VERSION ?? 2),
  });
  const port = Number(process.env.PORT ?? 8080);
  const host = process.env.HOST ?? '127.0.0.1';
  createTournamentHttpServer(service).listen(port, host, () => {
    console.log(`YIMO tournament service listening on http://${host}:${port}`);
  });
}
