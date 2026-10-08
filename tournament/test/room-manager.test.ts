import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer, createConnection } from 'node:net';
import { TournamentRoomManager, tournamentRoomArgs } from '../src/room-manager.ts';

async function freePort(): Promise<number> {
  const server = createServer();
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = (server.address() as any).port;
  await new Promise<void>((resolve) => server.close(() => resolve()));
  return port;
}

async function canConnect(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
  });
}

test('passes the authoritative round as a Java room argument', () => {
  const args = tournamentRoomArgs('room.jar', 'opaque-match', 31000, ['a', 'b'], 12);
  assert.deepEqual(args.slice(args.indexOf('--round'), args.indexOf('--round') + 2), ['--round', '12']);
});

test('starts one process per assigned tournament port and refuses cross-match reuse', async () => {
  const port = await freePort();
  const manager = new TournamentRoomManager({
    javaCommand: process.execPath,
    roomServerJar: 'unused-in-this-smoke-test.jar',
    portStart: port,
    portEnd: port,
    processExitTimeoutMs: 100,
    argsForRoom: (matchId, roomPort, participantIds, round) => {
      assert.equal(round, matchId === 'match-one' ? 3 : 4);
      const expected = matchId === 'match-one'
        ? ['candidate-1', 'candidate-2'] : ['candidate-3', 'candidate-4'];
      assert.deepEqual(participantIds, expected);
      return [
        '-e',
        'const net=require("node:net");const p=Number(process.argv[1]);const id=process.argv[2];const s=net.createServer();s.listen(p,"127.0.0.1",()=>console.log(`YIMO_TOURNAMENT_ROOM_READY|${p}|${id}`));setInterval(()=>{},1000);',
        String(roomPort), matchId,
      ];
    },
  });
  try {
    await manager.ensure('match-one', port, ['candidate-1', 'candidate-2'], 3);
    await manager.ensure('match-one', port, ['candidate-1', 'candidate-2'], 3);
    assert.equal(await canConnect(port), true);
    await assert.rejects(manager.ensure('match-two', port, ['candidate-3', 'candidate-4'], 4), /still shutting down/);
    assert.equal(await manager.release('match-one'), true);
    assert.equal(await canConnect(port), false, 'a completed room must stop listening');
    await manager.ensure('match-two', port, ['candidate-3', 'candidate-4'], 4);
    assert.equal(await canConnect(port), true, 'the released port must be reusable');
  } finally {
    manager.close();
  }
});

test('concurrent joins for the same match wait for the room startup once', async () => {
  const port = await freePort();
  const manager = new TournamentRoomManager({
    javaCommand: process.execPath,
    roomServerJar: 'unused-in-this-smoke-test.jar',
    portStart: port,
    portEnd: port,
    argsForRoom: (matchId, roomPort, participantIds, round) => {
      assert.deepEqual(participantIds, ['candidate-1', 'candidate-2']);
      assert.equal(round, 8);
      return [
        '-e',
        'const net=require("node:net");const p=Number(process.argv[1]);const id=process.argv[2];const s=net.createServer();setTimeout(()=>s.listen(p,"127.0.0.1",()=>console.log(`YIMO_TOURNAMENT_ROOM_READY|${p}|${id}`)),600);setInterval(()=>{},1000);',
        String(roomPort), matchId,
      ];
    },
  });
  try {
    const first = manager.ensure('same-match', port, ['candidate-1', 'candidate-2'], 8);
    const second = manager.ensure('same-match', port, ['candidate-1', 'candidate-2'], 8);
    await second;
    assert.equal(await canConnect(port), true, 'every join request must wait until the shared room is listening');
    await first;
  } finally {
    manager.close();
  }
});
