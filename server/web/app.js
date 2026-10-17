import {homeUI} from './home.js';
import {appearanceStudio,guideMetrics} from './appearance.js';
import {safeChannelLogo,bindChannelActions,channelActionMenu} from './channel-actions.js';
import {timeshiftUI,seekWithinWindow} from './timeshift.js';
import {discoveryUI,guideDensities} from './discovery.js';
import {recordingUI} from './recordings.js';
import {metadataPanel,metadataSettings,guideMetadata} from './metadata.js';
import {VideoWall} from './wall.js';
import {providerGroups,inGroup,clipProgramme,guideCells,orderedGroups,virtualRange} from './guide.js';
const $=id=>document.getElementById(id),phoneLayout=matchMedia('(max-width:700px)');
let rowHeight=62;
let duration=phoneLayout.matches?3600000:3*3600000;
let token=sessionStorage.getItem('iptv-admin')||'',channels=[],customGroups=[],selected='all',start=Date.now(),guide=[],guideRevision=0,timer;
let preferences={favorites:[],groupOrder:[],hiddenGroups:[]},arranging=false,preferenceBusy=false,scrollTimer;const guideCache=new Map();let lastWindow="";
let active=null,authSetup=false;
const time=value=>new Date(value).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
async function api(path,method='GET',body,auth=token){const r=await fetch('/api/v1'+path,{method,headers:{Authorization:'Bearer '+auth,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const data=await r.json();if(!r.ok)throw new Error(data.error||'Request failed');return data}
function message(text,error=false){$('message').textContent=text;$('message').className=error?'error':'';$('message').style.display='block';clearTimeout(timer);timer=setTimeout(()=>$('message').style.display='none',8000)}
function el(tag,text,className){const e=document.createElement(tag);if(text)e.textContent=text;if(className)e.className=className;return e}
function button(text,action,className='secondary'){const b=el('button',text,className);b.type='button';b.onclick=async()=>{b.disabled=true;try{await action()}catch(e){message(e.message,true)}finally{b.disabled=false}};return b}
function row(container,title,subtitle,action){const r=el('div'),info=el('span');info.append(el('strong',title));if(subtitle)info.append(el('small',subtitle));r.append(info);if(action)r.append(action);container.append(r)}
function allGroups(){return [{id:'favorites',name:'★ Favorites',channelIds:preferences.favorites},...orderedGroups([{id:'all',name:'All channels',channelIds:channels.map(c=>c.id)},...customGroups.map(g=>({...g,id:'custom:'+g.id})),...providerGroups(channels)],preferences.groupOrder)]}
function selectedGroup(){return allGroups().find(g=>g.id===selected)}
function filtered(){return inGroup(channels,selectedGroup(),$('search').value)}
function range(){return virtualRange(filtered().length,$('guide-viewport').scrollTop,$('guide-viewport').clientHeight||500,rowHeight)}
function visible(){const r=range();return filtered().slice(r.start,r.end)}
function resetScroll(){$('guide-viewport').scrollTop=0}
async function savePreference(patch){if(preferenceBusy)return;preferenceBusy=true;renderGroups();renderGuide();try{preferences=await api('/guide/preferences','PUT',patch);if(preferences.hiddenGroups.includes(selected)){selected='favorites';resetScroll()}}finally{preferenceBusy=false;renderGroups();renderGuide()}}
async function favorite(c){const ids=new Set(preferences.favorites);if(ids.has(c.id))ids.delete(c.id);else{if(ids.size>=500)throw Error('Maximum 500 favorites');ids.add(c.id)}await savePreference({favorites:[...ids]});await loadGuide()}
function renderGroups(){
 const box=$('groups'),scroll=box.parentElement.scrollTop;box.replaceChildren();const groups=allGroups();
 groups.forEach((g,index)=>{if(!arranging&&preferences.hiddenGroups.includes(g.id))return;const row=el('div',null,'group-entry'+(g.id==='favorites'?' pinned':''));const select=button(g.name+' · '+g.channelIds.length,()=>{selected=g.id;setGroups(false);resetScroll();renderGroups();renderGuide();return loadGuide()},selected===g.id?'active':'');row.append(select);
 if(arranging&&g.id!=='favorites'){
  const move=async delta=>{const ids=groups.slice(1).map(g=>g.id),from=index-1;const id=ids.splice(from,1)[0];ids.splice(from+delta,0,id);await savePreference({groupOrder:ids});const entry=[...box.querySelectorAll('[data-group-id]')].find(e=>e.dataset.groupId===g.id);entry?.focus({preventScroll:true})};
  const up=button('↑',()=>move(-1),'move-group'),down=button('↓',()=>move(1),'move-group');up.setAttribute('aria-label','Move '+g.name+' up');down.setAttribute('aria-label','Move '+g.name+' down');up.disabled=index===1||preferenceBusy;down.disabled=index===groups.length-1||preferenceBusy;const hide=button(preferences.hiddenGroups.includes(g.id)?'Show':'Hide',()=>{const hidden=new Set(preferences.hiddenGroups);if(hidden.has(g.id))hidden.delete(g.id);else hidden.add(g.id);return savePreference({hiddenGroups:[...hidden]})},'visibility-group');hide.setAttribute('aria-label',(preferences.hiddenGroups.includes(g.id)?'Show ':'Hide ')+g.name);hide.disabled=preferenceBusy;row.append(hide,up,down);
 }select.dataset.groupId=g.id;box.append(row)});
 box.parentElement.scrollTop=scroll;$('group-title').textContent=selectedGroup()?.name||'All channels';$('edit-group').hidden=!selected.startsWith('custom:');$('reorder-groups').textContent=arranging?'Done managing':'Manage groups';
}
function openChannelActions(c,p){
 const focused=document.activeElement?.dataset.focusKey;
 const run=fn=>()=>Promise.resolve().then(fn).catch(e=>message(e.message,true));
 channelActionMenu({channel:c,programme:p,favorite:preferences.favorites.includes(c.id),onWatch:run(()=>tune(c)),onFavorite:run(()=>favorite(c)),onDetails:()=>p?dvr.programme(c,p,()=>tune(c)):message('No EPG data for this channel'),onRecord:()=>dvr.schedule(c,p),onClose:()=>{[...document.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey===focused)?.focus({preventScroll:true})}});
}
function renderGuide(force=true){
 const list=filtered(),viewport=$('guide-viewport');if(viewport.scrollTop>Math.max(0,list.length*rowHeight-viewport.clientHeight))viewport.scrollTop=Math.max(0,list.length*rowHeight-viewport.clientHeight);
 const r=range(),signature=[selected,$('search').value,start,r.start,r.end,list.length].join('|');if(!force&&signature===lastWindow)return;lastWindow=signature;const focus=document.activeElement?.dataset.focusKey;
 $('time-labels').replaceChildren(...Array.from({length:duration/1800000},(_,i)=>el('span',time(start+i*1800000))));$('guide-date').textContent=new Date(start).toLocaleDateString([],{weekday:'short',month:'short',day:'numeric'});
 const enrichment=[];const target=$('guide-rows');target.replaceChildren();const spacer=height=>{const e=el('div');e.style.height=height+'px';e.setAttribute('aria-hidden','true');return e};target.append(spacer(r.top));
 for(const c of list.slice(r.start,r.end)){
  const row=el('div',null,'guide-row'),label=el('div',null,'channel-cell');
  const star=button(preferences.favorites.includes(c.id)?'★':'☆',()=>favorite(c),'favorite');star.setAttribute('aria-label',(preferences.favorites.includes(c.id)?'Remove '+c.name+' from':'Add '+c.name+' to')+' favorites');star.setAttribute('aria-pressed',String(preferences.favorites.includes(c.id)));star.dataset.focusKey='favorite:'+c.id;star.disabled=preferenceBusy;
  const tuneButton=button(c.name,()=>tune(c),'channel-name');tuneButton.dataset.focusKey='channel:'+c.id;
  const logo=safeChannelLogo(c.logo);if(logo){const image=el('img');image.src=logo;image.alt='';image.className='channel-logo';image.loading='lazy';image.decoding='async';image.referrerPolicy='no-referrer';image.onerror=()=>image.remove();tuneButton.prepend(image)}
  bindChannelActions(tuneButton,()=>openChannelActions(c,guideCache.get(c.id)?.programmes.find(p=>Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now())));label.append(star,tuneButton);row.append(label);
  row.addEventListener('keydown',event=>{if(['ArrowLeft','ArrowRight'].includes(event.key)&&!event.target.classList.contains('favorite')){event.preventDefault();shiftGuide(event.key==='ArrowRight'?1800000:-1800000);[...target.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey==='channel:'+c.id)?.focus({preventScroll:true});return;}if(!['ArrowDown','ArrowUp'].includes(event.key))return;event.preventDefault();const index=list.findIndex(item=>item.id===c.id)+(event.key==='ArrowDown'?1:-1);if(index<0||index>=list.length)return;const y=index*rowHeight;if(y<viewport.scrollTop)viewport.scrollTop=y;else if(y+rowHeight>viewport.scrollTop+viewport.clientHeight)viewport.scrollTop=y+rowHeight-viewport.clientHeight;renderGuide();const key=(event.target.classList.contains('favorite')?'favorite:':'channel:')+list[index].id;[...target.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey===key)?.focus({preventScroll:true});scheduleGuide()});
  const track=el('div',null,'programme-track');const cached=guideCache.get(c.id);
  for(const cell of guideCells(cached?.programmes||[],start,duration)){
   const p=cell.programme,b=p?button(p.title?.trim()||'Untitled programme',()=>{dvr.programme(c,p,()=>tune(c))},'programme'+(Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now()?' live':'')):el('div',cached?'No EPG data':'Loading EPG…','programme epg-gap');
   b.style.left=(cell.start-start)/duration*100+'%';b.style.width=(cell.end-cell.start)/duration*100+'%';
   if(p){bindChannelActions(b,()=>openChannelActions(c,p));enrichment.push({p,element:b});b.dataset.focusKey='programme:'+c.id+':'+p.start;b.title=(p.title?.trim()||'Untitled programme')+' · '+time(p.start)+'–'+time(p.end);b.append(el('small',time(p.start)+'–'+time(p.end)));b.addEventListener('focus',()=>metadata.show(p));b.addEventListener('pointerenter',()=>metadata.show(p))}else b.title=cached?'No EPG data for this time':'Loading EPG';track.append(b);
  }
  if(Date.now()>=start&&Date.now()<start+duration){const line=el('div',null,'now-line');line.style.left=(Date.now()-start)/duration*100+'%';track.append(line)}row.append(track);target.append(row);
 }target.append(spacer(r.bottom));if(!list.length)target.append(el('p',selected==='favorites'?'No favorites yet. Use the star beside any channel.':'No channels match this view.'));$('channel-total').textContent=list.length+' channels · Scroll to browse';
 if(focus)[...target.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey===focus)?.focus({preventScroll:true});updateNowPlaying();guideEnrichment.show(enrichment);
}
function updateNowPlaying(){if(!active)return;const p=(guideCache.get(active.channelId)?.programmes||[]).find(p=>Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now());$('playing-programme').textContent=p?p.title+' · '+time(p.start)+'–'+time(p.end):'Live channel · programme information unavailable';if(!document.activeElement?.classList.contains('programme'))metadata.show(p)}
async function loadGuide(){
 const revision=++guideRevision,windowStart=Math.floor(start/21600000)*21600000,ids=visible().filter(c=>{const cached=guideCache.get(c.id);return !cached||cached.start!==windowStart||Date.now()-cached.fetched>60000}).map(c=>c.id);if(!ids.length)return
 $('epg-message').textContent='Loading guide…';try{const data=await api('/guide?'+new URLSearchParams({channelIds:ids.join(','),from:new Date(windowStart).toISOString(),to:new Date(windowStart+43200000).toISOString()}));if(windowStart!==Math.floor(start/21600000)*21600000||revision!==guideRevision)return;
 const byChannel=new Map(ids.map(id=>[id,[]]));for(const p of data.programmes)byChannel.get(p.channelId)?.push(p);for(const id of ids){guideCache.set(id,{start:windowStart,fetched:Date.now(),programmes:byChannel.get(id)})}while(guideCache.size>300)guideCache.delete(guideCache.keys().next().value);
 $('epg-message').textContent='Local times · select a channel to watch · select a programme to record';$('guide-status').textContent=data.status;renderGuide()}catch(e){if(revision===guideRevision){for(const id of ids)guideCache.set(id,{start:windowStart,fetched:Date.now(),programmes:guideCache.get(id)?.programmes||[]});$('epg-message').textContent='EPG unavailable · retrying automatically';renderGuide()}}
}
function scheduleGuide(){clearTimeout(scrollTimer);scrollTimer=setTimeout(()=>loadGuide(),140)}
async function loadCatalog(){[channels,customGroups,preferences]=await Promise.all([api('/channels'),api('/groups'),api('/guide/preferences')]);$('channel-count').textContent=channels.length;renderGroups();renderGuide();await loadGuide()}
const wall=new VideoWall({host:$('wall'),api,onError:e=>message(e.message,true),onChange:w=>{
 $('back-wall').textContent=w.capacity>1&&w.host.classList.contains('tile-fullscreen')?'‹ Back to wall':'‹ Back to guide';
 active=w.selected?.session||null;
 if(phoneLayout.matches&&w.selected?.status.startsWith('Select Enable audio'))$('player-controls').open=true;
 $('player-placeholder').hidden=w.tiles.length>0;
 $('stop-player').disabled=!w.tiles.length;
 $('playing-title').textContent=w.selected?.channel.name||'Choose a channel';
 $('playback-status').textContent=w.selected?.status||'Ready to play';
 $('wall-count').textContent=w.tiles.length+' / '+w.capacity+' streams';
 $('pause-live').textContent=w.selected?.video.paused?'Resume':'Pause';
 const v=w.selected?.video;const behind=v?.seekable.length?Math.max(0,v.seekable.end(v.seekable.length-1)-v.currentTime):0;$('live-offset').textContent=behind>8?Math.floor(behind)+'s behind live':'Live';
 $('wall-layout').value=String(w.capacity);
 $('remove-tile').disabled=!w.selected;
 if(active)updateNowPlaying();else $('playing-programme').textContent='Browse your groups and live programme guide.';
 const h=active?.health;
 $('stream-health').textContent=h?`Output age ${(h.segmentAgeMs/1000).toFixed(1)}s · ${h.restarts} worker restarts · ${(h.scratchBytes/1048576).toFixed(1)} MB scratch`:'';
}});wall.auth=()=>token;
function tune(channel){discovery.watched(channel.id);return wall.add(channel).catch(e=>message(e.message,true));}
async function refreshAdmin(){if(!token)return;const [ss,providers,pairs,devices]=await Promise.all(['/playback/sessions','/providers','/pairing/requests','/devices'].map(p=>api(p)));$('session-count').textContent=ss.length;$('device-count').textContent=devices.length;$('providers').replaceChildren();providers.forEach(p=>{const label=el('label',null,'check'),check=el('input');check.type='checkbox';check.checked=p.allowHttp;check.onchange=async()=>{check.disabled=true;try{await api('/providers/'+p.id+'/transport','PUT',{allowHttp:check.checked});message('Transport setting saved. Restart playback to apply.')}catch(e){check.checked=!check.checked;message(e.message,true)}finally{check.disabled=false}};label.append(check,document.createTextNode('Allow HTTP streams / redirects (unencrypted)'));const actions=el('span');actions.append(label,button('Edit',()=>editProvider(p)),button('Delete login',async()=>{if(!confirm('Delete this saved login? Favorites and groups will be kept.'))return;try{await api('/providers/'+p.id,'DELETE');await loadCatalog();await refreshAdmin()}catch(e){message(e.message,true)}}));row($('providers'),p.name+' · '+p.username,p.url+' · '+p.format.toUpperCase()+' · '+(Object.entries(p.imports||{}).map(([kind,state])=>kind+': '+state.message+' · retry '+new Date(state.nextRetry).toLocaleTimeString()).join(' / ')||'Connection saved'),actions)});$('pairings').replaceChildren();if(!pairs.length)row($('pairings'),'Waiting for a pairing request');pairs.forEach(p=>row($('pairings'),p.name+' · '+p.code,'Verify this code matches your TV',button('Approve',async()=>{await api('/pairing/approve','POST',{id:p.id,code:p.code});await refreshAdmin()})));$('devices').replaceChildren();devices.forEach(d=>row($('devices'),d.name,'Companion access',button('Revoke',async()=>{await api('/devices/'+d.id,'DELETE');await refreshAdmin()})));$('sessions').replaceChildren();ss.forEach(s=>{const actions=el('span');const details=el('details'),summary=el('summary','Recovery history');details.append(summary);for(const event of s.health?.events||[])details.append(el('small',time(event.at)+' · '+event.state+' · '+event.reason));actions.append(details);if(s.channelId==='demo:1')actions.append(button('Interrupt test',()=>api('/playback/sessions/'+s.id+'/interrupt','POST',{})));actions.append(button('Stop',async()=>{const tile=wall.tiles.find(t=>t.session.id===s.id);if(tile){await wall.enqueue(()=>wall.removeTile(tile));if(!wall.tiles.length)wall.capacity=1;wall.render();}else await api('/playback/sessions/'+s.id,'DELETE');await refreshAdmin()}));row($('sessions'),channels.find(c=>c.id===s.channelId)?.name||s.channelId,s.state+' · '+s.reason+' · generation '+s.generation+' · output age '+((s.health?.segmentAgeMs||0)/1000).toFixed(1)+'s · restarts '+(s.health?.restarts||0)+' · scratch '+((s.health?.scratchBytes||0)/1048576).toFixed(1)+' MB',actions)})}
async function unlock(){await api('/providers');start=Date.now();sessionStorage.setItem('iptv-admin',token);$('login').hidden=true;$('dashboard').hidden=false;await loadCatalog();await refreshAdmin();await studio.ready;openDestination(studio.current().startup);void studio.connect()}
function stopPlayer(){return wall.clear();}
let editing=null,members=new Set();
function openGroup(group){editing=group||null;members=new Set(group?.channelIds||[]);$('group-name').value=group?.name||'';$('group-search').value='';$('editor-title').textContent=group?'Edit channel group':'Create channel group';$('delete-group').hidden=!group;$('group-error').textContent='';renderMembers();$('group-dialog').showModal()}
function renderMembers(){const target=$('group-members');target.replaceChildren();$('selection-count').textContent=members.size+' / 500 channels selected';const matches=channels.filter(c=>c.name.toLowerCase().includes($('group-search').value.toLowerCase()));matches.slice(0,100).forEach(c=>{const label=el('label'),check=el('input');check.type='checkbox';check.checked=members.has(c.id);check.onchange=()=>{if(check.checked&&members.size>=500){check.checked=false;$('group-error').textContent='Maximum 500 channels per group';return}if(check.checked)members.add(c.id);else members.delete(c.id);$('selection-count').textContent=members.size+' / 500 channels selected'};label.append(check,document.createTextNode(c.name));target.append(label)});if(matches.length>100)target.append(el('p','Showing the first 100 matches. Search to find more channels.'))}
$('group-form').onsubmit=async e=>{e.preventDefault();const b=e.target.querySelector('button[type=submit]');b.disabled=true;try{const g=await api('/groups'+(editing?'/'+editing.id:''),editing?'PUT':'POST',{name:$('group-name').value.trim(),channelIds:[...members]});customGroups=await api('/groups');selected='custom:'+g.id;resetScroll();$('group-dialog').close();renderGroups();renderGuide();await loadGuide()}catch(e){$('group-error').textContent=e.message}finally{b.disabled=false}};
$('delete-group').onclick=async()=>{try{await api('/groups/'+editing.id,'DELETE');customGroups=await api('/groups');selected='all';resetScroll();$('group-dialog').close();renderGroups();renderGuide();await loadGuide()}catch(e){$('group-error').textContent=e.message}};
$('new-group').onclick=()=>openGroup();$('edit-group').onclick=()=>openGroup(customGroups.find(g=>'custom:'+g.id===selected));$('cancel-group').onclick=()=>$('group-dialog').close();$('group-search').oninput=renderMembers;
async function showLogin(){const status=await api('/auth/status');authSetup=status.setup;$('login-description').textContent=authSetup?'Create your administrator username and password (at least 12 characters).':'Sign in with your administrator account.';$('login-submit').textContent=authSetup?'Create account':'Sign in';$('password').autocomplete=authSetup?'new-password':'current-password';$('password').minLength=authSetup?12:1;$('migration-label').hidden=!status.migration||!!token;if(!authSetup&&token){try{await unlock();return}catch{token='';sessionStorage.removeItem('iptv-admin')}}$('dashboard').hidden=true;$('login').hidden=false;}
$('login-form').onsubmit=async e=>{e.preventDefault();const b=$('login-submit');b.disabled=true;try{const result=await api('/auth/'+(authSetup?'setup':'login'),'POST',{username:$('username').value.trim(),password:$('password').value},$('migration-key').value.trim()||token);token=result.token;$('password').value='';$('migration-key').value='';await unlock()}catch(e){message(e.message,true);if(authSetup)$('migration-label').hidden=false}finally{b.disabled=false}};
$('provider-form').onsubmit=async e=>{e.preventDefault();const f=e.target,b=f.querySelector('button');b.disabled=true;const data=Object.fromEntries(new FormData(f));data.maxConnections=Number(data.maxConnections);data.allowHttp=!!data.allowHttp;data.httpFallback=!!data.httpFallback;try{const id=data.id;delete data.id;const r=await api('/providers'+(id?'/'+id:''),id?'PUT':'POST',data);resetProviderForm();message(`Provider saved. Imported ${r.channels} channels.`);await loadCatalog();await refreshAdmin()}catch(e){message(e.message,true)}finally{b.disabled=false}};
$('refresh-guide').onclick=async()=>{const b=$('refresh-guide');b.disabled=true;try{const results=await api('/guide/refresh','POST',{});message(Object.values(results).join(' · ')||'Add a provider first.');guideCache.clear();await loadGuide()}catch(e){message(e.message,true)}finally{b.disabled=false}};
$('search').oninput=()=>{resetScroll();renderGuide();loadGuide()};$('earlier').onclick=()=>{shiftGuide(-1800000)};$('later').onclick=()=>{shiftGuide(1800000)};$('now').onclick=()=>{start=Date.now();renderGuide();loadGuide()};
$('stop-player').onclick=()=>stopPlayer().catch(e=>message(e.message,true));$('fullscreen').onclick=()=>wall.fullscreen();
$('live-tab').onclick=()=>{const opening=$('live').hidden;$('live').hidden=false;$('settings').hidden=true;if(opening)$('now').click()};$('settings-tab').onclick=()=>{$('live').hidden=true;$('settings').hidden=false;refreshAdmin().catch(e=>message(e.message,true))};
$('logout').onclick=async()=>{try{await stopPlayer()}catch(e){message('Could not release streams. Retry locking after the server reconnects.',true);return;}await api('/auth/logout','POST',{});token='';sessionStorage.removeItem('iptv-admin');await showLogin()};
fetch('/healthz').then(r=>r.json()).then(()=>{$('health').textContent='● Server online'}).catch(()=>{$('health').textContent='Server unavailable'});
showLogin().catch(e=>message(e.message,true));
setInterval(()=>{if(token&&!document.hidden){refreshLineup().catch(()=>{});if(!$('settings').hidden)refreshAdmin().catch(()=>{})}},30000);
// On tab close the server lease expires; do not put administrator tokens in URLs or beacon bodies.

$('reorder-groups').onclick=()=>{arranging=!arranging;renderGroups()};
$('guide-viewport').addEventListener('scroll',()=>{renderGuide(false);scheduleGuide()},{passive:true});
$('guide-viewport').addEventListener('keydown',e=>{if(e.target!==$('guide-viewport'))return;const view=$('guide-viewport');if(e.key==='End')view.scrollTop=view.scrollHeight;else if(e.key==='Home')view.scrollTop=0;else if(e.key==='ArrowDown')view.scrollTop+=rowHeight;else if(e.key==='ArrowUp')view.scrollTop-=rowHeight;else return;e.preventDefault();renderGuide();scheduleGuide()});

function setGroups(show){$('live').classList.toggle('groups-open',show);$('show-groups').setAttribute('aria-expanded',String(show));requestAnimationFrame(()=>{renderGuide();loadGuide();});}
$('show-groups').onclick=()=>setGroups(!$('live').classList.contains('groups-open'));
$('rail-search').onclick=()=>{$('search').focus();};
$('rail-favorites').onclick=()=>{selected='favorites';setGroups(false);resetScroll();renderGroups();renderGuide();loadGuide();};
$('rail-wall').onclick=()=>{if(wall.tiles.length)wall.host.classList.add('expanded');$('wall-layout').focus();};
$('rail-settings').onclick=()=>$('settings-tab').click();
$('wall-layout').onchange=e=>{try{wall.layout(Number(e.target.value));}catch(err){e.target.value=String(wall.capacity);message(err.message,true);}};
$('clear-wall').onclick=()=>wall.clear().catch(e=>message(e.message,true));
$('remove-tile').onclick=()=>wall.remove().catch(e=>message(e.message,true));
$('enable-audio').onclick=()=>{const t=wall.selected;if(t){wall.select(t);t.video.play().catch(e=>message(e.message,true));}};
$('back-wall').onclick=()=>wall.back();
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!document.querySelector('dialog[open]')){if(wall.back()){e.preventDefault();}else if(!$('live').hidden){setGroups(true);}}});
for(const event of ['fullscreenchange','webkitfullscreenchange'])document.addEventListener(event,()=>{if(!document.fullscreenElement&&!document.webkitFullscreenElement&&wall.host.classList.contains('tile-fullscreen'))wall.back();});

