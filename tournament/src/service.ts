import { DatabaseSync } from 'node:sqlite';
import {
  createHash,
  createHmac,
  randomBytes,
  randomUUID,
  scryptSync,
  timingSafeEqual,
} from 'node:crypto';

export interface ServiceOptions {
  dbPath?: string;
  adminToken: string;
  roomSecret: string;
  buildId?: string;
  protocolVersion?: number;
  now?: () => number;
  participantScryptCost?: number;
  rateLimitMax?: number;
}

export interface ParticipantInput {
  participantId: string;
  displayName: string;
  participantCode: string;
}

export interface TournamentInput {
  tournamentId?: string;
  name: string;
  buildId: string;
  protocolVersion: number;
  matchTimeoutSeconds?: number;
  roomPortStart?: number;
  roomPortEnd?: number;
}

export interface SeedInput {
  tournamentId: string;
  participantIds: string[];
}

export interface SessionInput {
  participantCode: string;
  buildId: string;
  protocolVersion: number;
}

export interface JoinInput {
  sessionToken: string;
  matchCode: string;
  buildId: string;
  protocolVersion: number;
  clientKey?: string;
}

export interface HeartbeatInput {
  roomToken: string;
  state?: 'ASSIGNED' | 'IN_PROGRESS';
}

export interface ResultInput {
  matchId: string;
  winnerParticipantId: string;
  loserParticipantId: string;
  reason: string;
  roomToken: string;
}

type MatchRow = Record<string, any>;

const SCHEMA = `
CREATE TABLE IF NOT EXISTS participants (
  participant_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  participant_code_salt TEXT NOT NULL,
  participant_code_hash TEXT NOT NULL,
  participant_code_lookup_hash TEXT NOT NULL UNIQUE,
  session_token_hash TEXT,
  created_at INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS participant_sessions (
  session_token_hash TEXT PRIMARY KEY,
  participant_id TEXT NOT NULL REFERENCES participants(participant_id),
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tournaments (
  tournament_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  build_id TEXT NOT NULL,
  protocol_version INTEGER NOT NULL,
  status TEXT NOT NULL,
  match_timeout_seconds INTEGER NOT NULL,
  room_port_start INTEGER NOT NULL,
  room_port_end INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS matches (
  match_id TEXT PRIMARY KEY,
  tournament_id TEXT NOT NULL REFERENCES tournaments(tournament_id),
  round INTEGER NOT NULL,
  position INTEGER NOT NULL,
  player_a_id TEXT REFERENCES participants(participant_id),
  player_b_id TEXT REFERENCES participants(participant_id),
  possible_a INTEGER NOT NULL,
  possible_b INTEGER NOT NULL,
  winner_id TEXT REFERENCES participants(participant_id),
  loser_id TEXT REFERENCES participants(participant_id),
  status TEXT NOT NULL,
  match_code_hash TEXT UNIQUE,
  match_code_expires_at INTEGER,
  room_slot INTEGER,
  result_reason TEXT,
  server_nonce TEXT,
  result_signature TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(tournament_id, round, position)
);
CREATE TABLE IF NOT EXISTS match_players (
  match_id TEXT NOT NULL REFERENCES matches(match_id),
  participant_id TEXT NOT NULL REFERENCES participants(participant_id),
  seed INTEGER,
  side TEXT NOT NULL,
  PRIMARY KEY(match_id, participant_id)
);
CREATE TABLE IF NOT EXISTS room_slots (
  tournament_id TEXT NOT NULL REFERENCES tournaments(tournament_id),
  room_slot INTEGER NOT NULL,
  port INTEGER NOT NULL,
  warm INTEGER NOT NULL,
  state TEXT NOT NULL,
  match_id TEXT,
  assigned_at INTEGER,
  heartbeat_at INTEGER,
  expires_at INTEGER,
  PRIMARY KEY(tournament_id, room_slot),
  UNIQUE(tournament_id, port)
);
CREATE TABLE IF NOT EXISTS audit_events (
  event_id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_type TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  details_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_matches_code_hash ON matches(match_code_hash);
CREATE INDEX IF NOT EXISTS idx_participants_lookup ON participants(participant_code_lookup_hash);
CREATE INDEX IF NOT EXISTS idx_matches_tournament ON matches(tournament_id, round, position);
CREATE INDEX IF NOT EXISTS idx_match_players_participant ON match_players(participant_id);
`;

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const MATCH_CODE_LENGTH = 10;

