import { ADMIN_PAGE as LEGACY_ADMIN_PAGE } from './pages.ts';

export const ADMIN_PAGE = LEGACY_ADMIN_PAGE.replace(
  '.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}',
  '.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.form-grid .field{min-width:0}',
);
