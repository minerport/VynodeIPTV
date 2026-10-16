export function seekWithinWindow(video,delta){
 if(!video.seekable.length)return false;
 const first=video.seekable.start(0),last=video.seekable.end(video.seekable.length-1);
 video.currentTime=Math.max(first,Math.min(last-.1,delta===null?last-3:video.currentTime+delta));return true;
}
export function timeshiftUI({api,host,message}){
 const section=document.createElement('section');section.className='panel';
 section.innerHTML='<h2>Pause and rewind live TV</h2><p>Applies to new streams. Buffer starts when a channel opens and is discarded when it closes or reconnects. Rewinding cannot recover broadcasts that were never buffered.</p><label>Rolling window<select><option value="0">Off</option><option value="5">5 minutes</option><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="60">60 minutes</option></select></label><label>Maximum scratch MB per stream<input type="number" min="256" max="4096" value="512"></label><p>Allow enough scratch space for all simultaneous streams. A stream stops safely if its limit is exceeded. A long pause can outlast the rolling window.</p><button type="button">Save timeshift settings</button>';
 host.append(section);const select=section.querySelector('select'),input=section.querySelector('input'),button=section.querySelector('button');
 button.onclick=async()=>{button.disabled=true;try{await api('/timeshift','PUT',{minutes:Number(select.value),maxMB:Number(input.value)});message('Saved. Reopen a channel to apply.')}catch(e){message(e.message,true)}finally{button.disabled=false}};
 return {load:async()=>{const s=await api('/timeshift');select.value=String(s.minutes);input.value=s.maxMB}};
}
