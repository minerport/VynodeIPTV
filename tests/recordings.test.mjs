import test from 'node:test';
import assert from 'node:assert/strict';
import {recordingUI} from '../server/web/recordings.js';

function element(tag){return {tag,children:[],style:{},listeners:{},append(...nodes){this.children.push(...nodes)},replaceChildren(...nodes){this.children=nodes},addEventListener(name,fn){this.listeners[name]=fn},showModal(){this.open=true},close(){this.open=false;this.listeners.close?.()},pause(){}}}
function fixture(jobs=[],handler){
 const body=element('body');globalThis.document={body,createElement:element};globalThis.confirm=()=>false;
 const calls=[],errors=[];const ui=recordingUI({api:async(path,method,body)=>{calls.push({path,method,body});return handler?handler(path,method,body):jobs},channels:()=>[{id:'one',name:'One'}],token:()=>'',message:(...m)=>errors.push(m)});
 const dialog=body.children[0];return {ui,dialog,calls,errors};
}
function find(root,text){return [root,...root.children.flatMap(c=>all(c))].find(n=>n.textContent===text)}
function all(root){return [root,...root.children.flatMap(all)]}
test('recording programme action preserves watch-live choice',async()=>{const {ui,dialog}=fixture();let watched=0;ui.programme({id:'one'},{title:'Show',start:new Date().toISOString(),end:new Date(Date.now()+3600000).toISOString()},()=>watched++);await find(dialog,'Watch channel live').onclick();assert.equal(watched,1);assert.equal(dialog.open,false)});
test('recording library cancellation uses recording API and leaves playback alone',async()=>{const {ui,dialog,calls}=fixture([{id:'job',title:'Show',channel:{name:'One'},start:new Date().toISOString(),status:'recording',bytes:0}]);try{await ui.library();await find(dialog,'Cancel recording').onclick();assert.ok(calls.some(c=>c.path==='/recordings/job/cancel'&&c.method==='POST'));assert.ok(calls.every(c=>!c.path.includes('playback')))}finally{dialog.close()}});
test('recording deletion requires explicit confirmation',async()=>{const {ui,dialog,calls}=fixture([{id:'job',title:'Show',channel:{name:'One'},start:new Date().toISOString(),status:'failed',bytes:0}]);try{await ui.library();await find(dialog,'Delete').onclick();assert.equal(calls.filter(c=>c.method==='DELETE').length,0);globalThis.confirm=()=>true;await find(dialog,'Delete').onclick();assert.equal(calls.filter(c=>c.method==='DELETE').length,1)}finally{dialog.close()}});

const recorded={id:'job',title:'Show',channel:{name:'One'},start:new Date().toISOString(),status:'completed',bytes:100,resume:42};
test('closing while preparation starts cancels it and preserves saved resume',async()=>{
 globalThis.window={Hls:{isSupported:()=>true}};let resolve;
 const {ui,dialog,calls}=fixture([],async(path,method)=>{if(path==='/recordings')return [recorded];if(method==='POST')return new Promise(r=>resolve=r);return {}});
 await ui.library();const opening=find(dialog,'Resume').onclick();dialog.close();resolve({state:'preparing',availableSeconds:0});await opening;await new Promise(r=>setImmediate(r));
 assert.ok(calls.some(c=>c.path.endsWith('/playback')&&c.method==='DELETE'));assert.equal(calls.filter(c=>c.method==='PUT').length,0);
});
test('prepared playback uses compatible media and explicit resume rather than live edge',async()=>{
 const instances=[];class FakeHls{static isSupported=()=>true;static Events={MANIFEST_PARSED:'manifest',ERROR:'error'};constructor(options){this.options=options;instances.push(this)}on(){}loadSource(url){this.url=url}attachMedia(){}destroy(){this.destroyed=true}}
 globalThis.window={Hls:FakeHls};globalThis.Hls=FakeHls;
 const {ui,dialog,calls}=fixture([],async path=>path==='/recordings'?[recorded]:{state:'complete',availableSeconds:100,mediaUrl:'/recorded/job/browser.m3u8'});
 await ui.library();await find(dialog,'Resume').onclick();assert.equal(instances[0].options.startPosition,42);assert.equal(instances[0].url,'/recorded/job/browser.m3u8');all(dialog).find(n=>n.tag==='video').currentTime=43;dialog.close();assert.equal(instances[0].destroyed,true);assert.ok(calls.some(c=>c.method==='PUT'));
});
