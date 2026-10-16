import {guideMetadata} from './metadata.js';

export function homeCandidates(channels,favorites,recent){const byId=new Map(channels.map(c=>[c.id,c]));return [...new Set([...favorites,...recent,...channels.map(c=>c.id)])].map(id=>byId.get(id)).filter(Boolean).slice(0,12)}

export function upcoming(programmes,now=Date.now()){return programmes.filter(p=>Date.parse(p.start)>now&&Date.parse(p.start)<now+86400000).sort((a,b)=>Date.parse(a.start)-Date.parse(b.start)).slice(0,12)}

export function homeUI({host,api,channels,preferences,tune,guide,wall,library,programme,message}){

 let epoch=0;const enrich=guideMetadata(api);const el=(tag,text)=>{const e=document.createElement(tag);if(text)e.textContent=text;return e};

 const action=(text,fn)=>{const b=el('button',text);b.onclick=()=>Promise.resolve().then(fn).catch(e=>message(e.message,true));return b};

 async function show(){const id=++epoch;enrich.reset();host.replaceChildren(el('p','YOUR COLLECTION'),el('h1','Find something worth watching.'));const nav=el('div');nav.className='toolbar';nav.append(action('Open guide',guide),action('Video wall',wall),action('Recordings',library));host.append(nav);let recent=[];try{recent=JSON.parse(localStorage.getItem('vynode-recent')||'[]')}catch{};if(!Array.isArray(recent))recent=[];

 const all=channels(),fav=preferences().favorites||[],selected=homeCandidates(all,fav,recent);const targets=[];

 const section=(name)=>{host.append(el('h2',name));const row=el('div');row.className='home-cards';host.append(row);return row};

 const cards=(row,list,entries=[])=>{if(!list.length)row.append(el('p','Nothing here yet. Browse the guide to build your collection.'));for(const c of list.slice(0,12)){const p=entries.find(p=>p.channelId===c.id&&Date.parse(p.start)<=Date.now()&&Date.parse(p.end)>Date.now());const b=action(c.name,()=>tune(c));b.className='home-card';b.title=c.name;b.append(el('strong',p?.title||'Watch live'));if(p)targets.push({p,element:b});row.append(b)}};

 const recentRow=section('Recently watched'),favRow=section('Favorites'),liveRow=section('On now');cards(recentRow,recent.map(id=>all.find(c=>c.id===id)).filter(Boolean));cards(favRow,all.filter(c=>fav.includes(c.id)));cards(liveRow,selected);

 const status=el('p','Loading programme highlights…');host.append(status);

 try{const data=selected.length?await api('/guide?channelIds='+encodeURIComponent(selected.map(c=>c.id).join(','))+'&from='+encodeURIComponent(new Date().toISOString())+'&to='+encodeURIComponent(new Date(Date.now()+86400000).toISOString())):{programmes:[]};if(id!==epoch)return;for(const row of [recentRow,favRow,liveRow])row.replaceChildren();cards(recentRow,recent.map(id=>all.find(c=>c.id===id)).filter(Boolean),data.programmes);cards(favRow,all.filter(c=>fav.includes(c.id)),data.programmes);cards(liveRow,selected,data.programmes);const next=section('Upcoming on your favorites');const entries=upcoming(data.programmes.filter(p=>fav.includes(p.channelId)));if(!entries.length)next.append(el('p','No upcoming guide data for the sampled favorites. Open the guide for your complete lineup.'));for(const p of entries){const c=all.find(c=>c.id===p.channelId);if(!c)continue;const b=action(p.title,()=>programme(c,p,()=>tune(c)));b.className='home-card';b.title=p.title;b.append(el('small',c.name+' · '+new Date(p.start).toLocaleString()));next.append(b);targets.push({p,element:b})}status.textContent='Highlights from up to 12 channels · Open guide for the full lineup';enrich.show(targets);

 }catch{if(id===epoch)status.textContent='Programme highlights unavailable. Channels can still play.'}

 try{const entries=await api('/recordings');if(id!==epoch)return;const row=section('Recordings');if(!entries.length)row.append(el('p','Your recordings will appear here.'));for(const r of entries.slice(-12).reverse()){const b=action(r.title,library);b.className='home-card';b.append(el('small',r.status+' · Open recordings'));row.append(b)}}catch{if(id===epoch)host.append(el('p','Recordings unavailable right now.'))}

 }

 return {show,close(){epoch++;enrich.reset()}};

}