// All rows share one timeline so channel comparisons and the header stay aligned.
function shiftGuide(delta){const now=Date.now();start=Math.max(now,Math.min(now+7*86400000,start+delta));renderGuide();scheduleGuide()}
const guideViewport=$('guide-viewport');let drag=null,suppressGuideClick=false;
guideViewport.addEventListener('wheel',e=>{const dx=e.deltaX||(e.shiftKey?e.deltaY:0);if(!dx)return;e.preventDefault();shiftGuide(dx/(e.target.closest('.programme-track')?.clientWidth||600)*duration)},{passive:false});
guideViewport.addEventListener('pointerdown',e=>{if(e.button!==0||!e.target.closest('.programme-track'))return;drag={x:e.clientX,y:e.clientY,start,id:e.pointerId,width:e.target.closest('.programme-track').clientWidth,moved:false}});
guideViewport.addEventListener('pointermove',e=>{if(!drag||drag.id!==e.pointerId)return;const dx=e.clientX-drag.x;if(!drag.moved&&Math.abs(e.clientY-drag.y)>Math.abs(dx)){drag=null;return}if(Math.abs(dx)<8&&!drag.moved)return;drag.moved=true;suppressGuideClick=true;guideViewport.setPointerCapture(e.pointerId);const desired=drag.start-dx/drag.width*duration;shiftGuide(desired-start)});
function endGuideDrag(e){if(drag?.id===e.pointerId){if(guideViewport.hasPointerCapture(e.pointerId))guideViewport.releasePointerCapture(e.pointerId);drag=null;setTimeout(()=>suppressGuideClick=false,0)}}
guideViewport.addEventListener('pointerup',endGuideDrag);guideViewport.addEventListener('pointercancel',endGuideDrag);
guideViewport.addEventListener('click',e=>{if(suppressGuideClick){e.preventDefault();e.stopImmediatePropagation()}},true);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&token&&!$('dashboard').hidden)$('now').click()});

