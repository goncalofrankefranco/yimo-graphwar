export function applyTournamentMigrations(db: any): void {
  const existing = new Set((db.prepare('PRAGMA table_info(tournaments)').all() as any[])
    .map((column) => column.name));
  const columns: Array<[string, string]> = [
    ['require_check_in', 'INTEGER NOT NULL DEFAULT 0'],
    ['registration_open_at', 'INTEGER'],
    ['registration_close_at', 'INTEGER'],
    ['check_in_open_at', 'INTEGER'],
    ['check_in_close_at', 'INTEGER'],
    ['start_at', 'INTEGER'],
    ['auto_start', 'INTEGER NOT NULL DEFAULT 0'],
    ['started_at', 'INTEGER'],
    ['started_by', 'TEXT'],
    ['roster_frozen_at', 'INTEGER'],
    ['updated_at', 'INTEGER NOT NULL DEFAULT 0'],
  ];
  for (const [name, definition] of columns) {
    if (!existing.has(name)) db.exec(`ALTER TABLE tournaments ADD COLUMN ${name} ${definition}`);
  }
  db.exec('UPDATE tournaments SET updated_at = created_at WHERE updated_at = 0');
  db.exec(`
    CREATE TABLE IF NOT EXISTS tournament_entries (
      tournament_id TEXT NOT NULL REFERENCES tournaments(tournament_id),
      participant_id TEXT NOT NULL REFERENCES participants(participant_id),
      entry_status TEXT NOT NULL,
      registered_at INTEGER NOT NULL,
      checked_in_at INTEGER,
      seed INTEGER,
      PRIMARY KEY(tournament_id, participant_id)
    );
    CREATE INDEX IF NOT EXISTS idx_tournament_entries_status
      ON tournament_entries(tournament_id, entry_status);
    CREATE INDEX IF NOT EXISTS idx_tournament_entries_participant
      ON tournament_entries(participant_id, tournament_id);
  `);
}
