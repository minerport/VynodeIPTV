export const tmdbNotice='This product uses the TMDB API but is not endorsed or certified by TMDB.';
export function safeArtwork(value){return /^https:\/\/image\.tmdb\.org\/t\/p\/(w342|w780)\/[A-Za-z0-9_-]+\.(jpg|png)$/.test(value||'')?value:''}
const node=(tag,text)=>{const e=document.createElement(tag);if(text)e.textContent=text;return e};
export function metadataPanel(api,host){
 let revision=0,last='',timer;
 const show=p=>{
  const key=p?JSON.stringify([p.title,p.year,p.description]):'';if(key===last)return;last=key;const current=++revision;clearTimeout(timer);host.replaceChildren();if(!p)return;
  const summary=node('p',p.description||'');host.append(summary);
  const refresh=async(attempt=0)=>{try{const r=await api('/metadata/lookup','POST',{title:p.title.slice(0,240),year:p.year||''});if(current!==revision)return;
   if(r.data){const d=r.data;host.replaceChildren();const image=safeArtwork(d.poster);if(image){const img=node('img');img.src=image;img.alt='';img.loading='lazy';img.referrerPolicy='no-referrer';host.append(img)}const info=node('div');info.append(node('strong',p.title),node('small',[d.kind==='tv'?'Series':'Movie',d.year,...(d.genres||[])].filter(Boolean).join(' · ')),node('p',d.overview||p.description||''));if(d.cast?.length)info.append(node('small','Cast: '+d.cast.join(', ')));const link=node('a','Metadata: TMDB');link.href='https://www.themoviedb.org/'+(d.kind==='tv'?'tv':'movie')+'/'+Number(d.id);link.target='_blank';link.rel='noopener noreferrer';info.append(link);host.append(info)}
   else if(r.state==='pending'&&attempt<30)timer=setTimeout(()=>refresh(attempt+1),2000);
   else if(r.state==='unavailable'&&attempt<30)timer=setTimeout(()=>refresh(attempt+1),60000);
  }catch{/* Optional metadata must not interfere with the provider guide. */}};
  timer=setTimeout(()=>refresh(),400);
 };
 return {show,reset(){last='';revision++;clearTimeout(timer);host.replaceChildren()}};
}
export function metadataSettings(api,host,onChange,message){
 const section=node('section');section.className='panel';section.append(node('h2','Metadata · TMDB'),node('p','Optional movie and series artwork, descriptions and cast. Programme titles are sent to TMDB; your provider credentials are never sent.'));
 const form=node('form'),label=node('label','API Read Access Token'),input=node('input');input.type='password';input.autocomplete='new-password';input.maxLength=4096;input.placeholder='Leave blank to keep saved token';label.append(input);
 const toggle=node('input');toggle.type='checkbox';const enabled=node('label','Enable guide enrichment');enabled.className='check';enabled.prepend(toggle);
 const save=node('button','Test & save'),disconnect=node('button','Disconnect');disconnect.type='button';disconnect.className='secondary';const status=node('p');status.setAttribute('role','status');
 form.append(label,enabled,save,disconnect,status);section.append(form);
 const credits=node('div');credits.append(node('h3','Credits'));const logo=node('img');logo.src='/tmdb-logo.svg';logo.alt='TMDB';logo.width=70;const link=node('a','TMDB API settings');link.href='https://www.themoviedb.org/settings/api';link.target='_blank';link.rel='noopener noreferrer';credits.append(logo,node('p',tmdbNotice),link);section.append(credits);host.append(section);
 const load=async()=>{const s=await api('/metadata/settings');toggle.checked=s.enabled;status.textContent=(s.configured?'Token saved':'Not connected')+(s.status?' · '+s.status:'')};
 form.onsubmit=async e=>{e.preventDefault();save.disabled=disconnect.disabled=true;try{await api('/metadata/settings','PUT',{token:input.value.trim(),enabled:toggle.checked});input.value='';onChange();await load();message('TMDB settings saved')}catch(e){status.textContent=e.message}finally{save.disabled=disconnect.disabled=false}};
 disconnect.onclick=async()=>{disconnect.disabled=true;try{await api('/metadata/settings','DELETE');input.value='';onChange();await load()}catch(e){status.textContent=e.message}finally{disconnect.disabled=false}};
 return {load};
}
