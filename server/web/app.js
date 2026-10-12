import {VideoWall} from './wall.js';
import {providerGroups,inGroup,clipProgramme,orderedGroups,virtualRange} from './guide.js';
const $=id=>document.getElementById(id),duration=3*3600000;
let token=sessionStorage.getItem('iptv-admin')||'',channels=[],customGroups=[],selected='all',start=Math.floor(Date.now()/1800000)*1800000,guide=[],guideRevision=0,timer;
let preferences={favorites:[],groupOrder:[],hiddenGroups:[]},arranging=false,preferenceBusy=false,scrollTimer;const guideCache=new Map();let lastWindow="";
let active=null;
const time=value=>new Date(value).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
async function api(path,method='GET',body,auth=token){const r=await fetch('/api/v1'+path,{method,headers:{Authorization:'Bearer '+auth,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const data=await r.json();if(!r.ok)throw new Error(data.error||'Request failed');return data}
function message(text,error=false){$('message').textContent=text;$('message').className=error?'error':'';$('message').style.display='block';clearTimeout(timer);timer=setTimeout(()=>$('message').style.display='none',8000)}
function el(tag,text,className){const e=document.createElement(tag);if(text)e.textContent=text;if(className)e.className=className;return e}
function button(text,action,className='secondary'){const b=el('button',text,className);b.type='button';b.onclick=async()=>{b.disabled=true;try{await action()}catch(e){message(e.message,true)}finally{b.disabled=false}};return b}
function row(container,title,subtitle,action){const r=el('div'),info=el('span');info.append(el('strong',title));if(subtitle)info.append(el('small',subtitle));r.append(info);if(action)r.append(action);container.append(r)}
function allGroups(){return [{id:'favorites',name:'★ Favorites',channelIds:preferences.favorites},...orderedGroups([{id:'all',name:'All channels',channelIds:channels.map(c=>c.id)},...customGroups.map(g=>({...g,id:'custom:'+g.id})),...providerGroups(channels)],preferences.groupOrder)]}
function selectedGroup(){return allGroups().find(g=>g.id===selected)}
function filtered(){return inGroup(channels,selectedGroup(),$('search').value)}
function range(){return virtualRange(filtered().length,$('guide-viewport').scrollTop,$('guide-viewport').clientHeight||500)}
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
function renderGuide(force=true){
 const list=filtered(),viewport=$('guide-viewport');if(viewport.scrollTop>Math.max(0,list.length*62-viewport.clientHeight))viewport.scrollTop=Math.max(0,list.length*62-viewport.clientHeight);
 const r=range(),signature=[selected,$('search').value,start,r.start,r.end,list.length].join('|');if(!force&&signature===lastWindow)return;lastWindow=signature;const focus=document.activeElement?.dataset.focusKey;
 $('time-labels').replaceChildren(...Array.from({length:6},(_,i)=>el('span',time(start+i*1800000))));$('guide-date').textContent=new Date(start).toLocaleDateString([],{weekday:'short',month:'short',day:'numeric'});
 const target=$('guide-rows');target.replaceChildren();const spacer=height=>{const e=el('div');e.style.height=height+'px';e.setAttribute('aria-hidden','true');return e};target.append(spacer(r.top));
 for(const c of list.slice(r.start,r.end)){
  const row=el('div',null,'guide-row'),label=el('div',null,'channel-cell');
  const star=button(preferences.favorites.includes(c.id)?'★':'☆',()=>favorite(c),'favorite');star.setAttribute('aria-label',(preferences.favorites.includes(c.id)?'Remove '+c.name+' from':'Add '+c.name+' to')+' favorites');star.setAttribute('aria-pressed',String(preferences.favorites.includes(c.id)));star.dataset.focusKey='favorite:'+c.id;star.disabled=preferenceBusy;
  const tuneButton=button(c.name,()=>tune(c),'channel-name');tuneButton.dataset.focusKey='channel:'+c.id;label.append(star,tuneButton);row.append(label);
  row.addEventListener('keydown',event=>{if(!['ArrowDown','ArrowUp'].includes(event.key))return;event.preventDefault();const index=list.findIndex(item=>item.id===c.id)+(event.key==='ArrowDown'?1:-1);if(index<0||index>=list.length)return;const y=index*62;if(y<viewport.scrollTop)viewport.scrollTop=y;else if(y+62>viewport.scrollTop+viewport.clientHeight)viewport.scrollTop=y+62-viewport.clientHeight;renderGuide();const key=(event.target.classList.contains('favorite')?'favorite:':'channel:')+list[index].id;[...target.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey===key)?.focus({preventScroll:true});scheduleGuide()});
  const track=el('div',null,'programme-track');let count=0;const cached=guideCache.get(c.id);for(const p of cached?.start===start?cached.programmes:[]){const clip=clipProgramme(p,start,duration);if(!clip)continue;count++;const b=button(p.title,()=>tune(c),'programme'+(Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now()?' live':''));b.dataset.focusKey='programme:'+c.id+':'+p.start;b.title=p.title+' · '+time(p.start)+'–'+time(p.end);b.style.left=clip.left+'%';b.style.width=clip.width+'%';b.append(el('small',time(p.start)+'–'+time(p.end)));track.append(b)}
  if(!count)track.append(el('div',cached?.start===start?'No programme information':'Loading guide…','no-guide'));if(Date.now()>=start&&Date.now()<start+duration){const line=el('div',null,'now-line');line.style.left=(Date.now()-start)/duration*100+'%';track.append(line)}row.append(track);target.append(row);
 }target.append(spacer(r.bottom));if(!list.length)target.append(el('p',selected==='favorites'?'No favorites yet. Use the star beside any channel.':'No channels match this view.'));$('channel-total').textContent=list.length+' channels · Scroll to browse';
 if(focus)[...target.querySelectorAll('[data-focus-key]')].find(e=>e.dataset.focusKey===focus)?.focus({preventScroll:true});updateNowPlaying();
}
function updateNowPlaying(){if(!active)return;const p=(guideCache.get(active.channelId)?.programmes||[]).find(p=>Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now());$('playing-programme').textContent=p?p.title+' · '+time(p.start)+'–'+time(p.end):'Live channel · programme information unavailable'}
async function loadGuide(){
 const revision=++guideRevision,windowStart=start,ids=visible().filter(c=>{const cached=guideCache.get(c.id);return !cached||cached.start!==start||Date.now()-cached.fetched>60000}).map(c=>c.id);if(!ids.length)return
 $('epg-message').textContent='Loading guide…';try{const data=await api('/guide?'+new URLSearchParams({channelIds:ids.join(','),from:new Date(windowStart).toISOString(),to:new Date(windowStart+duration).toISOString()}));if(windowStart!==start||revision!==guideRevision)return;
 const byChannel=new Map(ids.map(id=>[id,[]]));for(const p of data.programmes)byChannel.get(p.channelId)?.push(p);for(const id of ids){guideCache.set(id,{start:windowStart,fetched:Date.now(),programmes:byChannel.get(id)})}while(guideCache.size>300)guideCache.delete(guideCache.keys().next().value);
 $('epg-message').textContent='Local times · select a programme to watch live';$('guide-status').textContent=data.status;renderGuide()}catch(e){if(revision===guideRevision)$('epg-message').textContent=e.message}
}
function scheduleGuide(){clearTimeout(scrollTimer);scrollTimer=setTimeout(()=>loadGuide(),140)}
async function loadCatalog(){[channels,customGroups,preferences]=await Promise.all([api('/channels'),api('/groups'),api('/guide/preferences')]);$('channel-count').textContent=channels.length;renderGroups();renderGuide();await loadGuide()}
const wall=new VideoWall({host:$('wall'),api,onError:e=>message(e.message,true),onChange:w=>{
 active=w.selected?.session||null;
 $('player-placeholder').hidden=w.tiles.length>0;
 $('stop-player').disabled=!w.tiles.length;
 $('playing-title').textContent=w.selected?.channel.name||'Choose a channel';
 $('playback-status').textContent=w.selected?.status||'Ready to play';
 $('wall-count').textContent=w.tiles.length+' / '+w.capacity+' streams';
 $('wall-layout').value=String(w.capacity);
 $('remove-tile').disabled=!w.selected;
 if(active)updateNowPlaying();else $('playing-programme').textContent='Browse your groups and live programme guide.';
 const h=active?.health;
 $('stream-health').textContent=h?`Output age ${(h.segmentAgeMs/1000).toFixed(1)}s · ${h.restarts} worker restarts · ${(h.scratchBytes/1048576).toFixed(1)} MB scratch`:'';
}});wall.auth=()=>token;
function tune(channel){return wall.add(channel).catch(e=>message(e.message,true));}
async function refreshAdmin(){if(!token)return;const [ss,providers,pairs,devices]=await Promise.all(['/playback/sessions','/providers','/pairing/requests','/devices'].map(p=>api(p)));$('session-count').textContent=ss.length;$('device-count').textContent=devices.length;$('providers').replaceChildren();providers.forEach(p=>{const label=el('label',null,'check'),check=el('input');check.type='checkbox';check.checked=p.allowHttp;check.onchange=async()=>{check.disabled=true;try{await api('/providers/'+p.id+'/transport','PUT',{allowHttp:check.checked});message('Transport setting saved. Restart playback to apply.')}catch(e){check.checked=!check.checked;message(e.message,true)}finally{check.disabled=false}};label.append(check,document.createTextNode('Allow HTTP streams / redirects (unencrypted)'));row($('providers'),p.name,`${p.format.toUpperCase()} · ${p.maxConnections} connection(s)`,label)});$('pairings').replaceChildren();if(!pairs.length)row($('pairings'),'Waiting for a pairing request');pairs.forEach(p=>row($('pairings'),p.name+' · '+p.code,'Verify this code matches your TV',button('Approve',async()=>{await api('/pairing/approve','POST',{id:p.id,code:p.code});await refreshAdmin()})));$('devices').replaceChildren();devices.forEach(d=>row($('devices'),d.name,'Companion access',button('Revoke',async()=>{await api('/devices/'+d.id,'DELETE');await refreshAdmin()})));$('sessions').replaceChildren();ss.forEach(s=>{const actions=el('span');const details=el('details'),summary=el('summary','Recovery history');details.append(summary);for(const event of s.health?.events||[])details.append(el('small',time(event.at)+' · '+event.state+' · '+event.reason));actions.append(details);if(s.channelId==='demo:1')actions.append(button('Interrupt test',()=>api('/playback/sessions/'+s.id+'/interrupt','POST',{})));actions.append(button('Stop',async()=>{const tile=wall.tiles.find(t=>t.session.id===s.id);if(tile){await wall.enqueue(()=>wall.removeTile(tile));if(!wall.tiles.length)wall.capacity=1;wall.render();}else await api('/playback/sessions/'+s.id,'DELETE');await refreshAdmin()}));row($('sessions'),channels.find(c=>c.id===s.channelId)?.name||s.channelId,s.state+' · '+s.reason+' · generation '+s.generation+' · output age '+((s.health?.segmentAgeMs||0)/1000).toFixed(1)+'s · restarts '+(s.health?.restarts||0)+' · scratch '+((s.health?.scratchBytes||0)/1048576).toFixed(1)+' MB',actions)})}
async function unlock(){await api('/providers');sessionStorage.setItem('iptv-admin',token);$('login').hidden=true;$('dashboard').hidden=false;await loadCatalog();await refreshAdmin()}
function stopPlayer(){return wall.clear();}
let editing=null,members=new Set();
function openGroup(group){editing=group||null;members=new Set(group?.channelIds||[]);$('group-name').value=group?.name||'';$('group-search').value='';$('editor-title').textContent=group?'Edit channel group':'Create channel group';$('delete-group').hidden=!group;$('group-error').textContent='';renderMembers();$('group-dialog').showModal()}
function renderMembers(){const target=$('group-members');target.replaceChildren();$('selection-count').textContent=members.size+' / 500 channels selected';const matches=channels.filter(c=>c.name.toLowerCase().includes($('group-search').value.toLowerCase()));matches.slice(0,100).forEach(c=>{const label=el('label'),check=el('input');check.type='checkbox';check.checked=members.has(c.id);check.onchange=()=>{if(check.checked&&members.size>=500){check.checked=false;$('group-error').textContent='Maximum 500 channels per group';return}if(check.checked)members.add(c.id);else members.delete(c.id);$('selection-count').textContent=members.size+' / 500 channels selected'};label.append(check,document.createTextNode(c.name));target.append(label)});if(matches.length>100)target.append(el('p','Showing the first 100 matches. Search to find more channels.'))}
$('group-form').onsubmit=async e=>{e.preventDefault();const b=e.target.querySelector('button[type=submit]');b.disabled=true;try{const g=await api('/groups'+(editing?'/'+editing.id:''),editing?'PUT':'POST',{name:$('group-name').value.trim(),channelIds:[...members]});customGroups=await api('/groups');selected='custom:'+g.id;resetScroll();$('group-dialog').close();renderGroups();renderGuide();await loadGuide()}catch(e){$('group-error').textContent=e.message}finally{b.disabled=false}};
$('delete-group').onclick=async()=>{try{await api('/groups/'+editing.id,'DELETE');customGroups=await api('/groups');selected='all';resetScroll();$('group-dialog').close();renderGroups();renderGuide();await loadGuide()}catch(e){$('group-error').textContent=e.message}};
$('new-group').onclick=()=>openGroup();$('edit-group').onclick=()=>openGroup(customGroups.find(g=>'custom:'+g.id===selected));$('cancel-group').onclick=()=>$('group-dialog').close();$('group-search').oninput=renderMembers;
$('login-form').onsubmit=async e=>{e.preventDefault();token=$('token').value.trim();try{await unlock();$('token').value=''}catch(e){message(e.message,true)}};
$('provider-form').onsubmit=async e=>{e.preventDefault();const f=e.target,b=f.querySelector('button');b.disabled=true;const data=Object.fromEntries(new FormData(f));data.maxConnections=Number(data.maxConnections);data.allowHttp=!!data.allowHttp;try{const r=await api('/providers','POST',data);f.reset();message(`Provider saved. Imported ${r.channels} channels.`);await loadCatalog();await refreshAdmin()}catch(e){message(e.message,true)}finally{b.disabled=false}};
$('refresh-guide').onclick=async()=>{const b=$('refresh-guide');b.disabled=true;try{const results=await api('/guide/refresh','POST',{});message(Object.values(results).join(' · ')||'Add a provider first.');await loadGuide()}catch(e){message(e.message,true)}finally{b.disabled=false}};
$('search').oninput=()=>{resetScroll();renderGuide();loadGuide()};$('earlier').onclick=()=>{start-=duration;renderGuide();loadGuide()};$('later').onclick=()=>{start+=duration;renderGuide();loadGuide()};$('now').onclick=()=>{start=Math.floor(Date.now()/1800000)*1800000;renderGuide();loadGuide()};
$('stop-player').onclick=()=>stopPlayer().catch(e=>message(e.message,true));$('fullscreen').onclick=()=>wall.fullscreen();
$('live-tab').onclick=()=>{$('live').hidden=false;$('settings').hidden=true};$('settings-tab').onclick=()=>{$('live').hidden=true;$('settings').hidden=false;refreshAdmin().catch(e=>message(e.message,true))};
$('logout').onclick=async()=>{try{await stopPlayer()}catch(e){message('Could not release streams. Retry locking after the server reconnects.',true);return;}token='';sessionStorage.removeItem('iptv-admin');$('dashboard').hidden=true;$('login').hidden=false};
fetch('/healthz').then(r=>r.json()).then(()=>{$('health').textContent='● Server online'}).catch(()=>{$('health').textContent='Server unavailable'});
if(token)unlock().catch(e=>{message(e.message,true);token='';sessionStorage.removeItem('iptv-admin');$('dashboard').hidden=true;$('login').hidden=false});
setInterval(()=>{if(token&&!document.hidden){loadGuide();if(!$('settings').hidden)refreshAdmin().catch(()=>{})}},30000);
// On tab close the server lease expires; do not put administrator tokens in URLs or beacon bodies.

$('reorder-groups').onclick=()=>{arranging=!arranging;renderGroups()};
$('guide-viewport').addEventListener('scroll',()=>{renderGuide(false);scheduleGuide()},{passive:true});
$('guide-viewport').addEventListener('keydown',e=>{if(e.target!==$('guide-viewport'))return;const view=$('guide-viewport');if(e.key==='End')view.scrollTop=view.scrollHeight;else if(e.key==='Home')view.scrollTop=0;else if(e.key==='ArrowDown')view.scrollTop+=62;else if(e.key==='ArrowUp')view.scrollTop-=62;else return;e.preventDefault();renderGuide();scheduleGuide()});

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
document.addEventListener('fullscreenchange',()=>{if(!document.fullscreenElement&&wall.host.classList.contains('tile-fullscreen'))wall.back();});
