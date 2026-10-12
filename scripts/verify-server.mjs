import {mkdir, writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const base=process.env.IPTV_TEST_URL||'http://127.0.0.1:8580';
const admin=execFileSync('docker',['exec',process.env.IPTV_TEST_CONTAINER||'signal-iptv-iptv-1','cat','/config/admin.token'],{encoding:'utf8'}).trim();
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function req(path,{token=admin,method='GET',body,status=200}={}){const r=await fetch(base+path,{method,headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});assert.equal(r.status,status,`${method} ${path} status`);return r.json()}
let device='',session='';
try{
 await req('/api/v1/channels',{token:'',status:401});
 const pair=await req('/api/v1/pairing/requests',{token:'',method:'POST',body:{name:'Automated smoke test'},status:201});device=pair.id;
 await req('/api/v1/pairing/approve',{method:'POST',body:{id:pair.id,code:pair.code}});
 const paired=await req('/api/v1/pairing/poll',{token:'',method:'POST',body:{id:pair.id,deviceSecret:pair.deviceSecret}});
 await req('/api/v1/providers',{token:paired.token,status:403});
 const started=await req('/api/v1/playback/sessions',{token:paired.token,method:'POST',body:{channelId:'demo:1'},status:201});session=started.id;
 async function playable(minGeneration){for(let i=0;i<35;i++){await sleep(1000);const s=await req(`/api/v1/playback/sessions/${session}/heartbeat`,{token:paired.token,method:'POST',body:{}});if(s.state==='failed')throw Error(s.reason);if(s.state==='playing'&&s.generation>=minGeneration)return s}throw Error('Playback startup/recovery timed out')}
 const first=await playable(1);assert.ok(first.health.segmentsObserved>0);assert.ok(first.health.segmentAgeMs<12000);const playlist=await fetch(base+first.mediaUrl,{headers:{Authorization:'Bearer '+paired.token}});assert.equal(playlist.status,200);const text=await playlist.text();assert.match(text,/#EXTM3U/);const file=text.split('\n').find(l=>l.startsWith('segment-'));assert.ok(file);
 const segment=await fetch(new URL(file,base+first.mediaUrl),{headers:{Authorization:'Bearer '+paired.token}});assert.equal(segment.status,200);const bytes=Buffer.from(await segment.arrayBuffer());assert.ok(bytes.length>188);assert.equal(bytes[0],0x47);
 await mkdir(new URL('../.local/verification/',import.meta.url),{recursive:true});await writeFile(new URL('../.local/verification/sample.ts',import.meta.url),bytes);
 const unauthorized=await fetch(base+first.mediaUrl);assert.equal(unauthorized.status,401);
 await req(`/api/v1/playback/sessions/${session}/interrupt`,{method:'POST',body:{}});const recovered=await playable(first.generation+1);
 assert.ok(recovered.health.restarts>=1);assert.ok(recovered.health.events.some(e=>e.state==='recovering'));
 const result={passed:true,checks:['unauthenticated API denied','pairing approved with device secret','device denied administration','authenticated HLS generated','MPEG-TS segment received','unauthenticated media denied','worker interruption recovered','segment freshness and recovery history reported'],initialGeneration:first.generation,recoveredGeneration:recovered.generation,segmentBytes:bytes.length};
 await writeFile(new URL('../.local/verification/server-result.json',import.meta.url),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}finally{if(session)await req('/api/v1/playback/sessions/'+session,{method:'DELETE'}).catch(()=>{});if(device)await req('/api/v1/devices/'+device,{method:'DELETE'}).catch(()=>{})}
