import { createTournamentHttpServer } from './server.ts';
import { createDemoService, DEMO_ADMIN_TOKEN, runDemoToStart } from './demo.ts';

const demo = createDemoService();
runDemoToStart(demo);
const { service } = demo;
const host = process.env.HOST ?? '127.0.0.1';
const port = Number(process.env.PORT ?? 8080);
const server = createTournamentHttpServer(service);
server.listen(port, host, () => {
  console.log(`YIMO tournament demo listening on http://${host}:${port}`);
  console.log(`Bracket: http://${host}:${port}/participant?tournament=${demo.tournamentId}`);
  console.log(`Admin token: ${DEMO_ADMIN_TOKEN}`);
  console.log(`Participant codes: ${demo.participantCodes.join(', ')}`);
});
process.on('SIGINT', () => {
  server.close();
  service.close();
});
