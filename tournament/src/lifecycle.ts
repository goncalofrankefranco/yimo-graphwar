export type TournamentStatus =
  | 'DRAFT'
  | 'REGISTRATION_OPEN'
  | 'CHECK_IN'
  | 'READY'
  | 'RUNNING'
  | 'START_BLOCKED'
  | 'COMPLETED'
  | 'SEEDED';

export interface ScheduleRow {
  status: TournamentStatus;
  requireCheckIn: boolean;
  autoStart: boolean;
  registrationOpenAt: number | null;
  registrationCloseAt: number | null;
  checkInOpenAt: number | null;
  checkInCloseAt: number | null;
  startAt: number | null;
}

const ALLOWED_TRANSITIONS: Record<TournamentStatus, readonly TournamentStatus[]> = {
  DRAFT: ['REGISTRATION_OPEN'],
  REGISTRATION_OPEN: ['CHECK_IN', 'READY'],
  CHECK_IN: ['READY'],
  READY: ['RUNNING', 'START_BLOCKED'],
  START_BLOCKED: ['RUNNING'],
  RUNNING: ['COMPLETED'],
  COMPLETED: [],
  SEEDED: [],
};

export function assertTransition(from: TournamentStatus, to: TournamentStatus): void {
  if (!ALLOWED_TRANSITIONS[from]?.includes(to)) {
    const error = new Error(`Tournament cannot transition from ${from} to ${to}.`) as Error & {
      status: number;
      code: string;
    };
    error.status = 409;
    error.code = 'INVALID_STATUS_TRANSITION';
    throw error;
  }
}

export function canStart(status: TournamentStatus): boolean {
  return status === 'READY' || status === 'START_BLOCKED';
}

export function dueTransition(row: ScheduleRow, now: number): TournamentStatus | null {
  if (row.status === 'DRAFT' && row.registrationOpenAt !== null
    && now >= row.registrationOpenAt) {
    return 'REGISTRATION_OPEN';
  }
  if (row.status === 'REGISTRATION_OPEN' && row.registrationCloseAt !== null
    && now >= row.registrationCloseAt) {
    return row.requireCheckIn ? 'CHECK_IN' : 'READY';
  }
  if (row.status === 'CHECK_IN' && row.checkInCloseAt !== null
    && now >= row.checkInCloseAt) {
    return 'READY';
  }
  if ((row.status === 'READY' || row.status === 'START_BLOCKED') && row.autoStart
    && row.startAt !== null && now >= row.startAt) {
    return 'RUNNING';
  }
  return null;
}