// Phone drawers and a shorter visible timeline leave channel names on screen.
function closeGroups(){setGroups(false);$('show-groups').focus({preventScroll:true})}
$('close-groups').onclick=closeGroups;$('group-backdrop').onclick=closeGroups;
function adaptGuide(){$('player-controls').open=!phoneLayout.matches;duration=phoneLayout.matches?3600000:3*3600000;if(phoneLayout.matches)setGroups(false);else{renderGuide();scheduleGuide()}}
phoneLayout.addEventListener('change',adaptGuide);
if(phoneLayout.matches){$('player-controls').open=false;setGroups(false)}

function resetProviderForm(){const f=$('provider-form');f.reset();f.elements.id.value='';f.elements.password.required=true;f.elements.password.placeholder='';f.elements.url.readOnly=false;f.elements.username.readOnly=false;$('cancel-provider-edit').hidden=true;$('provider-form-title').textContent='Add account'}
function editProvider(p){const f=$('provider-form');for(const key of ['id','name','url','username','format','maxConnections'])f.elements[key].value=p[key];f.elements.password.value='';f.elements.password.required=false;f.elements.password.placeholder='Leave blank to keep current password';f.elements.allowHttp.checked=p.allowHttp;f.elements.httpFallback.checked=!!p.httpFallback;f.elements.url.readOnly=false;f.elements.username.readOnly=false;$('provider-form-title').textContent='Edit '+p.name;$('cancel-provider-edit').hidden=false;f.scrollIntoView({block:'start'});}
$('cancel-provider-edit').onclick=resetProviderForm;
$('refresh-channels').onclick=async()=>{const b=$('refresh-channels');b.disabled=true;try{const result=await api('/channels/refresh','POST',{});await loadCatalog();message(Object.values(result).join(' · ')||'No saved providers')}catch(e){message(e.message,true)}finally{b.disabled=false}};
async function loadRefreshSettings(){const s=await api('/refresh/settings');$('daily-channels').checked=s.channelsDaily;$('daily-epg').checked=s.epgDaily;$('refresh-status').textContent='Runs on the server every 24 hours while enabled. Failed refreshes retain existing data and retry later.'}
$('save-refresh').onclick=async()=>{try{await api('/refresh/settings','PUT',{channelsDaily:$('daily-channels').checked,epgDaily:$('daily-epg').checked});message('Daily refresh settings saved')}catch(e){message(e.message,true)}};
$('settings-tab').addEventListener('click',()=>loadRefreshSettings().catch(e=>message(e.message,true)));