export class ServiceError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: Record<string, unknown>;

  constructor(status: number, code: string, message: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ServiceError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

class RateLimiter {
  private readonly buckets = new Map<string, { startedAt: number; count: number }>();
  private readonly max: number;

  constructor(max: number) {
    this.max = max;
  }

  check(key: string, now: number): void {
    const current = this.buckets.get(key);
    if (!current || now - current.startedAt >= 60) {
      this.buckets.set(key, { startedAt: now, count: 1 });
      return;
    }
    current.count += 1;
    if (current.count > this.max) {
      throw new ServiceError(429, 'RATE_LIMITED', 'Too many requests; retry later.');
    }
  }
}

function sha256(value: string): string {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function hmac(value: string, secret: string): string {
  return createHmac('sha256', secret).update(value, 'utf8').digest('base64url');
}

function safeEqual(left: string, right: string): boolean {
  const a = Buffer.from(left, 'hex');
  const b = Buffer.from(right, 'hex');
  return a.length === b.length && timingSafeEqual(a, b);
}

function validateIdentifier(value: unknown, field: string): string {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,100}$/.test(value)) {
    throw new ServiceError(400, 'INVALID_INPUT', `${field} is invalid.`);
  }
  return value;
}

function requiredText(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== 'string' || value.trim().length === 0 || value.length > maxLength) {
    throw new ServiceError(400, 'INVALID_INPUT', `${field} is required.`);
  }
  if (value.includes('\n') || value.includes('\r') || value.includes('&') || value.includes('|')) {
    throw new ServiceError(400, 'INVALID_INPUT', `${field} contains unsupported characters.`);
  }
  return value.trim();
}

function nextPowerOfTwo(value: number): number {
  let result = 1;
  while (result < value) result *= 2;
  return result;
}

function roomTokenPayload(payload: {
  protocolVersion: number;
  buildId: string;
  matchId: string;
  participantId: string;
  roomSlot: number;
  expiryMillis: number;
  nonce: string;
}): string {
  return [
    payload.protocolVersion,
    payload.buildId,
    payload.matchId,
    payload.participantId,
    payload.roomSlot,
    payload.expiryMillis,
    payload.nonce,
  ].join('|');
}

export function issueRoomToken(payload: {
  protocolVersion: number;
  buildId: string;
  matchId: string;
  participantId: string;
  roomSlot: number;
  expiryMillis: number;
  nonce: string;
}, secret: string): string {
  const raw = roomTokenPayload(payload);
  const encoded = Buffer.from(raw, 'utf8').toString('base64url');
  return `${encoded}.${hmac(raw, secret)}`;
}

export function verifyRoomToken(token: string, secret: string, nowMillis: number): {
  protocolVersion: number;
  buildId: string;
  matchId: string;
  participantId: string;
  roomSlot: number;
  expiryMillis: number;
  nonce: string;
} | null {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  try {
    const raw = Buffer.from(parts[0], 'base64url').toString('utf8');
    const expected = Buffer.from(hmac(raw, secret), 'utf8');
    const actual = Buffer.from(parts[1], 'utf8');
    if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null;
    const fields = raw.split('|');
    if (fields.length !== 7 || fields.some((field) => field.length === 0)) return null;
    const protocolVersion = Number(fields[0]);
    const roomSlot = Number(fields[4]);
    const expiryMillis = Number(fields[5]);
    if (!Number.isInteger(protocolVersion) || !Number.isInteger(roomSlot)
      || !Number.isInteger(expiryMillis) || expiryMillis < nowMillis) return null;
    return {
      protocolVersion,
      buildId: fields[1],
      matchId: fields[2],
      participantId: fields[3],
      roomSlot,
      expiryMillis,
      nonce: fields[6],
    };
  } catch {
    return null;
  }
}

export class TournamentService {
  readonly db: any;
  readonly buildId: string;
  readonly protocolVersion: number;
  private readonly adminToken: string;
  private readonly roomSecret: string;
  private readonly now: () => number;
  private readonly participantScryptCost: number;
  private readonly rateLimiter: RateLimiter;

