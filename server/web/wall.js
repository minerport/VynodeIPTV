// Each tile owns its decoder, recovery budget and server lease.
export class VideoWall {
 constructor({host,api,onChange,onError}) { Object.assign(this,{host,api,onChange,onError}); this.tiles=[];this.capacity=1;this.selected=null;this.queue=Promise.resolve();this.timer=setInterval(()=>this.poll(),3000); }
 enqueue(action){const result=this.queue.catch(()=>{}).then(action);this.queue=result;return result;}
 layout(count){if(count<this.tiles.length)throw Error('Remove streams before choosing a smaller layout.');this.capacity=count;this.render();}
 render(){this.host.dataset.layout=this.capacity;this.host.classList.toggle('wall-mode',this.capacity>1);this.onChange(this);}
 select(tile){this.selected=tile;for(const t of this.tiles){t.video.muted=t!==tile;t.node.classList.toggle('selected',t===tile);}this.onChange(this);}
 async add(channel){return this.enqueue(async()=>{
  if(!window.Hls?.isSupported())throw Error('Use a current Chrome, Edge or Firefox browser for authenticated playback.');
  if(this.capacity===1)await this.clearTiles();
  const existing=this.tiles.find(t=>t.channel.id===channel.id);if(existing){this.select(existing);return;}
  if(this.tiles.length>=this.capacity)throw Error('Wall is full. Remove a tile or choose a larger layout.');
  const session=await this.api('/playback/sessions','POST',{channelId:channel.id,profile:'browser'});
  const node=document.createElement('div'),video=document.createElement('video'),label=document.createElement('span');node.className='wall-tile';node.tabIndex=0;node.setAttribute('role','button');node.setAttribute('aria-label',channel.name+' — select audio; activate for fullscreen');video.playsInline=true;video.muted=true;label.textContent=channel.name;node.append(video,label);this.host.append(node);
  const tile={node,video,label,channel,session,generation:-1,retries:0,lastTime:0,lastProgress:Date.now(),healthySince:0,retryAt:0,status:'Starting stream',hls:null,removed:false,polling:false};
  node.onfocus=()=>this.select(tile);node.onpointerenter=()=>this.select(tile);node.onclick=()=>{this.select(tile);this.fullscreen();};node.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();this.select(tile);this.fullscreen();}if(e.key.startsWith('Arrow')){const columns=getComputedStyle(this.host).gridTemplateColumns.split(' ').length;const delta={ArrowLeft:-1,ArrowRight:1,ArrowUp:-columns,ArrowDown:columns}[e.key];this.tiles[this.tiles.indexOf(tile)+delta]?.node.focus();e.preventDefault();}};
  video.addEventListener('playing',()=>{tile.lastProgress=Date.now();});
  this.tiles.push(tile);this.select(tile);this.render();if(session.state==='playing')this.attach(tile);
 });}
 destroyMedia(t){t.hls?.destroy();t.hls=null;t.video.pause();t.video.removeAttribute('src');t.video.load();}
 attach(t){if(t.removed)return;this.destroyMedia(t);t.generation=t.session.generation;t.lastTime=0;t.lastProgress=Date.now();t.healthySince=0;
  const instance=new Hls({liveSyncDurationCount:3,maxBufferLength:24,backBufferLength:6,xhrSetup:(xhr,url)=>{const u=new URL(url,location.href);if(u.origin!==location.origin||!u.pathname.startsWith('/media/'))throw Error('Unexpected media address');xhr.setRequestHeader('Authorization','Bearer '+this.auth());}});t.hls=instance;
  instance.on(Hls.Events.MANIFEST_PARSED,()=>{if(t.removed||t.hls!==instance)return;t.video.play().catch(()=>{t.status='Select Enable audio / play to begin';this.onChange(this);});});
  instance.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal&&t.hls===instance)this.recover(t,'Browser decoder or network interrupted');});instance.loadSource(t.session.mediaUrl);instance.attachMedia(t.video);
 }
 recover(t,reason){if(t.retryAt||t.removed)return;if(t.retries>=5){t.status='Recovery limit reached. Remove this tile and select the channel to retry.';this.destroyMedia(t);this.onChange(this);return;}t.healthySince=0;t.retries++;t.status=reason+' · retry '+t.retries+'/5';t.retryAt=Date.now()+Math.min(16000,1000*2**(t.retries-1));this.onChange(this);}
 async poll(){await Promise.all(this.tiles.map(async t=>{if(t.polling||t.removed)return;t.polling=true;try{
  const s=await this.api('/playback/sessions/'+t.session.id+'/heartbeat','POST',{});if(t.removed)return;t.session=s;
  if(s.state==='failed'||s.state==='stopped'){this.destroyMedia(t);t.status=s.reason;t.retryAt=0;return;}
  if(s.state!=='playing'){t.status=s.reason;return;}
  if(t.generation!==s.generation){t.retryAt=0;t.retries=0;this.attach(t);}
  if(t.retryAt&&Date.now()>=t.retryAt){t.retryAt=0;this.attach(t);}
  if(!t.video.paused&&t.video.currentTime>t.lastTime+.05){t.lastTime=t.video.currentTime;t.lastProgress=Date.now();t.healthySince ||= Date.now();t.status='Playing live';if(Date.now()-t.healthySince>60000)t.retries=0;}
  else if(!t.video.paused&&Date.now()-t.lastProgress>15000)this.recover(t,'Playback stalled');
  if(t.retries>=5&&!t.retryAt&&Date.now()-t.lastProgress>15000)t.status='Recovery limit reached. Remove this tile and select the channel to retry.';
 }catch(e){if(!t.removed)t.status=e.message;}finally{t.polling=false;if(!t.removed)this.onChange(this);}}));}
 async removeTile(t){await this.api('/playback/sessions/'+t.session.id,'DELETE');t.removed=true;this.destroyMedia(t);t.node.remove();this.tiles=this.tiles.filter(x=>x!==t);if(this.selected===t)this.select(this.tiles[0]||null);}
 async clearTiles(){for(const t of [...this.tiles])await this.removeTile(t);}
 clear(){return this.enqueue(async()=>{await this.clearTiles();this.capacity=1;this.host.classList.remove('expanded','tile-fullscreen');this.render();});}
 remove(){return this.enqueue(async()=>{if(this.selected)await this.removeTile(this.selected);if(!this.tiles.length)this.capacity=1;this.render();});}
 fullscreen(){if(!this.selected)return;this.host.classList.add('expanded','tile-fullscreen');this.host.requestFullscreen?.().catch(()=>{});this.selected.node.focus();this.render();}
 back(){if(this.host.classList.contains('tile-fullscreen')){this.host.classList.remove('tile-fullscreen');if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});if(this.capacity===1)this.host.classList.remove('expanded');this.render();return true;}if(this.host.classList.contains('expanded')){this.host.classList.remove('expanded');this.render();return true;}return false;}
}
