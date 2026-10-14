import {enterNativeFullscreen} from './wall.js';

export function recordingUI({api,channels,token,message,onPlay=()=>{},onStop=()=>{}}) {
 const dialog=document.createElement('dialog');dialog.className='recording-dialog';document.body.append(dialog);
 let hls=null,video=null,playing=null,poll=null;
 const node=(tag,text)=>{const n=document.createElement(tag);if(text)n.textContent=text;return n};
 const action=(text,fn)=>{const b=node('button',text);b.type='button';b.onclick=async()=>{b.disabled=true;try{await fn()}catch(e){message(e.message,true)}finally{b.disabled=false}};return b};
 const stop=()=>{if(video&&playing)api('/recordings/'+playing.id+'/resume','PUT',{seconds:video.currentTime||0}).catch(()=>{});hls?.destroy();hls=null;video?.pause();if(playing)onStop();video=null;playing=null;clearInterval(poll)};
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
  shell(job.title);onPlay();video=node('video');video.controls=true;video.playsInline=true;video.style.width='100%';playing=job;dialog.append(video,action('Fullscreen',()=>enterNativeFullscreen(video,video)));
  if(!window.Hls?.isSupported())throw Error('Recorded playback requires a browser supporting authenticated HLS, such as current Chrome, Edge or Firefox.');
  hls=new Hls({xhrSetup:(xhr,url)=>{const u=new URL(url,location.href);if(u.origin!==location.origin||!u.pathname.startsWith('/recorded/'))throw Error('Unexpected recording address');xhr.setRequestHeader('Authorization','Bearer '+token())}});
  hls.on(Hls.Events.MANIFEST_PARSED,()=>{video.currentTime=job.resume||0;video.play().catch(()=>{})});hls.on(Hls.Events.ERROR,(_,d)=>{if(d.fatal)message('Recording cannot be decoded in this browser. Original provider codecs are retained; try the Android app.',true)});hls.loadSource('/recorded/'+job.id+'/recording.m3u8');hls.attachMedia(video);
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
