import { BRACKET_HELPERS, PAGE_STYLE } from './pages.ts';

export const PARTICIPANT_PAGE = `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>YIMO Tournament Portal</title>${PAGE_STYLE}
<style>
.login{display:grid;grid-template-columns:minmax(180px,1fr) minmax(180px,1fr) auto auto;gap:12px;align-items:end}
.tournament-summary{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}
.tournament-summary strong{font-size:20px}.tournament-summary span{color:var(--muted)}
.player-summary{min-height:48px;color:var(--muted);line-height:1.55}.primary-action{min-width:220px}
@media(max-width:760px){.login{grid-template-columns:minmax(0,1fr)}.primary-action{width:100%}}
</style></head>
<body><main>
<header><div><div class="brand">YIMO</div><div class="kicker">Olympiad tournament</div><h1>Competitor portal</h1></div>
<a class="link" href="/">YIMO Graphwar</a></header>
<section class="card stack">
  <div id="tournamentSummary" class="tournament-summary"><strong>No active tournament</strong><span>Checking tournament status…</span></div>
  <div class="login">
    <div class="field"><label for="displayName">Display name</label><input id="displayName" autocomplete="nickname" maxlength="80" placeholder="Name shown in the bracket"></div>
    <div class="field"><label for="participantCode">Candidate code</label><input id="participantCode" type="password" autocomplete="one-time-code" placeholder="Organizer-issued code"></div>
    <button id="login">Log in</button><button id="logout" class="secondary">Log out</button>
  </div>
  <div id="portalStatus" class="status" role="status" aria-live="polite">Log in with your candidate code to register or join your assigned match.</div>
  <div class="actions"><button id="refresh" class="secondary">Refresh</button><button id="primaryAction" class="primary-action" disabled>No active tournament</button></div>
  <div id="playerSummary" class="player-summary">Your registration and next match appear here after login.</div>
</section>
<section class="card" style="margin-top:18px"><h2>Public bracket</h2><div id="bracket" class="bracket"></div></section>
</main>${BRACKET_HELPERS}<script>
const statusBox=document.querySelector('#portalStatus'),summary=document.querySelector('#tournamentSummary');
const playerSummary=document.querySelector('#playerSummary'),bracket=document.querySelector('#bracket');
const actionButton=document.querySelector('#primaryAction');
let sessionToken=sessionStorage.getItem('yimoParticipantSession')||'',active=null,player=null,action='';
function message(value,error=false){statusBox.textContent=value;statusBox.className='status '+(error?'error':'ok');}
async function api(path,init={}){let response;try{response=await fetch(path,{...init,headers:{'Content-Type':'application/json',...(sessionToken?{Authorization:'Bearer '+sessionToken}:{}),...(init.headers||{})}});}catch{throw new Error('Could not reach the tournament service. Check your connection and try again.');}let data;try{data=await response.json();}catch{throw new Error('The tournament service returned an unreadable response (HTTP '+response.status+').');}if(response.status===401){sessionToken='';sessionStorage.removeItem('yimoParticipantSession');player=null;throw new Error('Your session expired. Log in again with your candidate code.');}if(!response.ok)throw new Error(data.message||data.error||'Request failed.');return data;}
function setAction(label,next,enabled){actionButton.textContent=label;action=next;actionButton.disabled=!enabled;}
function render(){
  if(!active){summary.replaceChildren(textNode('strong','','No active tournament'),textNode('span','','The organizer has not opened one yet.'));bracket.replaceChildren();player=null;playerSummary.textContent='There is no active YIMO tournament right now.';setAction('No active tournament','',false);return;}
  summary.replaceChildren(textNode('strong','',active.name),textNode('span','',active.status.replaceAll('_',' ')));
  renderBracket(active,bracket);
  if(!sessionToken){player=null;playerSummary.textContent='Log in with your candidate code to register or see your match.';setAction('Log in to continue','',false);return;}
  if(!player){playerSummary.textContent='Loading your tournament entry…';setAction('Refresh your entry','refresh',true);return;}
  const entry=player.entry;
  const match=player.nextMatch;
  playerSummary.textContent=entry?('Signed in as '+player.participant.displayName+' · '+entry.entryStatus.replaceAll('_',' ')+(match?' · Round '+match.round+' · '+(match.playerA||'TBD')+' vs '+(match.playerB||'TBD')+' · '+match.status.replaceAll('_',' '):'')):'Signed in as '+player.participant.displayName+' · not registered for this tournament.';
  if(active.status==='REGISTRATION_OPEN'){
    setAction(entry?'Registered':'Register for tournament',entry?'': 'register',!entry);
  }else if(active.status==='CHECK_IN'){
    setAction(entry&&entry.entryStatus==='CHECKED_IN'?'Checked in':'Check in',entry&&entry.entryStatus==='CHECKED_IN'?'':'check-in',!!entry&&entry.entryStatus!=='CHECKED_IN');
  }else if(active.status==='RUNNING'&&entry&&entry.entryStatus==='ELIGIBLE'){
    if(!match){setAction('Tournament complete','',false);}
    else if(['OPEN','ASSIGNED','IN_PROGRESS'].includes(match.status)){setAction('Join Room','join',true);}
    else{setAction('Waiting for next match','refresh',true);}
  }else{
    setAction(entry?'Waiting for tournament':'Not registered','refresh',!!entry);
  }
}
async function refresh(showMessage=true){
  try{
    active=await api('/api/v1/tournaments/active');player=null;
    if(active&&sessionToken)player=await api('/api/v1/player/tournaments/'+encodeURIComponent(active.tournamentId));
    render();if(showMessage)message(active?'Tournament status refreshed.':'No tournament is active yet.');
  }catch(error){if(!sessionToken)render();if(showMessage)message(error.message,true);}
}
async function login(){
  try{
    const participantCode=document.querySelector('#participantCode').value.trim();
    const displayName=document.querySelector('#displayName').value.trim();
    if(!participantCode||!displayName)throw new Error('Enter your candidate code and display name.');
    const data=await api('/api/v1/participant-sessions',{method:'POST',body:JSON.stringify({participantCode,displayName,buildId:'YIMO-Graphwar-2.2.0',protocolVersion:2})});
    sessionToken=data.sessionToken;sessionStorage.setItem('yimoParticipantSession',sessionToken);await refresh(false);message('Logged in as '+displayName+'.');
  }catch(error){message(error.message,true);}
}
async function runAction(){
  if(!active||!player||!action)return;
  try{
    if(action==='refresh'){await refresh(false);message('Tournament status refreshed.');return;}
    if(action==='register'||action==='check-in'){
      await api('/api/v1/tournaments/'+encodeURIComponent(active.tournamentId)+'/'+action,{method:'POST',body:'{}'});
      await refresh(false);message(action==='register'?'Registration confirmed.':'Check-in confirmed.');return;
    }
    if(action==='join'){
      const joined=await api('/api/v1/matches/'+encodeURIComponent(player.nextMatch.matchId)+'/join-assigned',{method:'POST',body:JSON.stringify({buildId:'YIMO-Graphwar-2.2.0',protocolVersion:2})});
      message('Match assigned to port '+joined.port+'. In YIMO Graphwar, click “Join Room”, enter this port, then enter your candidate code.');
    }
  }catch(error){message(error.message,true);}
}
document.querySelector('#login').addEventListener('click',login);
document.querySelector('#displayName').addEventListener('keydown',event=>{if(event.key==='Enter')login();});
document.querySelector('#participantCode').addEventListener('keydown',event=>{if(event.key==='Enter')login();});
document.querySelector('#logout').addEventListener('click',()=>{sessionToken='';player=null;sessionStorage.removeItem('yimoParticipantSession');render();message('Logged out.');});
document.querySelector('#refresh').addEventListener('click',()=>refresh(true));actionButton.addEventListener('click',runAction);
refresh(false);window.setInterval(()=>refresh(false),7000);
</script></body></html>`;
