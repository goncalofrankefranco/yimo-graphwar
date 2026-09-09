import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { applyTournamentMigrations } from '../src/migrations.ts';

test('migrates a legacy tournament schema idempotently', () => {
  const db = new DatabaseSync(':memory:');
  db.exec(`
    CREATE TABLE tournaments (
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
    CREATE TABLE participants (
      participant_id TEXT PRIMARY KEY
    );
  `);
  applyTournamentMigrations(db);
  applyTournamentMigrations(db);

  const columns = (db.prepare('PRAGMA table_info(tournaments)').all() as any[])
    .map((column) => column.name);
  for (const column of [
    'require_check_in', 'registration_open_at', 'registration_close_at',
    'check_in_open_at', 'check_in_close_at', 'start_at', 'auto_start',
    'started_at', 'started_by', 'roster_frozen_at', 'updated_at',
  ]) assert.ok(columns.includes(column), `missing ${column}`);
  assert.ok(db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'tournament_entries'").get());
  assert.ok(db.prepare("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_tournament_entries_status'").get());
  assert.equal((db.prepare("SELECT COUNT(*) AS count FROM sqlite_master WHERE name = 'tournament_entries'").get() as any).count, 1);
  db.close();
});
