import { PARTICIPANT_PAGE as LEGACY_PARTICIPANT_PAGE } from './pages.ts';

const SELF_REGISTRATION_PANEL = `<div class="toolbar"><div class="field"><label for="playerId">Local player ID</label><input id="playerId" autocomplete="off" placeholder="Generate in the app or here"></div><div class="field"><label for="displayName">Display name</label><input id="displayName" autocomplete="nickname" placeholder="Your display name"></div><button id="generatePlayerId" class="secondary">Generate player ID</button><button id="selfRegister">Register with player ID</button></div>`;

export const PARTICIPANT_PAGE = LEGACY_PARTICIPANT_PAGE
  .replace('<div class="toolbar"><div class="field"><label for="participantCode">',
    SELF_REGISTRATION_PANEL + '<div class="toolbar"><div class="field"><label for="participantCode">');
