import {enterNativeFullscreen} from './wall.js';

export function recordingUI({api,channels,token,message,onPlay=()=>{},onStop=()=>{}}) {
 const dialog=document.createElement('dialog');dialog.className='recording-dialog';document.body.append(dialog);
 let hls=null,video=null,playing=null,poll=null,epoch=0,preparation=null;
 const node=(tag,text)=>{const n=document.createElement(tag);if(text)n.textContent=text;return n};
 const action=(text,fn)=>{const b=node('button',text);b.type='button';b.onclick=async()=>{b.disabled=true;try{await fn()}catch(e){message(e.message,true)}finally{b.disabled=false}};return b};
 const stop=()=>{epoch++;if(video&&playing&&hls&&(video.readyState>=1||video.currentTime>0))api('/recordings/'+playing.id+'/resume','PUT',{seconds:video.currentTime||0}).catch(()=>{});const old=preparation;preparation=null;if(old)old.ready.then(()=>{if(preparation?.id!==old.id)return api('/recordings/'+old.id+'/playback','DELETE')}).catch(()=>{});hls?.destroy();hls=null;video?.pause();if(playing)onStop();video=null;playing=null;clearInterval(poll)};
 dialog.addEventListener('close',stop);
 function shell(title){stop();dialog.replaceChildren(node('h2',title),action('Close',()=>dialog.close()));if(!dialog.open)dialog.showModal()}
 function input(form,label,type,value){const l=node('label',label),i=node('input');i.type=type;i.value=value;i.required=true;l.append(i);form.append(l);return i}
 const local=d=>new Date(d-new Date(d).getTimezoneOffset()*60000).toISOString().slice(0,16);
 function schedule(channel,programme){
  shell('Schedule recording');const form=node('form');dialog.append(form);
  const select=node('select');for(const c of channels()){const o=node('option',c.name);o.value=c.id;o.selected=c.id===channel?.id;select.append(o)}const label=node('label','Channel');label.append(select);form.append(label);
  const title=input(form,'Recording title','text',programme?.title||channel?.name||'Recording');title.maxLength=240;
  const start=input(form,'Start (local time)','datetime-local',local(Math.max(Date.now(),Date.parse(programme?.start)||0)));
  const end=input(form,'End (local time)','datetime-local',local(Date.parse(programme?.end)||Date.now()+3600000));
  const before=input(form,'Minutes before','number','1');before.min=0;before.max=60;
  const after=input(form,'Minutes after','number','3');after.min=0;after.max=120;
  const submit=node('button','Save recording');form.append(submit);form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;try{await api('/recordings','POST',{channelId:select.value,title:title.value,start:new Date(start.value).toISOString(),end:new Date(end.value).toISOString(),beforeMinutes:Number(before.value),afterMinutes:Number(after.value)});message('Recording scheduled');await library()}catch(e){message(e.message,true)}finally{submit.disabled=false}};
 }
 async function play(job){
  if(!window.Hls?.isSupported())throw Error('Recorded playback requires a browser supporting authenticated HLS, such as current Chrome, Edge or Firefox.');
  shell(job.title);onPlay();video=node('video');video.controls=true;video.playsInline=true;video.style.width='100%';playing=job;dialog.append(video,action('Fullscreen',()=>enterNativeFullscreen(video,video)));
  const version=epoch,player=video,status=node('p','Preparing browser playback…'),resume=Math.max(0,job.resume||0);dialog.append(status);
  const current=()=>version===epoch&&playing===job;
  const ready=api('/recordings/'+job.id+'/playback','POST',{});preparation={id:job.id,ready};
  let checking=false;
  const update=state=>{
   if(!current())return;
   status.textContent=state.state==='complete'?'Browser copy ready':`Preparing playback · ${Math.floor(state.availableSeconds||0)} seconds ready`;
   if(state.state==='failed'){clearInterval(poll);status.textContent=state.reason;message(state.reason,true);return;}
   if(!hls&&(state.state==='complete'||state.availableSeconds>=resume+12)){
    const instance=new Hls({startPosition:resume,maxBufferLength:30,backBufferLength:15,xhrSetup:(xhr,url)=>{const u=new URL(url,location.href);if(u.origin!==location.origin||!u.pathname.startsWith('/recorded/'))throw Error('Unexpected recording address');xhr.setRequestHeader('Authorization','Bearer '+token())}});hls=instance;
    instance.on(Hls.Events.MANIFEST_PARSED,()=>{if(current())player.play().catch(()=>{status.textContent='Press Play to start the recording';})});
    instance.on(Hls.Events.ERROR,(_,d)=>{if(current()&&d.fatal){status.textContent='Recording playback failed. Close and reopen to retry.';message(status.textContent,true)}});
    instance.loadSource(state.mediaUrl);instance.attachMedia(player);
   }
   if(state.state==='complete')clearInterval(poll);
  };
  try{const state=await ready;if(!current())return;update(state);if(state.state==='preparing')poll=setInterval(async()=>{if(checking||!current())return;checking=true;try{update(await api('/recordings/'+job.id+'/playback'))}catch(e){if(current())status.textContent=e.message}finally{checking=false}},1500)}catch(e){if(current()){status.textContent=e.message;throw e}}
 }
 async function library(){
  shell('Recordings');dialog.append(action('Schedule a recording',()=>schedule()));const list=node('div');dialog.append(list);
  const refresh=async()=>{const jobs=await api('/recordings');list.replaceChildren();if(!jobs.length)list.append(node('p','No recordings yet.'));
   for(const j of jobs.reverse()){const row=node('section');row.className='panel';row.append(node('h3',(j.status==='recording'?'🔴 ':'')+j.title),node('p',j.channel.name+' · '+new Date(j.start).toLocaleString()),node('p',j.status+' · '+(j.bytes/1048576).toFixed(1)+' MiB'+(j.gaps?' · '+j.gaps+' interruption(s)':'')+(j.reason?' · '+j.reason:'')));
    if(['recording','scheduled'].includes(j.status))row.append(action('Cancel recording',async()=>{await api('/recordings/'+j.id+'/cancel','POST',{});await refresh()}));
    else {if(j.bytes>0)row.append(action(j.resume?'Resume':'Play',()=>play(j)));row.append(action('Delete',async()=>{if(!confirm('Delete this recording permanently?'))return;await api('/recordings/'+j.id,'DELETE');await refresh()}))}list.append(row)}
  };await refresh();poll=setInterval(()=>refresh().catch(()=>{}),5000);
 }
 function programme(channel,p,onWatch){shell(p.title);dialog.append(node('p',new Date(p.start).toLocaleString()+' – '+new Date(p.end).toLocaleTimeString()),action('Watch channel live',async()=>{dialog.close();await onWatch()}),action('Record programme',()=>schedule(channel,p)))}
 return {schedule,library,programme};
}