async function refreshLineup(){const next=await api('/channels');if(JSON.stringify(next)!==JSON.stringify(channels)){channels=next;$('channel-count').textContent=channels.length;renderGroups();renderGuide()}await loadGuide()}

const dvr=recordingUI({api,channels:()=>channels,token:()=>token,message,onPlay:()=>wall.suspend(),onStop:()=>wall.resume()});
const metadataHost=el('div',null,'metadata-feature');document.querySelector('.watch-info').append(metadataHost);
const metadata=metadataPanel(api,metadataHost);
const guideEnrichment=guideMetadata(api);
const tmdbSettings=metadataSettings(api,$('settings'),()=>{metadata.reset();guideEnrichment.reset();renderGuide()},message);
$('settings-tab').addEventListener('click',()=>tmdbSettings.load().catch(e=>message(e.message,true)));
$('logout').addEventListener('click',()=>{metadata.reset();guideEnrichment.reset()});
$('recordings-tab').onclick=()=>dvr.library().catch(e=>message(e.message,true));
$('record-now').onclick=()=>{const c=channels.find(c=>c.id===active?.channelId);dvr.schedule(c,c?(guideCache.get(c.id)?.programmes||[]).find(p=>Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now()):null)};

const discovery=discoveryUI({api,channels:()=>channels,tune,programme:(...args)=>dvr.programme(...args),library:()=>dvr.library(),onDensity:name=>{
 rowHeight=guideDensities[name]||62;document.documentElement.style.setProperty('--guide-row-height',(rowHeight-4)+'px');document.documentElement.style.setProperty('--guide-font-size',name==='large'?'18px':name==='compact'?'13px':'15px');resetScroll();renderGuide();scheduleGuide();
}});
$('search-everything').onclick=()=>discovery.search();$('recent-channels').onclick=()=>discovery.recent();$('guide-appearance').onclick=()=>studio.open();
try{const name=localStorage.getItem('vynode-density');rowHeight=guideDensities[name]||62;document.documentElement.style.setProperty('--guide-row-height',(rowHeight-4)+'px');document.documentElement.style.setProperty('--guide-font-size',name==='large'?'18px':name==='compact'?'13px':'15px')}catch{}

