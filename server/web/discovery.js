export const guideDensities={compact:50,comfortable:62,large:78};
export function addRecent(ids,id){return [id,...ids.filter(value=>value!==id)].slice(0,20)}

export function discoveryUI({api,channels,tune,programme,library,onDensity}){
 const dialog=document.createElement('dialog');dialog.className='recording-dialog';document.body.append(dialog);
 const make=(tag,text)=>{const e=document.createElement(tag);if(text)e.textContent=text;return e};
 const action=(text,fn)=>{const b=make('button',text);b.type='button';b.onclick=fn;return b};
 let epoch=0,timer;
 dialog.addEventListener('close',()=>{epoch++;clearTimeout(timer)});
 const shell=title=>{epoch++;dialog.replaceChildren(make('h2',title),action('Close',()=>dialog.close()));if(!dialog.open)dialog.showModal()};
 const loadRecent=()=>{try{const a=JSON.parse(localStorage.getItem('vynode-recent')||'[]');return Array.isArray(a)?a.filter(v=>typeof v==='string').slice(0,20):[]}catch{return []}};
 function watched(id){try{localStorage.setItem('vynode-recent',JSON.stringify(addRecent(loadRecent(),id)))}catch{}}
 function recent(){shell('Recent channels');for(const id of loadRecent()){const c=channels().find(c=>c.id===id);if(c)dialog.append(action(c.name,()=>{dialog.close();tune(c)}))}}
 function search(){
  shell('Search everything');const input=make('input'),results=make('div'),status=make('p','Search channels, programmes and recordings');input.type='search';input.maxLength=100;input.placeholder='Channel or programme title';input.setAttribute('aria-label','Search everything');status.setAttribute('role','status');dialog.append(input,status,results);input.focus();
  input.oninput=()=>{clearTimeout(timer);const version=++epoch,q=input.value.trim();results.replaceChildren();if(!q){status.textContent='Enter a title';return}status.textContent='Searching…';timer=setTimeout(async()=>{
   try{const data=await api('/search?q='+encodeURIComponent(q));if(version!==epoch||!dialog.open)return;
    status.textContent=data.results.length+' results'+(data.truncated?' · narrow your search for more':'');
    for(const hit of data.results){const title=hit.programme?.title||hit.recording?.title||hit.channel?.name;results.append(action(hit.kind+' · '+title+(hit.programme?' · '+hit.channel.name+' · '+new Date(hit.programme.start).toLocaleString():''),()=>{dialog.close();if(hit.kind==='channel')tune(hit.channel);else if(hit.kind==='programme')programme(hit.channel,hit.programme,()=>tune(hit.channel));else library()}))}
   }catch(e){if(version===epoch)status.textContent=e.message}
  },300)};
 }
 function appearance(){shell('Guide appearance');for(const name of Object.keys(guideDensities))dialog.append(action(name,()=>{try{localStorage.setItem('vynode-density',name)}catch{};onDensity(name)}))}
 return {search,recent,appearance,watched};
}
