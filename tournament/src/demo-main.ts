import { createTournamentHttpServer } from './server.ts';
import { createDemoService, DEMO_ADMIN_TOKEN, DEMO_ROOM_SECRET, DEMO_TOURNAMENT_ID } from './demo.ts';

const { service, matchCodes } = createDemoService();
const host = process.env.HOST ?? '127.0.0.1';
const port = Number(process.env.PORT ?? 8080);
const server = createTournamentHttpServer(service);
server.listen(port, host, () => {
  console.log(`YIMO tournament demo listening on http://${host}:${port}`);
  console.log(`Bracket: http://${host}:${port}/participant?tournament=${DEMO_TOURNAMENT_ID}`);
  console.log(`Admin token: ${DEMO_ADMIN_TOKEN}`);
  console.log(`Room secret: ${DEMO_ROOM_SECRET}`);
  console.log(`Open match codes: ${JSON.stringify(matchCodes)}`);
});
process.on('SIGINT', () => {
  server.close();
  service.close();
});