const timeshift=timeshiftUI({api,host:$('settings'),message});
$('pause-live').onclick=()=>{const t=wall.selected;if(!t)return;t.userPaused=!t.video.paused;if(t.userPaused){t.video.pause();t.status='Paused'}else t.video.play().catch(e=>message(e.message,true));wall.render()};
$('rewind-live').onclick=()=>{if(wall.selected&&!seekWithinWindow(wall.selected.video,-30))message('No rewind window is available yet')};
$('jump-live').onclick=()=>{const t=wall.selected;if(t){t.userPaused=false;seekWithinWindow(t.video,null);t.video.play().catch(e=>message(e.message,true))}};
$('settings-tab').addEventListener('click',()=>timeshift.load().catch(e=>message(e.message,true)));

const studio=appearanceStudio({api,message,onChange:settings=>{
 const viewport=$('guide-viewport'),anchor=viewport.scrollTop/rowHeight,metrics=guideMetrics(settings);
 rowHeight=metrics.row;document.documentElement.style.setProperty('--guide-row-height',(rowHeight-4)+'px');document.documentElement.style.setProperty('--guide-font-size',metrics.font+'px');
 viewport.scrollTop=anchor*rowHeight;renderGuide();scheduleGuide();
}});
const appearanceSettings=document.createElement('button');appearanceSettings.className='secondary';appearanceSettings.textContent='Appearance Studio';appearanceSettings.onclick=()=>studio.open();$('settings').prepend(appearanceSettings);