  constructor(options: ServiceOptions) {
    if (!options?.adminToken || !options.roomSecret) {
      throw new Error('YIMO_ADMIN_TOKEN and YIMO_ROOM_HMAC_SECRET are required.');
    }
    this.adminToken = options.adminToken;
    this.roomSecret = options.roomSecret;
    this.buildId = options.buildId ?? 'YIMO-Graphwar-2.0.0';
    this.protocolVersion = options.protocolVersion ?? 2;
    this.now = options.now ?? (() => Math.floor(Date.now() / 1000));
    this.participantScryptCost = options.participantScryptCost ?? 16384;
    this.rateLimiter = new RateLimiter(options.rateLimitMax ?? 120);
    this.db = new DatabaseSync(options.dbPath ?? ':memory:');
    this.db.exec('PRAGMA foreign_keys = ON;');
    this.db.exec('PRAGMA busy_timeout = 5000;');
    if ((options.dbPath ?? ':memory:') !== ':memory:') this.db.exec('PRAGMA journal_mode = WAL;');
    this.db.exec(SCHEMA);
  }

  close(): void {
    this.db.close();
  }

  private timestamp(): number {
    return Math.floor(this.now());
  }

  private requireAdmin(token: string | undefined): void {
    if (token !== this.adminToken) throw new ServiceError(401, 'UNAUTHORIZED', 'Organizer authorization required.');
  }

  private requireBuild(buildId: unknown, protocolVersion: unknown): void {
    if (buildId !== this.buildId || Number(protocolVersion) !== this.protocolVersion) {
      throw new ServiceError(409, 'VERSION_MISMATCH', 'Client build is not accepted by this YIMO tournament.');
    }
  }

  private transaction<T>(callback: () => T): T {
    this.db.exec('BEGIN IMMEDIATE;');
    try {
      const result = callback();
      this.db.exec('COMMIT;');
      return result;
    } catch (error) {
      this.db.exec('ROLLBACK;');
      throw error;
    }
  }

  private audit(eventType: string, subjectId: string, details: Record<string, unknown>): void {
    this.db.prepare(
      'INSERT INTO audit_events(event_type, subject_id, details_json, created_at) VALUES (?, ?, ?, ?)',
    ).run(eventType, subjectId, JSON.stringify(details), this.timestamp());
  }

  private participantHash(code: string, salt: string): string {
    return scryptSync(code, salt, 64, {
      N: this.participantScryptCost,
      r: 8,
      p: 1,
      maxmem: 128 * 1024 * 1024,
    }).toString('hex');
  }

  addParticipant(adminToken: string | undefined, input: ParticipantInput): { participantId: string; displayName: string } {
    this.requireAdmin(adminToken);
    const participantId = validateIdentifier(input?.participantId, 'participantId');
    const displayName = requiredText(input?.displayName, 'displayName', 80);
    const participantCode = requiredText(input?.participantCode, 'participantCode', 200);
    if (this.db.prepare('SELECT 1 FROM participants WHERE participant_id = ?').get(participantId)) {
      throw new ServiceError(409, 'PARTICIPANT_EXISTS', 'Participant already exists.');
    }
    const salt = randomBytes(16).toString('hex');
    const lookupHash = hmac(participantCode, this.roomSecret);
    if (this.db.prepare('SELECT 1 FROM participants WHERE participant_code_lookup_hash = ?').get(lookupHash)) {
      throw new ServiceError(409, 'PARTICIPANT_CODE_EXISTS', 'Participant code is already assigned.');
    }
    this.db.prepare(`
      INSERT INTO participants(participant_id, display_name, participant_code_salt, participant_code_hash,
        participant_code_lookup_hash, session_token_hash, created_at, status)
      VALUES (?, ?, ?, ?, ?, NULL, ?, 'ACTIVE')
    `).run(participantId, displayName, salt, this.participantHash(participantCode, salt), lookupHash,
      this.timestamp());
    this.audit('PARTICIPANT_CREATED', participantId, { displayName });
    return { participantId, displayName };
  }

