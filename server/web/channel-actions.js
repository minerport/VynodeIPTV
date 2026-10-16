// No provider credentials or session token are attached to artwork requests.
export function safeChannelLogo(value){
 try{const raw=String(value||'').trim(),u=new URL(raw);return raw.length<=2048&&u.protocol==='https:'&&!u.username&&!u.password&&!u.search&&!u.hash?u.href:''}catch{return ''}
}
export function bindChannelActions(element,open){
 let timer=null,held=false,x=0,y=0;
 const clear=()=>{clearTimeout(timer);timer=null};
 const show=(suppressClick=false)=>{clear();held=suppressClick;open()};
 element.addEventListener('contextmenu',event=>{event.preventDefault();show()});
 element.addEventListener('pointerdown',event=>{
  if(event.button!==0)return;clear();held=false;x=event.clientX;y=event.clientY;
  timer=setTimeout(()=>{if(element.isConnected)show(true)},550);
 });
 element.addEventListener('pointermove',event=>{if(Math.abs(event.clientX-x)>10||Math.abs(event.clientY-y)>10)clear()});
 for(const name of ['pointerup','pointercancel','pointerleave','blur'])element.addEventListener(name,clear);
 element.addEventListener('click',event=>{if(held){event.preventDefault();event.stopImmediatePropagation();held=false}},true);
 element.addEventListener('keydown',event=>{
  if(event.key==='ContextMenu'||(event.shiftKey&&event.key==='F10')){event.preventDefault();event.stopPropagation();show()}
 });
}
export function channelActionMenu({channel,programme,favorite,onFavorite,onWatch,onDetails,onRecord,onClose}){
 const dialog=document.createElement('dialog');dialog.className='recording-dialog channel-actions';
 const title=document.createElement('h2');title.textContent=channel.name;dialog.append(title);
 if(programme){const subtitle=document.createElement('p');subtitle.textContent=programme.title;dialog.append(subtitle)}
 const action=(label,fn)=>{const b=document.createElement('button');b.type='button';b.textContent=label;b.onclick=()=>{dialog.close();fn?.()};dialog.append(b)};
 action('Watch channel live',onWatch);action(favorite?'Remove from favorites':'Add to favorites',onFavorite);
 action('Programme details',onDetails);
 if(!programme||Date.parse(programme.end)>Date.now())action(programme&&Date.parse(programme.start)>Date.now()?'Schedule recording':'Record',onRecord);
 action('Close');dialog.addEventListener('close',()=>{dialog.remove();onClose?.()},{once:true});document.body.append(dialog);dialog.showModal();
}
