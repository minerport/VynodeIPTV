export const tmdbNotice='This product uses the TMDB API but is not endorsed or certified by TMDB.';
export function safeArtwork(value){return /^https:\/\/image\.tmdb\.org\/t\/p\/(w342|w780)\/[A-Za-z0-9_-]+\.(jpg|png)$/.test(value||'')?value:''}
const node=(tag,text)=>{const e=document.createElement(tag);if(text)e.textContent=text;return e};
export const programmeMetadataKey=p=>JSON.stringify([p.title.trim(),p.year||'',p.season||0,p.episode||0,p.episodeTitle||'']);
export const metadataQuery=p=>({title:p.title.slice(0,240),year:p.year||'',season:p.season||0,episode:p.episode||0,episodeTitle:(p.episodeTitle||'').slice(0,240)});
export const episodeLabel=d=>d?.episode>0?'S'+String(d.season||0).padStart(2,'0')+'E'+String(d.episode).padStart(2,'0')+(d.episodeTitle?' · '+d.episodeTitle:''):'';
// One lookup at a time, only for the latest visible window. Update cells in place
// so asynchronous artwork never moves keyboard focus or changes programme times.
export function guideMetadata(api,{wait=ms=>new Promise(resolve=>setTimeout(resolve,ms)),now=()=>Date.now()}={}){
 const cache=new Map();let cells=[],running=false,generation=0;
 const decorate=({p,element},data)=>{
  if(!data||!element.isConnected||element.dataset.enriched)return;
  element.dataset.enriched='true';const label=node('span');label.className='programme-copy';
  while(element.firstChild)label.append(element.firstChild);element.append(label);
  const art=safeArtwork(data.still)||safeArtwork(data.poster)||safeArtwork(data.backdrop);
  if(art){const img=node('img');img.src=art;img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';img.className='programme-art';img.onerror=()=>img.remove();element.prepend(img)}
  const facts=[episodeLabel(data),data.year,...(data.genres||[]).slice(0,2)].filter(Boolean).join(' · ');
  if(facts){const small=node('small',facts);small.className='programme-facts';label.append(small)}
  element.title+='\n'+facts+'\n'+(p.description||data.overview||'')+'\nMetadata: TMDB';
 };
 const pump=async()=>{if(running)return;running=true;const epoch=generation;
  try{await wait(350);while(epoch===generation){
   const target=cells.find(({p,element})=>element.isConnected&&(!cache.has(programmeMetadataKey(p))||cache.get(programmeMetadataKey(p)).expires<=now()));if(!target)break;
   const key=programmeMetadataKey(target.p);let result;
   try{result=await api('/metadata/lookup','POST',metadataQuery(target.p))}catch{result={state:'unavailable'}}
   if(epoch!==generation)break;
   const ttl=result.data?7*86400000:result.state==='pending'?2000:result.state==='disabled'?30000:result.state==='unavailable'?60000:6*3600000;
   cache.set(key,{data:result.data,expires:now()+ttl});while(cache.size>1000)cache.delete(cache.keys().next().value);
   cells.filter(c=>programmeMetadataKey(c.p)===key).forEach(c=>decorate(c,result.data));await wait(result.state==='pending'?2000:150);
  }}finally{running=false;if(epoch!==generation&&cells.length)void pump()}
 };
 return {show(next){cells=next.slice(0,64);cells.forEach(c=>decorate(c,cache.get(programmeMetadataKey(c.p))?.data));void pump()},reset(){generation++;cells=[];cache.clear()}};
}
export function metadataPanel(api,host){
 let revision=0,last='',timer;
 const show=p=>{
  const key=p?JSON.stringify([programmeMetadataKey(p),p.description]):'';if(key===last)return;last=key;const current=++revision;clearTimeout(timer);host.replaceChildren();if(!p)return;
  const summary=node('p',p.description||'');const status=node('small');host.append(summary,status);
  const refresh=async(attempt=0)=>{try{const r=await api('/metadata/lookup','POST',metadataQuery(p));if(current!==revision)return;
   if(r.data){const d=r.data;host.replaceChildren();const image=safeArtwork(d.still)||safeArtwork(d.poster)||safeArtwork(d.backdrop);if(image){const img=node('img');img.src=image;img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';img.onerror=()=>img.remove();host.append(img)}const info=node('div');info.append(node('strong',p.title),node('small',[d.episode>0?'Episode':d.kind==='tv'?'Series':'Movie',episodeLabel(d),d.year,...(d.genres||[])].filter(Boolean).join(' · ')),node('p',p.description||d.overview||''));if(d.cast?.length)info.append(node('small','Cast: '+d.cast.join(', ')));const link=node('a','Metadata: TMDB');link.href='https://www.themoviedb.org/'+(d.kind==='tv'?'tv':'movie')+'/'+Number(d.id)+(d.episode>0?'/season/'+Number(d.season||0)+'/episode/'+Number(d.episode):'');link.target='_blank';link.rel='noopener noreferrer';info.append(link);host.append(info)}
   else if(r.state==='disabled')status.textContent='Artwork is off · connect and enable TMDB in Server settings → Metadata.';
   else if(r.state==='unmatched')status.textContent='No reliable TMDB match · provider guide retained.';
   else if(r.state==='pending'&&attempt<30)timer=setTimeout(()=>refresh(attempt+1),2000);
   else if(r.state==='unavailable'&&attempt<30){status.textContent='TMDB temporarily unavailable · provider guide retained.';timer=setTimeout(()=>refresh(attempt+1),60000);}
  }catch{/* Optional metadata must not interfere with the provider guide. */}};
  timer=setTimeout(()=>refresh(),400);
 };
 return {show,reset(){last='';revision++;clearTimeout(timer);host.replaceChildren()}};
}
export function metadataSettings(api,host,onChange,message){
 const section=node('section');section.className='panel';section.append(node('h2','Metadata · TMDB'),node('p','Optional movie and series artwork, descriptions and cast. Programme titles are sent to TMDB; your provider credentials are never sent.'));
 const form=node('form'),label=node('label','API key or Read Access Token'),input=node('input');input.type='password';input.autocomplete='new-password';input.maxLength=4096;input.placeholder='Leave blank to keep saved token';label.append(input);
 const toggle=node('input');toggle.type='checkbox';const enabled=node('label','Enable guide enrichment');enabled.className='check';enabled.prepend(toggle);
 const background=node('input');background.type='checkbox';const warm=node('label','Cache upcoming 24-hour guide in the background');warm.className='check';warm.prepend(background);
 const save=node('button','Test & save'),disconnect=node('button','Disconnect');disconnect.type='button';disconnect.className='secondary';const status=node('p');status.setAttribute('role','status');
 form.append(label,enabled,warm,save,disconnect,status);section.append(form);
 const credits=node('div');credits.append(node('h3','Credits'));const logo=node('img');logo.src='/tmdb-logo.svg';logo.alt='TMDB';logo.width=70;const link=node('a','TMDB API settings');link.href='https://www.themoviedb.org/settings/api';link.target='_blank';link.rel='noopener noreferrer';credits.append(logo,node('p',tmdbNotice),link);section.append(credits);host.append(section);
 const load=async()=>{const s=await api('/metadata/settings');toggle.checked=s.enabled;background.checked=s.background!==false;status.textContent=(s.configured?'Token saved':'Not connected')+(s.status?' · '+s.status:'')};
 form.onsubmit=async e=>{e.preventDefault();save.disabled=disconnect.disabled=true;try{await api('/metadata/settings','PUT',{token:input.value.trim(),enabled:toggle.checked,background:background.checked});input.value='';onChange();await load();message('TMDB settings saved')}catch(e){status.textContent=e.message}finally{save.disabled=disconnect.disabled=false}};
 disconnect.onclick=async()=>{disconnect.disabled=true;try{await api('/metadata/settings','DELETE');input.value='';onChange();await load()}catch(e){status.textContent=e.message}finally{disconnect.disabled=false}};
 return {load};
}