const homeHost=el('section');homeHost.id='discover-home';homeHost.hidden=true;$('live').before(homeHost);
const home=homeUI({host:homeHost,api,channels:()=>channels,preferences:()=>preferences,tune:c=>{openDestination('guide');return tune(c)},guide:()=>openDestination('guide'),wall:()=>openDestination('wall'),library:()=>dvr.library(),programme:(...args)=>dvr.programme(...args),message});
function openDestination(destination){document.body.classList.toggle('wall-destination',destination==='wall');$('home-tab').classList.toggle('secondary',destination!=='home');$('live-tab').classList.toggle('secondary',destination==='home');home.close();homeHost.hidden=destination!=='home';$('live').hidden=destination==='home';$('settings').hidden=true;if(destination==='home')void home.show();else{renderGuide();scheduleGuide();if(destination==='wall'){$('player-controls').open=true;$('wall').scrollIntoView({block:'start'});$('wall-layout').focus()}}}
const homeButton=button('Discover',()=>openDestination('home'),'secondary');homeButton.id='home-tab';$('live-tab').before(homeButton);
$('live-tab').addEventListener('click',()=>openDestination('guide'));$('settings-tab').addEventListener('click',()=>{home.close();homeHost.hidden=true});$('logout').addEventListener('click',()=>{home.close();homeHost.hidden=true});

$('rail-wall').addEventListener('click',()=>openDestination('wall'));
