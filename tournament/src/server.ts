import { createServer } from 'node:http';
import { ServiceError, TournamentService } from './service.ts';

function send(response: any, status: number, body: unknown, contentType = 'application/json; charset=utf-8'): void {
  response.writeHead(status, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  response.end(contentType.startsWith('application/json') ? JSON.stringify(body) : String(body));
}

function bearer(request: any): string | undefined {
  const value = request.headers.authorization;
  return typeof value === 'string' && value.startsWith('Bearer ') ? value.slice(7) : undefined;
}

function readJson(request: any): Promise<any> {
  return new Promise((resolve, reject) => {
    let data = '';
    request.on('data', (chunk: Buffer) => {
      data += chunk.toString('utf8');
      if (data.length > 1_000_000) reject(new ServiceError(413, 'PAYLOAD_TOO_LARGE', 'Request body is too large.'));
    });
    request.on('end', () => {
      if (!data.trim()) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch {
        reject(new ServiceError(400, 'INVALID_JSON', 'Request body must be valid JSON.'));
      }
    });
    request.on('error', reject);
  });
}

const PAGE_STYLE = `<style>
:root{color-scheme:light;--ink:#102e2a;--muted:#66736f;--paper:#fffdf8;--line:#d8d0c4;--orange:#ee8c39;--mint:#edf5ee}
*{box-sizing:border-box}body{margin:0;background:#f4f0e8;color:var(--ink);font:15px system-ui,-apple-system,Segoe UI,sans-serif}
main{max-width:1180px;margin:0 auto;padding:32px 24px 56px}header{display:flex;align-items:end;justify-content:space-between;gap:24px;margin-bottom:26px}
.brand{font:700 28px Georgia,serif;color:#c86e2c;letter-spacing:.04em}.kicker{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.14em}
h1{margin:4px 0 0;font-size:32px}h2{font-size:18px;margin:0}.card{background:var(--paper);border:1px solid var(--line);border-radius:18px;padding:20px;box-shadow:0 10px 28px #102e2a12}
.toolbar{display:flex;flex-wrap:wrap;align-items:end;gap:12px;margin-bottom:18px}.field{display:flex;flex-direction:column;gap:6px;min-width:260px;flex:1}label{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em}
input{border:1px solid #bdb5a9;border-radius:10px;padding:11px 12px;background:#fff;color:var(--ink);font:inherit}button{border:0;border-radius:999px;background:var(--orange);color:#172e2a;padding:11px 20px;font-weight:700;cursor:pointer}button:hover{filter:brightness(1.05);transform:translateY(-1px)}
#status{color:var(--muted);min-height:22px;margin:10px 0 18px}.bracket{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(210px,1fr);gap:14px;overflow-x:auto;padding-bottom:8px}.round{display:flex;flex-direction:column;gap:12px}.round h2{font-size:13px;text-transform:uppercase;letter-spacing:.1em;color:#c86e2c}
.match{background:#fff;border:1px solid var(--line);border-left:4px solid #b8c9c1;border-radius:12px;padding:11px;min-height:96px;display:flex;flex-direction:column;justify-content:center;gap:4px}.match.open{border-left-color:var(--orange)}.match.completed{border-left-color:#2f83d7}.match.bye{background:var(--mint)}
.player{display:flex;justify-content:space-between;gap:8px;font-weight:650}.player.empty{color:#9aa39e;font-weight:400;font-style:italic}.winner{color:#2f83d7}.meta{color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:.08em;margin-top:4px}
.note{color:var(--muted);font-size:13px;line-height:1.55;margin-top:18px}.link{color:#c86e2c;text-decoration:none;font-weight:700}
</style>`;
const BRACKET_SCRIPT = `<script>
const params=new URLSearchParams(location.search);
const input=document.querySelector('#tournament');
input.value=params.get('tournament')||'yimo-demo-2026';
const status=document.querySelector('#status');
const bracket=document.querySelector('#bracket');
function esc(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function player(name,winner){return name?'<div class="player '+(winner?'winner':'')+'">'+esc(name)+(winner?' ✓':'')+'</div>':'<div class="player empty">Waiting for player</div>';}
function render(data){
  const rounds=new Map();
  for(const match of data.matches){if(!rounds.has(match.round))rounds.set(match.round,[]);rounds.get(match.round).push(match);}
  bracket.innerHTML='';
  for(const [round,matches] of rounds){
    const column=document.createElement('section'); column.className='round';
    column.innerHTML='<h2>Round '+round+'</h2>'+matches.map(match=>{
      const state=String(match.status).toLowerCase();
      return '<article class="match '+state+'">'+player(match.playerA,match.winner===match.playerA)+player(match.playerB,match.winner===match.playerB)+'<div class="meta">'+esc(match.status)+' · '+esc(match.matchId)+'</div></article>';
    }).join('');
    bracket.appendChild(column);
  }
  status.textContent=data.name+' · '+data.status+' · '+data.matches.length+' matches';
}
async function load(){
  const id=input.value.trim(); if(!id){status.textContent='Enter a tournament ID.';return;}
  status.textContent='Loading bracket…'; bracket.innerHTML='';
  try{const response=await fetch('/api/v1/tournaments/'+encodeURIComponent(id)+'/bracket');const data=await response.json();if(!response.ok)throw new Error(data.message||data.error);render(data);}
  catch(error){status.textContent='Unable to load bracket: '+error.message;}
}
document.querySelector('#load').addEventListener('click',load); input.addEventListener('keydown',event=>{if(event.key==='Enter')load();}); load();
</script>`;
const ADMIN_PAGE = `<!doctype html><html><head><meta charset="utf-8"><title>YIMO Tournament Admin</title>${PAGE_STYLE}</head>
<body><main><header><div><div class="brand">YIMO</div><div class="kicker">Tournament control</div><h1>Organizer console</h1></div><a class="link" href="/participant">Open bracket view →</a></header>
<section class="card"><h2>API operations</h2><p class="note">Use the authenticated JSON API to register participants, create tournaments, seed brackets, allocate rooms, and record authoritative results.</p>
<p><code>POST /api/v1/admin/participants</code><br><code>POST /api/v1/admin/tournaments</code><br><code>POST /api/v1/admin/bracket/seed</code></p></section></main></body></html>`;
const PARTICIPANT_PAGE = `<!doctype html><html><head><meta charset="utf-8"><title>YIMO Tournament Bracket</title>${PAGE_STYLE}</head>
<body><main><header><div><div class="brand">YIMO</div><div class="kicker">Olympiad tournament</div><h1>Live bracket</h1></div><a class="link" href="/admin">Organizer console →</a></header>
<section class="card"><div class="toolbar"><div class="field"><label for="tournament">Tournament ID</label><input id="tournament" autocomplete="off"></div><button id="load">Refresh bracket</button></div><div id="status">Enter a tournament ID.</div><div id="bracket" class="bracket"></div><p class="note">This public view shows progress only. Match codes and signed room tokens are never exposed here.</p></section></main>${BRACKET_SCRIPT}</body></html>`;

export function createTournamentHttpServer(service: TournamentService): any {
  return createServer(async (request: any, response: any) => {
    if (request.method === 'OPTIONS') {
      response.writeHead(204, { 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', 'Access-Control-Allow-Headers': 'Authorization,Content-Type' });
      response.end();
      return;
    }
    const url = new URL(request.url ?? '/', 'http://localhost');
    try {
      if (request.method === 'GET' && url.pathname === '/healthz') {
        send(response, 200, { ok: true, buildId: service.buildId, protocolVersion: service.protocolVersion });
        return;
      }
      if (request.method === 'GET' && url.pathname === '/admin') {
        send(response, 200, ADMIN_PAGE, 'text/html; charset=utf-8');
        return;
      }
      if (request.method === 'GET' && url.pathname === '/participant') {
        send(response, 200, PARTICIPANT_PAGE, 'text/html; charset=utf-8');
        return;
      }
      const isBracketRoute = request.method === 'GET' && /^\/api\/v1\/tournaments\/[^/]+\/bracket$/.test(url.pathname);
      if (request.method !== 'POST' && !(request.method === 'GET' && url.pathname === '/api/v1/player/matches') && !isBracketRoute) {
        throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      }

      const body = request.method === 'POST' ? await readJson(request) : {};
      const clientKey = request.socket?.remoteAddress ?? 'unknown';
      if (request.method === 'POST' && url.pathname === '/api/v1/admin/participants') {
        send(response, 201, service.addParticipant(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/tournaments') {
        send(response, 201, service.createTournament(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/admin/bracket/seed') {
        send(response, 201, service.seedBracket(bearer(request), body));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/participant-sessions') {
        send(response, 200, service.createParticipantSession(body, clientKey));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/matches/join') {
        send(response, 200, service.joinMatch({ ...body, clientKey }));
      } else if (request.method === 'POST' && url.pathname === '/api/v1/rooms/heartbeat') {
        send(response, 200, service.heartbeat(body));
      } else if (request.method === 'POST' && /^\/api\/v1\/matches\/[^/]+\/result$/.test(url.pathname)) {
        const matchId = url.pathname.split('/')[4];
        send(response, 200, service.submitResult({ ...body, matchId }));
      } else if (request.method === 'GET' && url.pathname === '/api/v1/player/matches') {
        send(response, 200, { matches: service.playerMatches(bearer(request) ?? url.searchParams.get('sessionToken') ?? '') });
      } else if (isBracketRoute) {
        send(response, 200, service.publicBracket(url.pathname.split('/')[4]));
      } else {
        throw new ServiceError(404, 'NOT_FOUND', 'Route not found.');
      }
    } catch (error: any) {
      if (error instanceof ServiceError) {
        send(response, error.status, { error: error.code, message: error.message, details: error.details });
      } else {
        send(response, 500, { error: 'INTERNAL_ERROR', message: 'The tournament service failed to process the request.' });
      }
    }
  });
}
