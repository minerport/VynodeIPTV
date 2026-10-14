import {mkdir, writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
const base=process.env.IPTV_TEST_URL||'http://127.0.0.1:8580';
let admin=process.env.IPTV_TEST_ADMIN_SESSION||'';
if(process.env.IPTV_TEST_ACCOUNT_SETUP!=='1'&&!admin)admin=execFileSync('docker',['exec',process.env.IPTV_TEST_CONTAINER||'signal-iptv-iptv-1','cat','/config/admin.token'],{encoding:'utf8'}).trim();
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function req(path,{token=admin,method='GET',body,status=200}={}){if(process.env.IPTV_TEST_TRACE==='1')console.log(method,path);const r=await fetch(base+path,{signal:AbortSignal.timeout(15000),method,headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});assert.equal(r.status,status,`${method} ${path} status`);return r.json()}
let device='',session='',recording='';
try{
 if(process.env.IPTV_TEST_ACCOUNT_SETUP==='1'){
  const credentials={username:'synthetic-admin',password:randomBytes(24).toString('hex')};
  const status=await req('/api/v1/auth/status',{token:''});assert.equal(status.setup,true);assert.equal(status.migration,false,'Account setup smoke must only run against an empty isolated fixture');
  const created=await req('/api/v1/auth/setup',{token:'',method:'POST',body:credentials});
  await req('/api/v1/auth/logout',{token:created.token,method:'POST',body:{}});
  await req('/api/v1/providers',{token:created.token,status:401});
  const signedIn=await req('/api/v1/auth/login',{token:'',method:'POST',body:credentials});admin=signedIn.token;
 }
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
 const scheduled=await req('/api/v1/recordings',{method:'POST',body:{channelId:'demo:1',title:'Synthetic DVR acceptance',end:new Date(Date.now()+14000).toISOString(),beforeMinutes:0,afterMinutes:0},status:201});recording=scheduled.id;
 await req(`/api/v1/recordings/${recording}/cancel`,{token:paired.token,method:'POST',body:{},status:404});
 let completed;
 for(let i=0;i<40;i++){await sleep(1000);const jobs=await req('/api/v1/recordings');const j=jobs.find(j=>j.id===recording);if(j.status==='failed'||j.status==='partial')throw Error('Synthetic recording '+j.status+': '+j.reason+' (gaps '+j.gaps+')');if(j.status==='completed'){completed=j;break}}
 assert.ok(completed,'Recording did not complete');assert.ok(completed.bytes>188);
 const recordedPath=`/recorded/${recording}/recording.m3u8`;
 assert.equal((await fetch(base+recordedPath)).status,401);
 const recorded=await fetch(base+recordedPath,{headers:{Authorization:'Bearer '+admin}});assert.equal(recorded.status,200);const manifest=await recorded.text();assert.match(manifest,/#EXT-X-ENDLIST/);
 const recordedSegment=manifest.split('\n').find(l=>l.startsWith('part-'));assert.ok(recordedSegment);
 const sample=await fetch(new URL(recordedSegment,base+recordedPath),{headers:{Authorization:'Bearer '+admin}});assert.equal(sample.status,200);assert.equal(new Uint8Array(await sample.arrayBuffer())[0],0x47);
 await req(`/api/v1/recordings/${recording}/resume`,{method:'PUT',body:{seconds:3}});
 assert.equal((await req('/api/v1/recordings')).find(j=>j.id===recording).resume,3);
 const browserPath=`/api/v1/recordings/${recording}/playback`;
 await req(browserPath,{token:paired.token,method:'POST',body:{},status:404});
 await req(browserPath,{method:'POST',body:{}});
 let browser;
 for(let i=0;i<40;i++){browser=await req(browserPath);if(browser.state==='failed')throw Error(browser.reason);if(browser.state==='complete')break;await sleep(1000)}
 assert.equal(browser.state,'complete');assert.ok(browser.availableSeconds>10);
 const browserManifest=await fetch(base+browser.mediaUrl,{headers:{Authorization:'Bearer '+admin}});assert.equal(browserManifest.status,200);const browserText=await browserManifest.text();assert.match(browserText,/#EXT-X-ENDLIST/);
 const browserSegment=browserText.split('\n').find(l=>l.startsWith('browser-'));assert.ok(browserSegment);
 assert.equal((await fetch(new URL(browserSegment,base+browser.mediaUrl),{headers:{Authorization:'Bearer '+admin}})).status,200);
 assert.equal((await fetch(base+browser.mediaUrl)).status,401);
 assert.equal(await (await fetch(base+recordedPath,{headers:{Authorization:'Bearer '+admin}})).text(),manifest,'Original recording retained');
 assert.equal((await req(browserPath,{method:'POST',body:{}})).state,'complete','Completed cache reused');
 const result={passed:true,checks:['unauthenticated API denied','pairing approved with device secret','device denied administration','authenticated HLS generated','MPEG-TS segment received','unauthenticated media denied','worker interruption recovered','segment freshness and recovery history reported','scheduled DVR completed independently of live playback','recorded MPEG-TS retrieved with authentication','recording ownership enforced','recording resume persisted','browser recording conversion completed','derived media authenticated and original retained','completed browser cache reused'],initialGeneration:first.generation,recoveredGeneration:recovered.generation,segmentBytes:bytes.length};
 await writeFile(new URL('../.local/verification/server-result.json',import.meta.url),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}finally{if(recording){await req(`/api/v1/recordings/${recording}/playback`,{method:'DELETE'}).catch(()=>{});await req(`/api/v1/recordings/${recording}/cancel`,{method:"POST",body:{}}).catch(()=>{});await req(`/api/v1/recordings/${recording}`,{method:"DELETE"}).catch(()=>{})}if(session)await req('/api/v1/playback/sessions/'+session,{method:'DELETE'}).catch(()=>{});if(device)await req('/api/v1/devices/'+device,{method:'DELETE'}).catch(()=>{})}