  createTournament(adminToken: string | undefined, input: TournamentInput): { tournamentId: string; status: string } {
    this.requireAdmin(adminToken);
    const tournamentId = validateIdentifier(input?.tournamentId ?? `t-${randomUUID()}`, 'tournamentId');
    const name = requiredText(input?.name, 'name', 120);
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const timeout = Number(input?.matchTimeoutSeconds ?? 900);
    const roomStart = Number(input?.roomPortStart ?? 30000);
    const roomEnd = Number(input?.roomPortEnd ?? 30049);
    if (!Number.isInteger(timeout) || timeout < 60 || timeout > 86400
      || !Number.isInteger(roomStart) || !Number.isInteger(roomEnd)
      || roomStart < 1 || roomEnd > 65535 || roomStart > roomEnd || roomEnd - roomStart + 1 > 50) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Tournament timeout or room-port range is invalid.');
    }
    if (this.db.prepare('SELECT 1 FROM tournaments WHERE tournament_id = ?').get(tournamentId)) {
      throw new ServiceError(409, 'TOURNAMENT_EXISTS', 'Tournament already exists.');
    }
    const now = this.timestamp();
    this.transaction(() => {
      this.db.prepare(`
        INSERT INTO tournaments(tournament_id, name, build_id, protocol_version, status,
          match_timeout_seconds, room_port_start, room_port_end, created_at)
        VALUES (?, ?, ?, ?, 'CREATED', ?, ?, ?, ?)
      `).run(tournamentId, name, this.buildId, this.protocolVersion, timeout, roomStart, roomEnd, now);
      const insertSlot = this.db.prepare(`
        INSERT INTO room_slots(tournament_id, room_slot, port, warm, state)
        VALUES (?, ?, ?, ?, ?)
      `);
      for (let port = roomStart; port <= roomEnd; port += 1) {
        insertSlot.run(tournamentId, port, port, port - roomStart < 20 ? 1 : 0,
          port - roomStart < 20 ? 'AVAILABLE' : 'OFFLINE');
      }
      this.audit('TOURNAMENT_CREATED', tournamentId, { name, roomStart, roomEnd });
    });
    return { tournamentId, status: 'CREATED' };
  }

  private newMatchCode(): { code: string; hash: string } {
    for (;;) {
      let code = '';
      for (let i = 0; i < MATCH_CODE_LENGTH; i += 1) {
        code += CODE_ALPHABET[randomBytes(1)[0] % CODE_ALPHABET.length];
      }
      const hash = sha256(code);
      if (!this.db.prepare('SELECT 1 FROM matches WHERE match_code_hash = ?').get(hash)) return { code, hash };
    }
  }

  private buildMatchNode(tournamentId: string, round: number, position: number, playerA: string | null,
    playerB: string | null, possibleA: boolean, possibleB: boolean): any {
    let status = 'PENDING';
    let winner: string | null = null;
    if (!possibleA && !possibleB) status = 'BYE';
    else if (possibleA && possibleB) status = playerA && playerB ? 'OPEN' : 'PENDING';
    else if (playerA || playerB) {
      status = 'BYE';
      winner = playerA ?? playerB;
    }
    const code = status === 'OPEN' ? this.newMatchCode() : null;
    return {
      matchId: `${tournamentId}-r${round}-m${position + 1}`,
      round,
      position,
      playerA,
      playerB,
      possibleA,
      possibleB,
      winner,
      status,
      matchCode: code?.code ?? null,
      matchCodeHash: code?.hash ?? null,
    };
  }

  private publicMatch(match: any): Record<string, unknown> {
    return {
      matchId: match.matchId,
      round: match.round,
      position: match.position,
      playerA: match.playerA,
      playerB: match.playerB,
      status: match.status,
      winner: match.winner,
      matchCode: match.matchCode,
    };
  }

  seedBracket(adminToken: string | undefined, input: SeedInput): { tournamentId: string; matches: Record<string, unknown>[] } {
    this.requireAdmin(adminToken);
    const tournamentId = validateIdentifier(input?.tournamentId, 'tournamentId');
    const participantIds = input?.participantIds;
    if (!Array.isArray(participantIds) || participantIds.length < 2 || participantIds.length > 5000) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Two to 5000 participant IDs are required.');
    }
    const ids = participantIds.map((id) => validateIdentifier(id, 'participantId'));
    if (new Set(ids).size !== ids.length) throw new ServiceError(400, 'DUPLICATE_PARTICIPANT', 'Participant IDs must be unique.');
    const tournament = this.db.prepare('SELECT * FROM tournaments WHERE tournament_id = ?').get(tournamentId) as any;
    if (!tournament) throw new ServiceError(404, 'TOURNAMENT_NOT_FOUND', 'Tournament not found.');
    if (tournament.status !== 'CREATED') throw new ServiceError(409, 'BRACKET_EXISTS', 'Tournament bracket already seeded.');
    for (const id of ids) {
      if (!this.db.prepare("SELECT 1 FROM participants WHERE participant_id = ? AND status = 'ACTIVE'").get(id)) {
        throw new ServiceError(400, 'INVALID_PARTICIPANT', `Participant ${id} is not active.`);
      }
    }

    const size = nextPowerOfTwo(ids.length);
    const rounds = Math.log2(size);
    const nodes: any[][] = [];
    nodes[0] = [];
    for (let position = 0; position < size / 2; position += 1) {
      nodes[0].push(this.buildMatchNode(tournamentId, 1, position, ids[position * 2] ?? null,
        ids[position * 2 + 1] ?? null, position * 2 < ids.length, position * 2 + 1 < ids.length));
    }
    for (let round = 2; round <= rounds; round += 1) {
      const previous = nodes[round - 2];
      nodes[round - 1] = [];
      for (let position = 0; position < previous.length / 2; position += 1) {
        const left = previous[position * 2];
        const right = previous[position * 2 + 1];
        nodes[round - 1].push(this.buildMatchNode(tournamentId, round, position,
          left.winner, right.winner, left.possibleA || left.possibleB, right.possibleA || right.possibleB));
      }
    }

    this.transaction(() => {
      const insertMatch = this.db.prepare(`
        INSERT INTO matches(match_id, tournament_id, round, position, player_a_id, player_b_id,
          possible_a, possible_b, winner_id, loser_id, status, match_code_hash,
          match_code_expires_at, room_slot, result_reason, server_nonce, result_signature, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, NULL, NULL, NULL, ?, ?)
      `);
      const insertPlayer = this.db.prepare(
        'INSERT OR IGNORE INTO match_players(match_id, participant_id, seed, side) VALUES (?, ?, ?, ?)',
      );
      for (const round of nodes) {
        for (const match of round) {
          const expires = match.status === 'OPEN' ? this.timestamp() + Number(tournament.match_timeout_seconds) : null;
          insertMatch.run(match.matchId, tournamentId, match.round, match.position, match.playerA, match.playerB,
            match.possibleA ? 1 : 0, match.possibleB ? 1 : 0, match.winner, match.status,
            match.matchCodeHash, expires, this.timestamp(), this.timestamp());
          if (match.playerA) insertPlayer.run(match.matchId, match.playerA, ids.indexOf(match.playerA) + 1, 'A');
          if (match.playerB) insertPlayer.run(match.matchId, match.playerB, ids.indexOf(match.playerB) + 1, 'B');
        }
      }
      this.db.prepare("UPDATE tournaments SET status = 'SEEDED' WHERE tournament_id = ?").run(tournamentId);
      this.audit('BRACKET_SEEDED', tournamentId, { participantCount: ids.length, bracketSize: size });
    });
    return { tournamentId, matches: nodes.flat().map((match) => this.publicMatch(match)) };
  }

  createParticipantSession(input: SessionInput, clientKey = 'local'): { sessionToken: string; participantId: string; expiresAt: number } {
    this.rateLimiter.check(`session:${clientKey}`, this.timestamp());
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const code = requiredText(input?.participantCode, 'participantCode', 200);
    const lookupHash = hmac(code, this.roomSecret);
    const participant = this.db.prepare(
      "SELECT * FROM participants WHERE status = 'ACTIVE' AND participant_code_lookup_hash = ?",
    ).get(lookupHash) as any;
    if (!participant || !safeEqual(
      this.participantHash(code, participant.participant_code_salt), participant.participant_code_hash,
    )) {
      throw new ServiceError(401, 'INVALID_PARTICIPANT_CODE', 'Participant code is invalid.');
    }
    const sessionToken = randomBytes(32).toString('base64url');
    const expiresAt = this.timestamp() + 3600;
    this.db.prepare(`
      INSERT INTO participant_sessions(session_token_hash, participant_id, expires_at, created_at)
      VALUES (?, ?, ?, ?)
    `).run(sha256(sessionToken), participant.participant_id, expiresAt, this.timestamp());
    this.audit('PARTICIPANT_SESSION_CREATED', participant.participant_id, { expiresAt });
    return { sessionToken, participantId: participant.participant_id, expiresAt };
  }

  private participantForSession(sessionToken: string): any {
    if (typeof sessionToken !== 'string' || sessionToken.length < 20) {
      throw new ServiceError(401, 'INVALID_SESSION', 'Participant session is invalid.');
    }
    const participant = this.db.prepare(`
      SELECT p.* FROM participants p JOIN participant_sessions s ON s.participant_id = p.participant_id
      WHERE s.session_token_hash = ? AND s.expires_at > ? AND p.status = 'ACTIVE'
    `).get(sha256(sessionToken), this.timestamp()) as any;
    if (!participant) throw new ServiceError(401, 'INVALID_SESSION', 'Participant session is invalid.');
    return participant;
  }

  private verifyMatchCode(matchCode: string): MatchRow {
    const normalized = requiredText(matchCode, 'matchCode', 20).toUpperCase();
    const hash = sha256(normalized);
    const match = this.db.prepare(`
      SELECT m.*, t.build_id, t.protocol_version, t.match_timeout_seconds, t.status AS tournament_status
      FROM matches m JOIN tournaments t ON t.tournament_id = m.tournament_id
      WHERE m.match_code_hash = ?
    `).get(hash) as MatchRow | undefined;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match code is invalid.');
    if (!match.match_code_expires_at || Number(match.match_code_expires_at) <= this.timestamp()) {
      throw new ServiceError(410, 'MATCH_CODE_EXPIRED', 'Match code has expired.');
    }
    if (match.status !== 'OPEN' && match.status !== 'ASSIGNED' && match.status !== 'IN_PROGRESS') {
      throw new ServiceError(409, 'MATCH_CLOSED', 'Match is not accepting players.');
    }
    return match;
  }

  joinMatch(input: JoinInput): { matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number } {
    this.rateLimiter.check(`join:${input?.clientKey ?? 'local'}`, this.timestamp());
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const participant = this.participantForSession(input?.sessionToken);
    const match = this.verifyMatchCode(input?.matchCode);
    if (match.player_a_id !== participant.participant_id && match.player_b_id !== participant.participant_id) {
      throw new ServiceError(403, 'PARTICIPANT_NOT_IN_MATCH', 'Participant is not assigned to this match.');
    }
    const now = this.timestamp();
    return this.transaction(() => {
      let room = this.db.prepare(
        'SELECT * FROM room_slots WHERE tournament_id = ? AND match_id = ?',
      ).get(match.tournament_id, match.match_id) as any;
      if (!room) {
        room = this.db.prepare(`
          SELECT * FROM room_slots WHERE tournament_id = ? AND state = 'AVAILABLE'
          ORDER BY room_slot LIMIT 1
        `).get(match.tournament_id) as any;
        if (!room) throw new ServiceError(503, 'ROOM_POOL_EXHAUSTED', 'No tournament room is available.');
        this.db.prepare(`
          UPDATE room_slots SET state = 'ASSIGNED', match_id = ?, assigned_at = ?, heartbeat_at = ?,
            expires_at = ? WHERE tournament_id = ? AND room_slot = ?
        `).run(match.match_id, now, now, match.match_code_expires_at, match.tournament_id, room.room_slot);
        this.db.prepare("UPDATE matches SET room_slot = ?, status = 'ASSIGNED', updated_at = ? WHERE match_id = ?")
          .run(room.room_slot, now, match.match_id);
      }
      const expiryMillis = Math.min(Number(match.match_code_expires_at) * 1000, (now + 900) * 1000);
      const token = issueRoomToken({
        protocolVersion: this.protocolVersion,
        buildId: this.buildId,
        matchId: match.match_id,
        participantId: participant.participant_id,
        roomSlot: Number(room.room_slot),
        expiryMillis,
        nonce: randomBytes(16).toString('base64url'),
      }, this.roomSecret);
      this.audit('MATCH_JOINED', match.match_id, { participantId: participant.participant_id, roomSlot: room.room_slot });
      return {
        matchId: match.match_id,
        roomSlot: Number(room.room_slot),
        port: Number(room.port),
        roomToken: token,
        expiresAt: Math.floor(expiryMillis / 1000),
      };
    });
  }

  heartbeat(input: HeartbeatInput): { matchId: string; roomSlot: number; state: string } {
    const payload = verifyRoomToken(input?.roomToken, this.roomSecret, this.now() * 1000);
    if (!payload || payload.protocolVersion !== this.protocolVersion || payload.buildId !== this.buildId) {
      throw new ServiceError(403, 'INVALID_ROOM_TOKEN', 'Room token is invalid or expired.');
    }
    const match = this.db.prepare('SELECT * FROM matches WHERE match_id = ?').get(payload.matchId) as any;
    if (!match || Number(match.room_slot) !== payload.roomSlot || match.status === 'COMPLETED') {
      throw new ServiceError(403, 'INVALID_ROOM_TOKEN', 'Room token does not belong to this room.');
    }
    const state = input?.state === 'ASSIGNED' ? 'ASSIGNED' : 'IN_PROGRESS';
    const now = this.timestamp();
    this.db.prepare(`
      UPDATE room_slots SET state = ?, heartbeat_at = ? WHERE tournament_id = ? AND room_slot = ? AND match_id = ?
    `).run(state, now, match.tournament_id, payload.roomSlot, payload.matchId);
    this.db.prepare("UPDATE matches SET status = 'IN_PROGRESS', updated_at = ? WHERE match_id = ? AND status <> 'COMPLETED'")
      .run(now, payload.matchId);
    this.audit('ROOM_HEARTBEAT', payload.matchId, { roomSlot: payload.roomSlot, state });
    return { matchId: payload.matchId, roomSlot: payload.roomSlot, state };
  }

  private advanceWinner(match: MatchRow, winnerId: string): Record<string, unknown> | null {
    const parent = this.db.prepare(`
      SELECT * FROM matches WHERE tournament_id = ? AND round = ? AND position = ?
    `).get(match.tournament_id, Number(match.round) + 1, Math.floor(Number(match.position) / 2)) as any;
    if (!parent) return null;
    const sideA = Number(match.position) % 2 === 0;
    const nextA = sideA ? winnerId : parent.player_a_id;
    const nextB = sideA ? parent.player_b_id : winnerId;
    const now = this.timestamp();
    const possibleA = Number(parent.possible_a) === 1;
    const possibleB = Number(parent.possible_b) === 1;
    let status = 'PENDING';
    let parentWinner: string | null = null;
    let code: { code: string; hash: string } | null = null;
    if (!possibleA && !possibleB) status = 'BYE';
    else if (possibleA && possibleB) {
      status = nextA && nextB ? 'OPEN' : 'PENDING';
    } else if (nextA || nextB) {
      status = 'BYE';
      parentWinner = nextA ?? nextB;
    }
    if (status === 'OPEN' && !parent.match_code_hash) code = this.newMatchCode();
    const tournament = this.db.prepare('SELECT match_timeout_seconds FROM tournaments WHERE tournament_id = ?')
      .get(match.tournament_id) as any;
    this.db.prepare(`
      UPDATE matches SET player_a_id = ?, player_b_id = ?, winner_id = ?, status = ?,
        match_code_hash = COALESCE(match_code_hash, ?),
        match_code_expires_at = CASE WHEN ? = 'OPEN' THEN ? ELSE match_code_expires_at END,
        updated_at = ? WHERE match_id = ?
    `).run(nextA, nextB, parentWinner, status, code?.hash ?? null, status,
      now + Number(tournament.match_timeout_seconds), now, parent.match_id);
    const side = sideA ? 'A' : 'B';
    this.db.prepare('INSERT OR IGNORE INTO match_players(match_id, participant_id, seed, side) VALUES (?, ?, NULL, ?)')
      .run(parent.match_id, winnerId, side);
    if (parentWinner) {
      this.advanceWinner({ ...parent, player_a_id: nextA, player_b_id: nextB, status }, parentWinner);
    }
    return {
      matchId: parent.match_id,
      round: Number(parent.round),
      position: Number(parent.position),
      status,
      playerA: nextA,
      playerB: nextB,
      matchCode: code?.code ?? null,
    };
  }

  submitResult(input: ResultInput): { duplicate: boolean; matchId: string; resultSignature: string; nextMatch: Record<string, unknown> | null } {
    const matchId = validateIdentifier(input?.matchId, 'matchId');
    const winnerId = validateIdentifier(input?.winnerParticipantId, 'winnerParticipantId');
    const loserId = validateIdentifier(input?.loserParticipantId, 'loserParticipantId');
    const reason = requiredText(input?.reason, 'reason', 80);
    const match = this.db.prepare('SELECT * FROM matches WHERE match_id = ?').get(matchId) as MatchRow | undefined;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match not found.');
    if (match.status === 'COMPLETED') {
      if (match.winner_id === winnerId && match.loser_id === loserId && match.result_reason === reason) {
        return { duplicate: true, matchId, resultSignature: match.result_signature, nextMatch: null };
      }
      throw new ServiceError(409, 'RESULT_ALREADY_SUBMITTED', 'A different result is already recorded.');
    }
    const payload = verifyRoomToken(input?.roomToken, this.roomSecret, this.now() * 1000);
    if (!payload || payload.protocolVersion !== this.protocolVersion || payload.buildId !== this.buildId
      || payload.matchId !== matchId || Number(match.room_slot) !== payload.roomSlot) {
      throw new ServiceError(403, 'INVALID_ROOM_TOKEN', 'Room token is invalid for this match.');
    }
    if (match.status !== 'ASSIGNED' && match.status !== 'IN_PROGRESS') {
      throw new ServiceError(409, 'MATCH_NOT_ACTIVE', 'Match has not started.');
    }
    const isValidPair = (match.player_a_id === winnerId && match.player_b_id === loserId)
      || (match.player_b_id === winnerId && match.player_a_id === loserId);
    if (!isValidPair) throw new ServiceError(400, 'INVALID_RESULT', 'Winner and loser are not this match pair.');
    return this.transaction(() => {
      const nonce = randomBytes(16).toString('base64url');
      const resultBody = `${matchId}|${winnerId}|${loserId}|${reason}|${nonce}`;
      const signature = hmac(resultBody, this.roomSecret);
      const now = this.timestamp();
      this.db.prepare(`
        UPDATE matches SET status = 'COMPLETED', winner_id = ?, loser_id = ?, result_reason = ?,
          server_nonce = ?, result_signature = ?, updated_at = ? WHERE match_id = ? AND status <> 'COMPLETED'
      `).run(winnerId, loserId, reason, nonce, signature, now, matchId);
      this.db.prepare(`
        UPDATE room_slots SET state = 'AVAILABLE', match_id = NULL, assigned_at = NULL,
          heartbeat_at = NULL, expires_at = NULL WHERE tournament_id = ? AND room_slot = ? AND match_id = ?
      `).run(match.tournament_id, match.room_slot, matchId);
      const nextMatch = this.advanceWinner(match, winnerId);
      this.audit('MATCH_RESULT_SUBMITTED', matchId, { winnerId, loserId, reason });
      return { duplicate: false, matchId, resultSignature: signature, nextMatch };
    });
  }

  playerMatches(sessionToken: string): Record<string, unknown>[] {
    const participant = this.participantForSession(sessionToken);
    const rows = this.db.prepare(`
      SELECT match_id AS matchId, tournament_id AS tournamentId, round, position,
        player_a_id AS playerA, player_b_id AS playerB, status, winner_id AS winner,
        room_slot AS roomSlot, match_code_expires_at AS matchCodeExpiresAt
      FROM matches WHERE player_a_id = ? OR player_b_id = ? ORDER BY tournament_id, round, position
    `).all(participant.participant_id, participant.participant_id) as any[];
    return rows.map((row) => ({ ...row }));
  }

  countParticipants(): number {
    return Number((this.db.prepare('SELECT COUNT(*) AS count FROM participants').get() as any).count);
  }

  rawParticipantCodeCount(): number {
    const columns = this.db.prepare('PRAGMA table_info(participants)').all() as any[];
    return columns.some((column) => column.name === 'participant_code') ? this.countParticipants() : 0;
  }
}
