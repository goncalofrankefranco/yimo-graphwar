import { DatabaseSync } from 'node:sqlite';
import {
  createHash,
  createHmac,
  randomInt,
  randomBytes,
  randomUUID,
  scryptSync,
  timingSafeEqual,
} from 'node:crypto';
import { applyTournamentMigrations } from './migrations.ts';
import { assertTransition, canStart, dueTransition } from './lifecycle.ts';

export interface ServiceOptions {
  dbPath?: string;
  adminToken: string;
  roomSecret: string;
  buildId?: string;
  protocolVersion?: number;
  now?: () => number;
  participantScryptCost?: number;
  rateLimitMax?: number;
  defaultRoomCount?: number;
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
  registrationOpenAt?: number;
  registrationCloseAt?: number;
  checkInOpenAt?: number;
  checkInCloseAt?: number;
  startAt?: number;
  autoStart?: boolean;
  requireCheckIn?: boolean;
}

export interface SeedInput {
  tournamentId: string;
  participantIds: string[];
}

export interface SessionInput {
  participantCode: string;
  displayName?: string;
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

export interface AssignedJoinInput {
  sessionToken: string;
  matchId: string;
  buildId: string;
  protocolVersion: number;
  clientKey?: string;
}

export interface GameJoinInput extends SessionInput {
  roomPort: number;
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
  serverNonce: string;
  serverSignature: string;
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
const TOURNAMENT_PORT_START = 31000;
const TOURNAMENT_PORT_END = 31049;
const ACTIVE_TOURNAMENT_STATUSES = "'REGISTRATION_OPEN', 'CHECK_IN', 'READY', 'START_BLOCKED', 'RUNNING'";

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

function safeEqualText(left: string, right: string): boolean {
  const a = Buffer.from(left, 'utf8');
  const b = Buffer.from(right, 'utf8');
  return a.length === b.length && timingSafeEqual(a, b);
}

function optionalTimestamp(value: unknown, field: string): number | null {
  if (value === undefined || value === null || value === '') return null;
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
    throw new ServiceError(400, 'INVALID_INPUT', `${field} must be an integer timestamp.`);
  }
  return value;
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
  displayName: string;
  roomSlot: number;
  expiryMillis: number;
  nonce: string;
}): string {
  return [
    payload.protocolVersion,
    payload.buildId,
    payload.matchId,
    payload.participantId,
    payload.displayName,
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
  displayName: string;
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
  displayName: string;
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
    if (fields.length !== 8 || fields.some((field) => field.length === 0)) return null;
    const protocolVersion = Number(fields[0]);
    const roomSlot = Number(fields[5]);
    const expiryMillis = Number(fields[6]);
    if (!Number.isInteger(protocolVersion) || !Number.isInteger(roomSlot)
      || !Number.isInteger(expiryMillis) || expiryMillis < nowMillis) return null;
    return {
      protocolVersion,
      buildId: fields[1],
      matchId: fields[2],
      participantId: fields[3],
      displayName: fields[4],
      roomSlot,
      expiryMillis,
      nonce: fields[7],
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
  private readonly defaultRoomCount: number;

  constructor(options: ServiceOptions) {
    if (!options?.adminToken || !options.roomSecret) {
      throw new Error('YIMO_ADMIN_PASSWORD and YIMO_ROOM_HMAC_SECRET are required.');
    }
    this.adminToken = options.adminToken;
    this.roomSecret = options.roomSecret;
    this.buildId = options.buildId ?? 'YIMO-Graphwar-2.2.0';
    this.protocolVersion = options.protocolVersion ?? 2;
    this.now = options.now ?? (() => Math.floor(Date.now() / 1000));
    this.participantScryptCost = options.participantScryptCost ?? 16384;
    this.rateLimiter = new RateLimiter(options.rateLimitMax ?? 120);
    this.defaultRoomCount = options.defaultRoomCount ?? 1;
    if (!Number.isInteger(this.defaultRoomCount) || this.defaultRoomCount < 1 || this.defaultRoomCount > 50) {
      throw new Error('YIMO_TOURNAMENT_ROOM_COUNT must be an integer from 1 to 50.');
    }
    this.db = new DatabaseSync(options.dbPath ?? ':memory:');
    this.db.exec('PRAGMA foreign_keys = ON;');
    this.db.exec('PRAGMA busy_timeout = 5000;');
    if ((options.dbPath ?? ':memory:') !== ':memory:') this.db.exec('PRAGMA journal_mode = WAL;');
    this.db.exec(SCHEMA);
    applyTournamentMigrations(this.db);
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

  private otherActiveTournament(tournamentId: string): any | undefined {
    return this.db.prepare(`
      SELECT tournament_id FROM tournaments
      WHERE tournament_id <> ? AND status IN (${ACTIVE_TOURNAMENT_STATUSES}) LIMIT 1
    `).get(tournamentId) as any;
  }

  private assertNoOtherActiveTournament(tournamentId: string): void {
    const active = this.otherActiveTournament(tournamentId);
    if (active) {
      throw new ServiceError(409, 'ACTIVE_TOURNAMENT_EXISTS',
        'Another tournament is already active. Complete it before opening another.');
    }
  }

  activeTournament(): Record<string, unknown> | null {
    const row = this.db.prepare(`
      SELECT tournament_id FROM tournaments
      WHERE status IN ('REGISTRATION_OPEN', 'CHECK_IN', 'READY', 'RUNNING')
      ORDER BY CASE status WHEN 'RUNNING' THEN 0 ELSE 1 END, updated_at DESC, created_at DESC LIMIT 1
    `).get() as any;
    return row ? this.publicBracket(String(row.tournament_id)) : null;
  }

  currentAdminTournament(adminToken: string | undefined): Record<string, unknown> | null {
    this.requireAdmin(adminToken);
    const active = this.db.prepare(`
      SELECT tournament_id FROM tournaments WHERE status IN (${ACTIVE_TOURNAMENT_STATUSES})
      ORDER BY CASE status WHEN 'RUNNING' THEN 0 ELSE 1 END, updated_at DESC, created_at DESC LIMIT 1
    `).get() as any;
    const draft = active ? null : this.db.prepare(`
      SELECT tournament_id FROM tournaments WHERE status = 'DRAFT'
      ORDER BY updated_at DESC, created_at DESC LIMIT 1
    `).get() as any;
    const row = active ?? draft ?? this.db.prepare(`
      SELECT tournament_id FROM tournaments WHERE status = 'COMPLETED'
      ORDER BY updated_at DESC, created_at DESC LIMIT 1
    `).get() as any;
    return row ? this.adminTournament(adminToken, String(row.tournament_id)) : null;
  }

  deleteTournament(adminToken: string | undefined, tournamentId: string): { tournamentId: string; deleted: true } {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    return this.transaction(() => {
      const tournament = this.db.prepare('SELECT name, status FROM tournaments WHERE tournament_id = ?').get(id) as any;
      if (!tournament) throw new ServiceError(404, 'TOURNAMENT_NOT_FOUND', 'Tournament not found.');
      const deletable = new Set(['DRAFT', 'REGISTRATION_OPEN', 'CHECK_IN', 'READY', 'START_BLOCKED', 'COMPLETED']);
      const activeRoom = this.db.prepare(`
        SELECT 1 FROM room_slots WHERE tournament_id = ? AND state IN ('ASSIGNED', 'IN_PROGRESS', 'DRAINING') LIMIT 1
      `).get(id);
      if (!deletable.has(String(tournament.status)) || activeRoom) {
        throw new ServiceError(409, 'TOURNAMENT_NOT_DELETABLE',
          'Running tournaments or tournaments with active rooms cannot be deleted.');
      }
      this.db.prepare('DELETE FROM tournament_entries WHERE tournament_id = ?').run(id);
      this.db.prepare('DELETE FROM match_players WHERE match_id IN (SELECT match_id FROM matches WHERE tournament_id = ?)').run(id);
      this.db.prepare('DELETE FROM room_slots WHERE tournament_id = ?').run(id);
      this.db.prepare('DELETE FROM matches WHERE tournament_id = ?').run(id);
      this.db.prepare('DELETE FROM tournaments WHERE tournament_id = ?').run(id);
      this.audit('TOURNAMENT_DELETED', id, { name: tournament.name, status: tournament.status });
      return { tournamentId: id, deleted: true };
    });
  }

  createTournament(adminToken: string | undefined, input: TournamentInput): { tournamentId: string; status: string } {
    this.requireAdmin(adminToken);
    const tournamentId = validateIdentifier(input?.tournamentId ?? `t-${randomUUID()}`, 'tournamentId');
    const name = requiredText(input?.name, 'name', 120);
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const timeout = Number(input?.matchTimeoutSeconds ?? 1200);
    const roomStart = Number(input?.roomPortStart ?? TOURNAMENT_PORT_START);
    // ponytail: the configured pool size supplies the default; explicit ranges stay authoritative.
    const roomEnd = Number(input?.roomPortEnd ?? roomStart + this.defaultRoomCount - 1);
    if (!Number.isInteger(timeout) || timeout < 60 || timeout > 1200
      || !Number.isInteger(roomStart) || !Number.isInteger(roomEnd)
      || roomStart < TOURNAMENT_PORT_START || roomEnd > TOURNAMENT_PORT_END
      || roomStart > roomEnd || roomEnd - roomStart + 1 > 50) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Tournament timeout or room-port range is invalid.');
    }
    const requireCheckIn = input?.requireCheckIn === true;
    const autoStart = input?.autoStart === true;
    const registrationOpenAt = optionalTimestamp(input?.registrationOpenAt, 'registrationOpenAt');
    const registrationCloseAt = optionalTimestamp(input?.registrationCloseAt, 'registrationCloseAt');
    const checkInOpenAt = optionalTimestamp(input?.checkInOpenAt, 'checkInOpenAt');
    const checkInCloseAt = optionalTimestamp(input?.checkInCloseAt, 'checkInCloseAt');
    const startAt = optionalTimestamp(input?.startAt, 'startAt');
    if (registrationCloseAt !== null && registrationOpenAt === null) {
      throw new ServiceError(400, 'INVALID_INPUT', 'registrationOpenAt is required with registrationCloseAt.');
    }
    if (registrationOpenAt !== null && registrationCloseAt !== null
      && registrationCloseAt < registrationOpenAt) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Registration window is inverted.');
    }
    if (!requireCheckIn && (checkInOpenAt !== null || checkInCloseAt !== null)) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Check-in timestamps require requireCheckIn=true.');
    }
    if (checkInCloseAt !== null && checkInOpenAt === null) {
      throw new ServiceError(400, 'INVALID_INPUT', 'checkInOpenAt is required with checkInCloseAt.');
    }
    if (checkInOpenAt !== null && checkInCloseAt !== null && checkInCloseAt < checkInOpenAt) {
      throw new ServiceError(400, 'INVALID_INPUT', 'Check-in window is inverted.');
    }
    if (autoStart && startAt === null) {
      throw new ServiceError(400, 'INVALID_INPUT', 'autoStart requires startAt.');
    }
    if (startAt !== null && registrationCloseAt !== null && startAt < registrationCloseAt) {
      throw new ServiceError(400, 'INVALID_INPUT', 'startAt must be after registration closes.');
    }
    if (startAt !== null && checkInCloseAt !== null && startAt < checkInCloseAt) {
      throw new ServiceError(400, 'INVALID_INPUT', 'startAt must be after check-in closes.');
    }
    if (this.db.prepare('SELECT 1 FROM tournaments WHERE tournament_id = ?').get(tournamentId)) {
      throw new ServiceError(409, 'TOURNAMENT_EXISTS', 'Tournament already exists.');
    }
    const now = this.timestamp();
    this.transaction(() => {
      this.db.prepare(`
        INSERT INTO tournaments(tournament_id, name, build_id, protocol_version, status,
          match_timeout_seconds, room_port_start, room_port_end, created_at,
          require_check_in, registration_open_at, registration_close_at,
          check_in_open_at, check_in_close_at, start_at, auto_start,
          started_at, started_by, roster_frozen_at, updated_at)
        VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?)
      `).run(tournamentId, name, this.buildId, this.protocolVersion, timeout, roomStart, roomEnd, now,
        requireCheckIn ? 1 : 0, registrationOpenAt, registrationCloseAt, checkInOpenAt,
        checkInCloseAt, startAt, autoStart ? 1 : 0, now);
      const insertSlot = this.db.prepare(`
        INSERT INTO room_slots(tournament_id, room_slot, port, warm, state)
        VALUES (?, ?, ?, ?, ?)
      `);
      for (let port = roomStart; port <= roomEnd; port += 1) {
        insertSlot.run(tournamentId, port, port, port - roomStart < 20 ? 1 : 0,
          port - roomStart < 20 ? 'AVAILABLE' : 'OFFLINE');
      }
      this.audit('TOURNAMENT_CREATED', tournamentId, {
        name, roomStart, roomEnd, requireCheckIn, autoStart,
        registrationOpenAt, registrationCloseAt, checkInOpenAt, checkInCloseAt, startAt,
      });
    });
    return { tournamentId, status: 'DRAFT' };
  }

  private issueParticipantSession(participantId: string): { sessionToken: string; participantId: string; expiresAt: number } {
    const participant = this.db.prepare(
      "SELECT participant_id FROM participants WHERE participant_id = ? AND status = 'ACTIVE'",
    ).get(participantId) as any;
    if (!participant) throw new ServiceError(404, 'PARTICIPANT_NOT_FOUND', 'Participant not found.');
    const sessionToken = randomBytes(32).toString('base64url');
    const expiresAt = this.timestamp() + 3600;
    this.db.prepare(`
      INSERT INTO participant_sessions(session_token_hash, participant_id, expires_at, created_at)
      VALUES (?, ?, ?, ?)
    `).run(sha256(sessionToken), participant.participant_id, expiresAt, this.timestamp());
    this.audit('PARTICIPANT_SESSION_CREATED', participant.participant_id, { expiresAt });
    return { sessionToken, participantId: participant.participant_id, expiresAt };
  }

  private tournamentRow(tournamentId: string): any {
    const id = validateIdentifier(tournamentId, 'tournamentId');
    const tournament = this.db.prepare('SELECT * FROM tournaments WHERE tournament_id = ?').get(id) as any;
    if (!tournament) throw new ServiceError(404, 'TOURNAMENT_NOT_FOUND', 'Tournament not found.');
    return tournament;
  }

  private schedule(tournament: any): Record<string, unknown> {
    return {
      registrationOpenAt: tournament.registration_open_at === null ? null : Number(tournament.registration_open_at),
      registrationCloseAt: tournament.registration_close_at === null ? null : Number(tournament.registration_close_at),
      checkInOpenAt: tournament.check_in_open_at === null ? null : Number(tournament.check_in_open_at),
      checkInCloseAt: tournament.check_in_close_at === null ? null : Number(tournament.check_in_close_at),
      startAt: tournament.start_at === null ? null : Number(tournament.start_at),
      autoStart: Number(tournament.auto_start) === 1,
      requireCheckIn: Number(tournament.require_check_in) === 1,
      startedAt: tournament.started_at === null ? null : Number(tournament.started_at),
      startedBy: tournament.started_by ?? null,
      rosterFrozenAt: tournament.roster_frozen_at === null ? null : Number(tournament.roster_frozen_at),
    };
  }

  private changeTournamentStatus(adminToken: string | undefined, tournamentId: string,
    to: 'REGISTRATION_OPEN' | 'CHECK_IN' | 'READY', eventType: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    this.transaction(() => {
      const tournament = this.tournamentRow(id);
      if (tournament.status !== to) {
        this.assertNoOtherActiveTournament(id);
        assertTransition(tournament.status, to);
        const now = this.timestamp();
        this.db.prepare('UPDATE tournaments SET status = ?, updated_at = ? WHERE tournament_id = ?')
          .run(to, now, id);
        this.audit(eventType, id, { from: tournament.status, to });
      }
    });
    return this.adminTournament(adminToken, id);
  }

  openRegistration(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    return this.changeTournamentStatus(adminToken, tournamentId, 'REGISTRATION_OPEN', 'REGISTRATION_OPENED');
  }

  reopenRegistration(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    this.transaction(() => {
      const tournament = this.tournamentRow(id);
      if (tournament.status !== 'START_BLOCKED' || tournament.roster_frozen_at !== null) {
        throw new ServiceError(409, 'REGISTRATION_CANNOT_REOPEN',
          'Registration can only be reopened when a tournament start is blocked.');
      }
      this.assertNoOtherActiveTournament(id);
      assertTransition(tournament.status, 'REGISTRATION_OPEN');
      const now = this.timestamp();
      this.db.prepare(`
        UPDATE tournaments SET status = 'REGISTRATION_OPEN', registration_open_at = ?,
          registration_close_at = NULL, check_in_open_at = NULL, check_in_close_at = NULL,
          start_at = NULL, auto_start = 0, updated_at = ? WHERE tournament_id = ?
      `).run(now, now, id);
      this.audit('REGISTRATION_REOPENED', id, { from: tournament.status, source: 'admin' });
    });
    return this.adminTournament(adminToken, id);
  }

  closeRegistration(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    const tournament = this.tournamentRow(id);
    return this.changeTournamentStatus(adminToken, id,
      Number(tournament.require_check_in) === 1 ? 'CHECK_IN' : 'READY',
      'REGISTRATION_CLOSED');
  }

  openCheckIn(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const tournament = this.tournamentRow(tournamentId);
    if (Number(tournament.require_check_in) !== 1) {
      throw new ServiceError(409, 'CHECK_IN_DISABLED', 'Check-in is disabled for this tournament.');
    }
    return this.changeTournamentStatus(adminToken, tournamentId, 'CHECK_IN', 'CHECK_IN_OPENED');
  }

  closeCheckIn(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const tournament = this.tournamentRow(tournamentId);
    if (Number(tournament.require_check_in) !== 1) {
      throw new ServiceError(409, 'CHECK_IN_DISABLED', 'Check-in is disabled for this tournament.');
    }
    return this.changeTournamentStatus(adminToken, tournamentId, 'READY', 'CHECK_IN_CLOSED');
  }

  extendExpiredMatches(adminToken: string | undefined, tournamentId: string): { tournamentId: string; extended: number } {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    const tournament = this.tournamentRow(id);
    if (tournament.status !== 'RUNNING') {
      throw new ServiceError(409, 'TOURNAMENT_NOT_RUNNING', 'Expired matches can only be extended during a running tournament.');
    }
    const now = this.timestamp();
    const expiresAt = now + Number(tournament.match_timeout_seconds);
    const extended = this.transaction(() => {
      const expired = this.db.prepare(`
        SELECT match_id, status, room_slot FROM matches
        WHERE tournament_id = ? AND status = 'OPEN'
          AND match_code_expires_at IS NOT NULL AND match_code_expires_at <= ?
      `).all(id, now) as any[];
      for (const match of expired) {
        const code = this.newMatchCode();
        this.db.prepare(`
          UPDATE matches SET match_code_hash = ?, match_code_expires_at = ?, updated_at = ?
          WHERE match_id = ? AND status = 'OPEN' AND match_code_expires_at <= ?
        `).run(code.hash, expiresAt, now, match.match_id, now);
        this.audit('MATCH_EXPIRY_EXTENDED', match.match_id, { tournamentId: id, expiresAt });
      }
      return expired.length;
    });
    return { tournamentId: id, extended };
  }

  confirmCompletedMatch(adminToken: string | undefined, matchId: string): { matchId: string } {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(matchId, 'matchId');
    const match = this.db.prepare('SELECT status FROM matches WHERE match_id = ?').get(id) as any;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match not found.');
    if (match.status !== 'COMPLETED') {
      throw new ServiceError(409, 'MATCH_NOT_COMPLETED', 'A room can only be released after its result is recorded.');
    }
    return { matchId: id };
  }

  forfeitExpiredMatch(adminToken: string | undefined, matchId: string, winnerSide: string):
    { matchId: string; winnerParticipantId: string; loserParticipantId: string; roomSlot: number | null; reason: 'FORFEIT'; nextMatch: Record<string, unknown> | null } {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(matchId, 'matchId');
    if (winnerSide !== 'A' && winnerSide !== 'B') {
      throw new ServiceError(400, 'INVALID_INPUT', 'winnerSide must be A or B.');
    }
    const match = this.db.prepare(`
      SELECT m.*, t.status AS tournament_status FROM matches m
      JOIN tournaments t ON t.tournament_id = m.tournament_id WHERE m.match_id = ?
    `).get(id) as MatchRow | undefined;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match not found.');
    if (match.tournament_status !== 'RUNNING') {
      throw new ServiceError(409, 'TOURNAMENT_NOT_RUNNING', 'An expired match can only be forfeited during a running tournament.');
    }
    if (!['OPEN', 'ASSIGNED', 'IN_PROGRESS'].includes(match.status)
      || match.match_code_expires_at === null || Number(match.match_code_expires_at) > this.timestamp()) {
      throw new ServiceError(409, 'MATCH_NOT_EXPIRED', 'Only an expired, unfinished match can be forfeited.');
    }
    const winnerId = winnerSide === 'A' ? match.player_a_id : match.player_b_id;
    const loserId = winnerSide === 'A' ? match.player_b_id : match.player_a_id;
    if (!winnerId || !loserId) {
      throw new ServiceError(409, 'MATCH_NOT_READY', 'A match needs two assigned competitors before a forfeit can be recorded.');
    }
    const reason = 'FORFEIT';
    const serverNonce = randomBytes(16).toString('base64url');
    const serverSignature = hmac(`${id}|${winnerId}|${loserId}|${reason}|${serverNonce}`, this.roomSecret);
    const result = this.submitResult({
      matchId: id,
      winnerParticipantId: String(winnerId),
      loserParticipantId: String(loserId),
      reason,
      serverNonce,
      serverSignature,
    });
    this.audit('ADMIN_MATCH_FORFEIT', id, { winnerId, loserId, winnerSide });
    return {
      matchId: id,
      winnerParticipantId: String(winnerId),
      loserParticipantId: String(loserId),
      roomSlot: match.room_slot === null ? null : Number(match.room_slot),
      reason,
      nextMatch: result.nextMatch,
    };
  }

  private entryView(entry: any): Record<string, unknown> {
    return {
      tournamentId: entry.tournament_id,
      participantId: entry.participant_id,
      entryStatus: entry.entry_status,
      registeredAt: Number(entry.registered_at),
      checkedInAt: entry.checked_in_at === null ? null : Number(entry.checked_in_at),
      seed: entry.seed === null ? null : Number(entry.seed),
    };
  }

  registerParticipant(sessionToken: string, tournamentId: string): Record<string, unknown> {
    const participant = this.participantForSession(sessionToken);
    const tournament = this.tournamentRow(tournamentId);
    if (tournament.roster_frozen_at !== null) {
      throw new ServiceError(409, 'ROSTER_FROZEN', 'Tournament registration is frozen.');
    }
    if (tournament.status !== 'REGISTRATION_OPEN') {
      throw new ServiceError(409, tournament.status === 'DRAFT' ? 'REGISTRATION_NOT_OPEN' : 'REGISTRATION_CLOSED',
        'Registration is not open.');
    }
    const now = this.timestamp();
    if (tournament.registration_close_at !== null && now >= Number(tournament.registration_close_at)) {
      throw new ServiceError(409, 'REGISTRATION_CLOSED', 'Registration has closed.');
    }
    return this.transaction(() => {
      this.db.prepare(`
        INSERT OR IGNORE INTO tournament_entries(tournament_id, participant_id, entry_status, registered_at)
        VALUES (?, ?, 'REGISTERED', ?)
      `).run(tournament.tournament_id, participant.participant_id, now);
      const entry = this.db.prepare(
        'SELECT * FROM tournament_entries WHERE tournament_id = ? AND participant_id = ?',
      ).get(tournament.tournament_id, participant.participant_id) as any;
      this.audit('PARTICIPANT_REGISTERED', tournament.tournament_id, {
        participantId: participant.participant_id,
      });
      return this.entryView(entry);
    });
  }

  checkInParticipant(sessionToken: string, tournamentId: string): Record<string, unknown> {
    const participant = this.participantForSession(sessionToken);
    const tournament = this.tournamentRow(tournamentId);
    if (Number(tournament.require_check_in) !== 1) {
      throw new ServiceError(409, 'CHECK_IN_DISABLED', 'Check-in is disabled for this tournament.');
    }
    if (tournament.status !== 'CHECK_IN') {
      throw new ServiceError(409, 'CHECK_IN_NOT_OPEN', 'Check-in is not open.');
    }
    const now = this.timestamp();
    if (tournament.check_in_close_at !== null && now >= Number(tournament.check_in_close_at)) {
      throw new ServiceError(409, 'CHECK_IN_CLOSED', 'Check-in has closed.');
    }
    return this.transaction(() => {
      const entry = this.db.prepare(
        'SELECT * FROM tournament_entries WHERE tournament_id = ? AND participant_id = ?',
      ).get(tournament.tournament_id, participant.participant_id) as any;
      if (!entry) throw new ServiceError(409, 'NOT_REGISTERED', 'Participant is not registered.');
      if (entry.entry_status === 'CHECKED_IN') return this.entryView(entry);
      if (entry.entry_status !== 'REGISTERED') {
        throw new ServiceError(409, 'CHECK_IN_CLOSED', 'Participant cannot check in.');
      }
      this.db.prepare(`
        UPDATE tournament_entries SET entry_status = 'CHECKED_IN', checked_in_at = ?
        WHERE tournament_id = ? AND participant_id = ? AND entry_status = 'REGISTERED'
      `).run(now, tournament.tournament_id, participant.participant_id);
      const updated = this.db.prepare(
        'SELECT * FROM tournament_entries WHERE tournament_id = ? AND participant_id = ?',
      ).get(tournament.tournament_id, participant.participant_id) as any;
      this.audit('PARTICIPANT_CHECKED_IN', tournament.tournament_id, {
        participantId: participant.participant_id,
      });
      return this.entryView(updated);
    });
  }

  adminTournament(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const tournament = this.tournamentRow(tournamentId);
    const countsRows = this.db.prepare(`
      SELECT entry_status, COUNT(*) AS count FROM tournament_entries
      WHERE tournament_id = ? GROUP BY entry_status
    `).all(tournament.tournament_id) as any[];
    const counts: Record<string, number> = { registered: 0, checkedIn: 0, eligible: 0, total: 0 };
    for (const row of countsRows) {
      const count = Number(row.count);
      counts.total += count;
      if (row.entry_status === 'REGISTERED' || row.entry_status === 'CHECKED_IN'
        || row.entry_status === 'ELIGIBLE') counts.registered += count;
      if (row.entry_status === 'CHECKED_IN') counts.checkedIn += count;
      if (row.entry_status === 'ELIGIBLE') counts.eligible += count;
    }
    const roster = (this.db.prepare(`
      SELECT e.participant_id AS participantId, p.display_name AS displayName,
        e.entry_status AS entryStatus, e.registered_at AS registeredAt,
        e.checked_in_at AS checkedInAt, e.seed AS seed
      FROM tournament_entries e JOIN participants p ON p.participant_id = e.participant_id
      WHERE e.tournament_id = ? ORDER BY e.registered_at, e.participant_id
    `).all(tournament.tournament_id) as any[]).map((row) => ({
      participantId: row.participantId,
      displayName: row.displayName,
      entryStatus: row.entryStatus,
      registeredAt: Number(row.registeredAt),
      checkedInAt: row.checkedInAt === null ? null : Number(row.checkedInAt),
      seed: row.seed === null ? null : Number(row.seed),
    }));
    return {
      tournamentId: tournament.tournament_id,
      name: tournament.name,
      status: tournament.status,
      buildId: tournament.build_id,
      protocolVersion: Number(tournament.protocol_version),
      schedule: this.schedule(tournament),
      counts,
      roster,
      bracket: this.publicBracket(tournament.tournament_id),
    };
  }

  playerTournament(sessionToken: string, tournamentId: string): Record<string, unknown> {
    const participant = this.participantForSession(sessionToken);
    const tournament = this.tournamentRow(tournamentId);
    const entry = this.db.prepare(
      'SELECT * FROM tournament_entries WHERE tournament_id = ? AND participant_id = ?',
    ).get(tournament.tournament_id, participant.participant_id) as any;
    const match = this.db.prepare(`
      SELECT m.match_id AS matchId, m.round, m.position, m.status,
        pa.display_name AS playerA, pb.display_name AS playerB, pw.display_name AS winner
      FROM matches m
      LEFT JOIN participants pa ON pa.participant_id = m.player_a_id
      LEFT JOIN participants pb ON pb.participant_id = m.player_b_id
      LEFT JOIN participants pw ON pw.participant_id = m.winner_id
      WHERE m.tournament_id = ? AND (m.player_a_id = ? OR m.player_b_id = ?)
        AND m.status NOT IN ('COMPLETED', 'BYE') ORDER BY m.round, m.position LIMIT 1
    `).get(tournament.tournament_id, participant.participant_id, participant.participant_id) as any;
    return {
      tournamentId: tournament.tournament_id,
      name: tournament.name,
      status: tournament.status,
      participant: { participantId: participant.participant_id, displayName: participant.display_name },
      entry: entry ? this.entryView(entry) : null,
      schedule: this.schedule(tournament),
      nextMatch: match ? {
        matchId: match.matchId,
        round: Number(match.round),
        position: Number(match.position),
        status: match.status,
        playerA: match.playerA ?? null,
        playerB: match.playerB ?? null,
        winner: match.winner ?? null,
      } : null,
      bracket: this.publicBracket(tournament.tournament_id),
    };
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

  publicBracket(tournamentId: string): {
    tournamentId: string;
    name: string;
    status: string;
    matches: Record<string, unknown>[];
  } {
    const id = validateIdentifier(tournamentId, 'tournamentId');
    const tournament = this.db.prepare(
      'SELECT tournament_id, name, status FROM tournaments WHERE tournament_id = ?',
    ).get(id) as any;
    if (!tournament) throw new ServiceError(404, 'TOURNAMENT_NOT_FOUND', 'Tournament not found.');
    const rows = this.db.prepare(`
      SELECT m.match_id, m.round, m.position, m.status, m.match_code_expires_at,
        pa.display_name AS player_a_name, pb.display_name AS player_b_name,
        pw.display_name AS winner_name
      FROM matches m
      LEFT JOIN participants pa ON pa.participant_id = m.player_a_id
      LEFT JOIN participants pb ON pb.participant_id = m.player_b_id
      LEFT JOIN participants pw ON pw.participant_id = m.winner_id
      WHERE m.tournament_id = ? ORDER BY m.round, m.position
    `).all(id) as any[];
    return {
      tournamentId: id,
      name: tournament.name,
      status: tournament.status,
      matches: rows.map((match) => ({
        matchId: match.match_id,
        round: Number(match.round),
        position: Number(match.position),
        playerA: match.player_a_name ?? null,
        playerB: match.player_b_name ?? null,
        winner: match.winner_name ?? null,
        status: match.status,
        expiresAt: match.match_code_expires_at === null ? null : Number(match.match_code_expires_at),
      })),
    };
  }

  private bracketNodes(tournamentId: string, ids: string[]): any[][] {
    const size = nextPowerOfTwo(ids.length);
    const rounds = Math.log2(size);
    const nodes: any[][] = [[]];
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
    return nodes;
  }

  private insertBracket(tournament: any, ids: string[]): Record<string, unknown>[] {
    const nodes = this.bracketNodes(tournament.tournament_id, ids);
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
        const now = this.timestamp();
        const expires = match.status === 'OPEN' ? now + Number(tournament.match_timeout_seconds) : null;
        insertMatch.run(match.matchId, tournament.tournament_id, match.round, match.position,
          match.playerA, match.playerB, match.possibleA ? 1 : 0, match.possibleB ? 1 : 0,
          match.winner, match.status, match.matchCodeHash, expires, now, now);
        if (match.playerA) insertPlayer.run(match.matchId, match.playerA, ids.indexOf(match.playerA) + 1, 'A');
        if (match.playerB) insertPlayer.run(match.matchId, match.playerB, ids.indexOf(match.playerB) + 1, 'B');
      }
    }
    return nodes.flat().map((match) => this.publicMatch(match));
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
    const tournament = this.tournamentRow(tournamentId);
    if (!tournament) throw new ServiceError(404, 'TOURNAMENT_NOT_FOUND', 'Tournament not found.');
    if (tournament.status !== 'CREATED' && tournament.status !== 'DRAFT') {
      throw new ServiceError(409, 'BRACKET_EXISTS', 'Tournament bracket already seeded.');
    }
    for (const id of ids) {
      if (!this.db.prepare("SELECT 1 FROM participants WHERE participant_id = ? AND status = 'ACTIVE'").get(id)) {
        throw new ServiceError(400, 'INVALID_PARTICIPANT', `Participant ${id} is not active.`);
      }
    }

    const size = nextPowerOfTwo(ids.length);
    const matches = this.transaction(() => {
      const current = this.tournamentRow(tournamentId);
      if (current.status !== 'CREATED' && current.status !== 'DRAFT') {
        throw new ServiceError(409, 'BRACKET_EXISTS', 'Tournament bracket already seeded.');
      }
      const inserted = this.insertBracket(current, ids);
      const now = this.timestamp();
      this.db.prepare("UPDATE tournaments SET status = 'SEEDED', updated_at = ? WHERE tournament_id = ?")
        .run(now, tournamentId);
      this.audit('BRACKET_SEEDED', tournamentId, { participantCount: ids.length, bracketSize: size });
      return inserted;
    });
    return { tournamentId, matches };
  }

  private startTournamentInternal(tournamentId: string, startedBy: 'admin' | 'scheduler'):
    { status: 'RUNNING' | 'START_BLOCKED' } {
    const id = validateIdentifier(tournamentId, 'tournamentId');
    return this.transaction(() => {
      const tournament = this.tournamentRow(id);
      if (tournament.status === 'RUNNING' || tournament.status === 'COMPLETED') {
        return { status: tournament.status as 'RUNNING' };
      }
      if (!canStart(tournament.status)) {
        throw new ServiceError(409, 'TOURNAMENT_NOT_READY', 'Tournament is not ready to start.');
      }
      this.assertNoOtherActiveTournament(id);
      const eligibleStatus = Number(tournament.require_check_in) === 1
        ? "entry_status = 'CHECKED_IN'"
        : "entry_status IN ('REGISTERED', 'CHECKED_IN')";
      const entries = this.db.prepare(`
        SELECT * FROM tournament_entries WHERE tournament_id = ? AND ${eligibleStatus}
        ORDER BY registered_at, participant_id
      `).all(id) as any[];
      if (entries.length < 2) {
        if (tournament.status !== 'START_BLOCKED') {
          const now = this.timestamp();
          assertTransition(tournament.status, 'START_BLOCKED');
          this.db.prepare('UPDATE tournaments SET status = ?, updated_at = ? WHERE tournament_id = ?')
            .run('START_BLOCKED', now, id);
          this.audit('TOURNAMENT_START_BLOCKED', id, { eligibleCount: entries.length });
        }
        return { status: 'START_BLOCKED' };
      }
      if (this.db.prepare('SELECT 1 FROM matches WHERE tournament_id = ? LIMIT 1').get(id)) {
        throw new ServiceError(409, 'BRACKET_EXISTS', 'Tournament bracket already seeded.');
      }
      const ids = entries.map((entry) => String(entry.participant_id));
      const inserted = this.insertBracket(tournament, ids);
      const now = this.timestamp();
      const updateEntry = this.db.prepare(`
        UPDATE tournament_entries SET entry_status = 'ELIGIBLE', seed = ?
        WHERE tournament_id = ? AND participant_id = ?
      `);
      entries.forEach((entry, index) => updateEntry.run(index + 1, id, entry.participant_id));
      this.db.prepare(`
        UPDATE tournaments SET status = 'RUNNING', started_at = ?, started_by = ?,
          roster_frozen_at = ?, updated_at = ? WHERE tournament_id = ?
      `).run(now, startedBy, now, now, id);
      this.audit('TOURNAMENT_STARTED', id, {
        participantCount: entries.length, bracketMatchCount: inserted.length, startedBy,
      });
      return { status: 'RUNNING' };
    });
  }

  startTournament(adminToken: string | undefined, tournamentId: string): Record<string, unknown> {
    this.requireAdmin(adminToken);
    const id = validateIdentifier(tournamentId, 'tournamentId');
    this.startTournamentInternal(id, 'admin');
    return this.adminTournament(adminToken, id);
  }

  processScheduledEvents(): number {
    let changes = 0;
    const blockedThisRun = new Set<string>();
    let progress = true;
    while (progress) {
      progress = false;
      const now = this.timestamp();
      const rows = this.db.prepare(`
        SELECT * FROM tournaments
        WHERE status IN ('DRAFT', 'REGISTRATION_OPEN', 'CHECK_IN', 'READY', 'START_BLOCKED')
        ORDER BY tournament_id
      `).all() as any[];
      for (const row of rows) {
        const id = String(row.tournament_id);
        if (blockedThisRun.has(id)) continue;
        const target = dueTransition({
          status: row.status,
          requireCheckIn: Number(row.require_check_in) === 1,
          autoStart: Number(row.auto_start) === 1,
          registrationOpenAt: row.registration_open_at === null ? null : Number(row.registration_open_at),
          registrationCloseAt: row.registration_close_at === null ? null : Number(row.registration_close_at),
          checkInOpenAt: row.check_in_open_at === null ? null : Number(row.check_in_open_at),
          checkInCloseAt: row.check_in_close_at === null ? null : Number(row.check_in_close_at),
          startAt: row.start_at === null ? null : Number(row.start_at),
        }, now);
        if (!target) continue;
        if (this.otherActiveTournament(id)) continue;
        if (target === 'RUNNING') {
          const result = this.startTournamentInternal(id, 'scheduler');
          changes += 1;
          if (result.status === 'START_BLOCKED') blockedThisRun.add(id);
          else progress = true;
          continue;
        }
        const changed = this.transaction(() => {
          const current = this.tournamentRow(id);
          const currentTarget = dueTransition({
            status: current.status,
            requireCheckIn: Number(current.require_check_in) === 1,
            autoStart: Number(current.auto_start) === 1,
            registrationOpenAt: current.registration_open_at === null ? null : Number(current.registration_open_at),
            registrationCloseAt: current.registration_close_at === null ? null : Number(current.registration_close_at),
            checkInOpenAt: current.check_in_open_at === null ? null : Number(current.check_in_open_at),
            checkInCloseAt: current.check_in_close_at === null ? null : Number(current.check_in_close_at),
            startAt: current.start_at === null ? null : Number(current.start_at),
          }, this.timestamp());
          if (currentTarget !== target) return false;
          this.assertNoOtherActiveTournament(id);
          assertTransition(current.status, target);
          const changedAt = this.timestamp();
          this.db.prepare('UPDATE tournaments SET status = ?, updated_at = ? WHERE tournament_id = ?')
            .run(target, changedAt, id);
          this.audit(`TOURNAMENT_${target}`, id, { from: current.status, to: target, source: 'scheduler' });
          return true;
        });
        if (changed) {
          changes += 1;
          progress = true;
        }
      }
    }
    return changes;
  }

  processExpiredMatches(): string[] {
    const now = this.timestamp();
    const expired = this.db.prepare(`
      SELECT m.match_id, m.player_a_id, m.player_b_id, r.assigned_at
      FROM matches m
      JOIN tournaments t ON t.tournament_id = m.tournament_id
      JOIN room_slots r ON r.tournament_id = m.tournament_id AND r.match_id = m.match_id
      WHERE t.status = 'RUNNING' AND m.status IN ('ASSIGNED', 'IN_PROGRESS')
        AND r.state IN ('ASSIGNED', 'IN_PROGRESS') AND r.assigned_at IS NOT NULL
        AND r.assigned_at + t.match_timeout_seconds <= ?
      ORDER BY r.assigned_at, m.match_id
    `).all(now) as any[];
    const resolved: string[] = [];
    for (const match of expired) {
      if (!match.player_a_id || !match.player_b_id) continue;
      const matchId = String(match.match_id);
      const winnerId = String(randomInt(2) === 0 ? match.player_a_id : match.player_b_id);
      const loserId = winnerId === String(match.player_a_id)
        ? String(match.player_b_id) : String(match.player_a_id);
      const reason = 'TIMEOUT_RANDOM';
      const nonce = randomBytes(16).toString('base64url');
      this.submitResult({
        matchId,
        winnerParticipantId: winnerId,
        loserParticipantId: loserId,
        reason,
        serverNonce: nonce,
        serverSignature: hmac(`${matchId}|${winnerId}|${loserId}|${reason}|${nonce}`, this.roomSecret),
      });
      this.audit('MATCH_TIMEOUT_RANDOM_WINNER', matchId, { winnerId, loserId, assignedAt: Number(match.assigned_at) });
      resolved.push(matchId);
    }
    return resolved;
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
    if (input.displayName !== undefined) {
      const displayName = requiredText(input.displayName, 'displayName', 80);
      this.db.prepare('UPDATE participants SET display_name = ? WHERE participant_id = ?')
        .run(displayName, participant.participant_id);
    }
    return this.issueParticipantSession(participant.participant_id);
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
    if (match.tournament_status !== 'RUNNING') {
      throw new ServiceError(409, 'TOURNAMENT_NOT_RUNNING', 'Tournament matches are not open yet.');
    }
    if (!match.match_code_expires_at || Number(match.match_code_expires_at) <= this.timestamp()) {
      throw new ServiceError(410, 'MATCH_CODE_EXPIRED', 'Match code has expired.');
    }
    if (match.status !== 'OPEN' && match.status !== 'ASSIGNED' && match.status !== 'IN_PROGRESS') {
      throw new ServiceError(409, 'MATCH_CLOSED', 'Match is not accepting players.');
    }
    return match;
  }

  private joinMatchForParticipant(match: MatchRow, participant: any): {
    matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number;
  } {
    const now = this.timestamp();
    return this.transaction(() => {
      let room = this.db.prepare(
        'SELECT * FROM room_slots WHERE tournament_id = ? AND match_id = ?',
      ).get(match.tournament_id, match.match_id) as any;
      let matchExpiresAt = match.match_code_expires_at === null
        ? null : Number(match.match_code_expires_at);
      if (!room) {
        room = this.db.prepare(`
          SELECT * FROM room_slots WHERE tournament_id = ? AND state = 'AVAILABLE'
          ORDER BY room_slot LIMIT 1
        `).get(match.tournament_id) as any;
        if (!room) throw new ServiceError(503, 'ROOM_POOL_EXHAUSTED', 'No tournament room is available.');
        const roomExpiry = now + Number(match.match_timeout_seconds ?? 1200);
        this.db.prepare(`
          UPDATE room_slots SET state = 'ASSIGNED', match_id = ?, assigned_at = ?, heartbeat_at = ?,
            expires_at = ? WHERE tournament_id = ? AND room_slot = ?
        `).run(match.match_id, now, now, roomExpiry, match.tournament_id, room.room_slot);
        this.db.prepare(`
          UPDATE matches SET room_slot = ?, status = 'ASSIGNED', match_code_expires_at = ?, updated_at = ?
          WHERE match_id = ?
        `).run(room.room_slot, roomExpiry, now, match.match_id);
        matchExpiresAt = roomExpiry;
      }
      const expirySeconds = matchExpiresAt === null ? now + 900 : Math.min(matchExpiresAt, now + 900);
      const expiryMillis = expirySeconds * 1000;
      const token = issueRoomToken({
        protocolVersion: this.protocolVersion,
        buildId: this.buildId,
        matchId: match.match_id,
        participantId: participant.participant_id,
        displayName: participant.display_name,
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
        expiresAt: expirySeconds,
      };
    });
  }

  joinMatch(input: JoinInput): { matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number } {
    this.rateLimiter.check(`join:${input?.clientKey ?? 'local'}`, this.timestamp());
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const participant = this.participantForSession(input?.sessionToken);
    const match = this.verifyMatchCode(input?.matchCode);
    if (match.player_a_id !== participant.participant_id && match.player_b_id !== participant.participant_id) {
      throw new ServiceError(403, 'PARTICIPANT_NOT_IN_MATCH', 'Participant is not assigned to this match.');
    }
    return this.joinMatchForParticipant(match, participant);
  }

  joinAssignedMatch(input: AssignedJoinInput): {
    matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number;
  } {
    this.rateLimiter.check(`join:${input?.clientKey ?? 'local'}`, this.timestamp());
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const participant = this.participantForSession(input?.sessionToken);
    const matchId = validateIdentifier(input?.matchId, 'matchId');
    const match = this.db.prepare(`
      SELECT m.*, t.build_id, t.protocol_version, t.match_timeout_seconds, t.status AS tournament_status
      FROM matches m JOIN tournaments t ON t.tournament_id = m.tournament_id
      WHERE m.match_id = ?
    `).get(matchId) as MatchRow | undefined;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match not found.');
    if (match.tournament_status !== 'RUNNING') {
      throw new ServiceError(409, 'TOURNAMENT_NOT_RUNNING', 'Tournament matches are not open yet.');
    }
    if (match.status !== 'OPEN' && match.status !== 'ASSIGNED' && match.status !== 'IN_PROGRESS') {
      throw new ServiceError(409, 'MATCH_CLOSED', 'Match is not accepting players.');
    }
    if (match.status !== 'OPEN' && match.match_code_expires_at !== null
      && Number(match.match_code_expires_at) <= this.timestamp()) {
      throw new ServiceError(410, 'MATCH_EXPIRED', 'This assigned match has expired.');
    }
    if (match.player_a_id !== participant.participant_id && match.player_b_id !== participant.participant_id) {
      throw new ServiceError(403, 'PARTICIPANT_NOT_IN_MATCH', 'Participant is not assigned to this match.');
    }
    return this.joinMatchForParticipant(match, participant);
  }

  joinActiveMatch(input: GameJoinInput): {
    matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number; displayName: string;
  } {
    this.requireBuild(input?.buildId, input?.protocolVersion);
    const requestedPort = Number(input?.roomPort);
    if (!Number.isInteger(requestedPort) || requestedPort < TOURNAMENT_PORT_START || requestedPort > TOURNAMENT_PORT_END) {
      throw new ServiceError(400, 'INVALID_TOURNAMENT_PORT', 'Enter a port from the tournament room range.');
    }
    const active = this.activeTournament();
    if (!active) throw new ServiceError(404, 'NO_ACTIVE_TOURNAMENT', 'There is no active YIMO tournament.');
    if (active.status !== 'RUNNING') {
      throw new ServiceError(409, 'TOURNAMENT_NOT_RUNNING', 'Tournament matches are not open yet.');
    }
    const session = this.createParticipantSession(input, input.clientKey ?? 'local');
    const player = this.playerTournament(session.sessionToken, String(active.tournamentId)) as any;
    if (!player.entry || player.entry.entryStatus !== 'ELIGIBLE') {
      throw new ServiceError(403, 'NOT_ELIGIBLE', 'This candidate is not registered and eligible for the active tournament.');
    }
    if (!player.nextMatch || !['OPEN', 'ASSIGNED', 'IN_PROGRESS'].includes(player.nextMatch.status)) {
      throw new ServiceError(409, 'MATCH_NOT_READY', 'Your next match is not ready yet. Refresh the tournament portal after the other match finishes.');
    }
    const joined = this.joinAssignedMatch({
      sessionToken: session.sessionToken,
      matchId: player.nextMatch.matchId,
      buildId: input.buildId,
      protocolVersion: input.protocolVersion,
      clientKey: input.clientKey,
    });
    if (joined.port !== requestedPort) {
      throw new ServiceError(409, 'WRONG_TOURNAMENT_PORT',
        `This match is assigned to port ${joined.port}.`, { assignedPort: joined.port, matchId: joined.matchId });
    }
    return { ...joined, displayName: player.participant.displayName };
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
    };
  }

  submitResult(input: ResultInput): { duplicate: boolean; matchId: string; resultSignature: string; nextMatch: Record<string, unknown> | null } {
    const matchId = validateIdentifier(input?.matchId, 'matchId');
    const winnerId = validateIdentifier(input?.winnerParticipantId, 'winnerParticipantId');
    const loserId = validateIdentifier(input?.loserParticipantId, 'loserParticipantId');
    const reason = requiredText(input?.reason, 'reason', 80);
    const nonce = requiredText(input?.serverNonce, 'serverNonce', 100);
    const signature = requiredText(input?.serverSignature, 'serverSignature', 100);
    if (!/^[A-Za-z0-9_-]{12,100}$/.test(nonce)) {
      throw new ServiceError(400, 'INVALID_INPUT', 'serverNonce is invalid.');
    }
    if (!safeEqualText(hmac(`${matchId}|${winnerId}|${loserId}|${reason}|${nonce}`, this.roomSecret), signature)) {
      throw new ServiceError(403, 'INVALID_SERVER_SIGNATURE', 'Match result was not signed by the room server.');
    }
    const match = this.db.prepare(`
      SELECT m.*, t.status AS tournament_status FROM matches m
      JOIN tournaments t ON t.tournament_id = m.tournament_id WHERE m.match_id = ?
    `).get(matchId) as MatchRow | undefined;
    if (!match) throw new ServiceError(404, 'MATCH_NOT_FOUND', 'Match not found.');
    if (match.status === 'COMPLETED') {
      if (match.winner_id === winnerId && match.loser_id === loserId && match.result_reason === reason) {
        return { duplicate: true, matchId, resultSignature: match.result_signature, nextMatch: null };
      }
      throw new ServiceError(409, 'RESULT_ALREADY_SUBMITTED', 'A different result is already recorded.');
    }
    const acceptedMatchState = match.status === 'ASSIGNED' || match.status === 'IN_PROGRESS'
      || (reason === 'FORFEIT' && match.status === 'OPEN');
    if (match.tournament_status !== 'RUNNING' || !acceptedMatchState) {
      throw new ServiceError(409, 'MATCH_NOT_ACTIVE', 'Match has not started.');
    }
    const isValidPair = (match.player_a_id === winnerId && match.player_b_id === loserId)
      || (match.player_b_id === winnerId && match.player_a_id === loserId);
    if (!isValidPair) throw new ServiceError(400, 'INVALID_RESULT', 'Winner and loser are not this match pair.');
    return this.transaction(() => {
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
      if (!nextMatch) {
        this.db.prepare("UPDATE tournaments SET status = 'COMPLETED', updated_at = ? WHERE tournament_id = ? AND status = 'RUNNING'")
          .run(now, match.tournament_id);
        this.audit('TOURNAMENT_COMPLETED', match.tournament_id, { championId: winnerId });
      }
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
