import { spawn } from 'node:child_process';

interface RoomProcess {
  matchId: string;
  child: any;
  ready: Promise<void>;
}

interface RoomManagerOptions {
  javaCommand: string;
  roomServerJar: string;
  portStart?: number;
  portEnd?: number;
  startupTimeoutMs?: number;
  processExitTimeoutMs?: number;
  argsForRoom?: (matchId: string, port: number, participantIds: string[]) => string[];
}

/** Starts one hidden Java room process when the tournament service assigns its port. */
export class TournamentRoomManager {
  private readonly rooms = new Map<number, RoomProcess>();
  private readonly options: RoomManagerOptions;

  constructor(options: RoomManagerOptions) {
    if (!options?.javaCommand || !options.roomServerJar) {
      throw new Error('Tournament room Java command and roomServer.jar are required.');
    }
    this.options = options;
  }

  async ensure(matchId: string, port: number, participantIds: string[]): Promise<void> {
    const start = this.options.portStart ?? 31000;
    const end = this.options.portEnd ?? 31049;
    if (!/^[A-Za-z0-9_-]{1,100}$/.test(matchId) || !Number.isInteger(port) || port < start || port > end) {
      throw new Error('Tournament room assignment is invalid.');
    }
    if (!Array.isArray(participantIds) || participantIds.length !== 2
      || !participantIds.every((id) => /^[A-Za-z0-9_-]{1,100}$/.test(id))
      || participantIds[0] === participantIds[1]) {
      throw new Error('Tournament room assignment must contain two distinct participant IDs.');
    }
    const running = this.rooms.get(port);
    if (running) {
      if (running.matchId === matchId) {
        await running.ready;
        return;
      }
      await this.waitForExit(running.child);
      if (this.rooms.get(port) === running
          && (running.child.exitCode !== null || running.child.signalCode !== null)) {
        this.rooms.delete(port);
      }
      if (this.rooms.get(port) === running) {
        throw new Error('Tournament room port is still occupied by another match.');
      }
    }

    const args = this.options.argsForRoom?.(matchId, port, participantIds) ?? [
      '-Xms24m', '-Xmx96m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
      '-Dyimo.config=/etc/yimo/yimo.properties', '-cp', this.options.roomServerJar,
      'RoomServer.TournamentRoomMain', '--port', String(port), '--match-id', matchId,
      '--participant-a', participantIds[0], '--participant-b', participantIds[1],
    ];
    const child = spawn(this.options.javaCommand, args, {
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });
    const room: RoomProcess = { matchId, child, ready: Promise.resolve() };
    room.ready = this.waitUntilReady(room, port);
    this.rooms.set(port, room);
    child.once('exit', () => {
      if (this.rooms.get(port) === room) this.rooms.delete(port);
    });
    child.stderr?.on('data', (chunk: Buffer) => process.stderr.write(`[tournament room ${port}] ${chunk}`));
    try {
      await room.ready;
    } catch (error) {
      child.kill();
      if (this.rooms.get(port) === room) this.rooms.delete(port);
      throw error;
    }
  }

  async release(matchId: string): Promise<boolean> {
    for (const [port, room] of this.rooms) {
      if (room.matchId !== matchId) continue;
      room.child.kill();
      await this.waitForExit(room.child);
      if (this.rooms.get(port) === room) this.rooms.delete(port);
      return true;
    }
    return false;
  }

  close(): void {
    for (const room of this.rooms.values()) room.child.kill();
    this.rooms.clear();
  }

  private waitUntilReady(room: RoomProcess, port: number): Promise<void> {
    const timeoutMs = this.options.startupTimeoutMs ?? 10000;
    return new Promise((resolve, reject) => {
      let output = '';
      const finish = (error?: Error) => {
        clearTimeout(timer);
        room.child.stdout?.removeListener('data', onData);
        room.child.removeListener('exit', onExit);
        if (error) reject(error); else resolve();
      };
      const onData = (chunk: Buffer) => {
        output = (output + chunk.toString('utf8')).slice(-2048);
        if (output.includes(`YIMO_TOURNAMENT_ROOM_READY|${port}|${room.matchId}`)) finish();
      };
      const onExit = (code: number | null) => finish(new Error(`Tournament room failed to start (exit ${code}).`));
      const timer = setTimeout(() => finish(new Error('Tournament room did not become ready in time.')), timeoutMs);
      room.child.stdout?.on('data', onData);
      room.child.once('exit', onExit);
      room.child.once('error', (error: Error) => finish(error));
    });
  }

  private waitForExit(child: any): Promise<void> {
    if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve();
    const timeoutMs = this.options.processExitTimeoutMs ?? 5000;
    return new Promise((resolve, reject) => {
      const finish = (error?: Error) => {
        clearTimeout(timer);
        child.removeListener('exit', onExit);
        if (error) reject(error); else resolve();
      };
      const onExit = () => finish();
      const timer = setTimeout(() => finish(new Error('Previous tournament room is still shutting down.')), timeoutMs);
      child.once('exit', onExit);
    });
  }
}
