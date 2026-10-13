import test from 'node:test';
import assert from 'node:assert/strict';
import {VideoWall,enterNativeFullscreen} from '../server/web/wall.js';
function element(){const classes=new Set();return {listeners:{},dataset:{},children:[],classList:{add(...xs){xs.forEach(x=>classes.add(x))},remove(...xs){xs.forEach(x=>classes.delete(x))},contains:x=>classes.has(x),toggle(x,yes){if(yes)classes.add(x);else classes.delete(x)}},append(...xs){this.children.push(...xs)},addEventListener(name,handler){this.listeners[name]=handler;},setAttribute(){},removeAttribute(){},remove(){this.removed=true},pause(){this.paused=true},load(){},focus(){this.onfocus?.()},play(){this.paused=false;return Promise.resolve()}}}
globalThis.document={createElement:element};globalThis.window={Hls:{isSupported:()=>true}};
function fixture(){const calls=[];let count=0;const wall=new VideoWall({host:element(),api:async(path,method,body)=>{calls.push({path,method,body});return {id:String(++count),channelId:body?.channelId,state:'starting',generation:1}},onChange(){}});clearInterval(wall.timer);return {wall,calls}}
test('wall owns independent players and audio follows selection',async()=>{const {wall}=fixture();wall.layout(4);await wall.add({id:'a',name:'A'});await wall.add({id:'b',name:'B'});assert.notEqual(wall.tiles[0].video,wall.tiles[1].video);assert.equal(wall.tiles[0].video.muted,true);assert.equal(wall.tiles[1].video.muted,false);wall.select(wall.tiles[0]);assert.equal(wall.tiles[0].video.muted,false);assert.equal(wall.tiles[1].video.muted,true);assert.throws(()=>wall.layout(1),/Remove streams/);});
test('clear releases all sessions and next tune stays single',async()=>{const {wall,calls}=fixture();wall.layout(6);await wall.add({id:'a'});await wall.add({id:'b'});await wall.clear();assert.equal(wall.capacity,1);assert.equal(wall.tiles.length,0);assert.equal(calls.filter(x=>x.method==='DELETE').length,2);await wall.add({id:'c'});await wall.add({id:'d'});assert.equal(wall.tiles.length,1);assert.equal(wall.selected.channel.id,'d');assert.equal(calls.filter(x=>x.method==='DELETE').length,3);});
test('fullscreen back restores wall then controls without releasing streams',async()=>{const {wall,calls}=fixture();wall.layout(4);await wall.add({id:'a'});wall.fullscreen();assert.equal(wall.host.classList.contains('tile-fullscreen'),true);assert.equal(wall.back(),true);assert.equal(wall.host.classList.contains('expanded'),true);assert.equal(wall.back(),true);assert.equal(wall.host.classList.contains('expanded'),false);assert.equal(wall.tiles.length,1);assert.equal(calls.filter(x=>x.method==='DELETE').length,0);});
test('failed server deletion retains tile for retry instead of losing its lease',async()=>{const {wall}=fixture();await wall.add({id:'a'});wall.api=async()=>{throw Error('offline')};await assert.rejects(wall.clear(),/offline/);assert.equal(wall.tiles.length,1);assert.equal(wall.tiles[0].removed,false);});
test('recovery has one pending retry and a bounded budget',()=>{const {wall}=fixture();const t={retries:0,retryAt:0,healthySince:1,video:element()};wall.recover(t,'stall');const first=t.retryAt;wall.recover(t,'stall');assert.equal(t.retries,1);assert.equal(t.retryAt,first);for(let i=0;i<8;i++){t.retryAt=0;wall.recover(t,'stall')}assert.equal(t.retries,5);});

test('native fullscreen requests hidden browser navigation',async()=>{
 let options;assert.equal(await enterNativeFullscreen({requestFullscreen:async value=>{options=value}},{}),'standard');assert.deepEqual(options,{navigationUI:'hide'});
});
test('iPhone uses video-native fullscreen when container API is absent',async()=>{
 let called=0;assert.equal(await enterNativeFullscreen({}, {readyState:1,webkitEnterFullscreen(){called++}}),'video');assert.equal(called,1);
});
test('rejected container fullscreen falls back to native video',async()=>{
 assert.equal(await enterNativeFullscreen({requestFullscreen:async()=>{throw Error('denied')}},{readyState:1,webkitEnterFullscreen(){}}),'video');
});
test('unsupported or not-yet-ready fullscreen reports an actionable error',async()=>{
 await assert.rejects(enterNativeFullscreen({},{}),/cannot hide its address bar/);
 await assert.rejects(enterNativeFullscreen({},{readyState:0,webkitEnterFullscreen(){throw Error('must not call')}}),/still loading/);
});
test('fullscreen rejection does not pretend CSS expansion is native fullscreen',async()=>{
 const {wall}=fixture();await wall.add({id:'a'});let error;wall.onError=e=>{error=e};await wall.fullscreen();assert.equal(wall.host.classList.contains('tile-fullscreen'),false);assert.equal(wall.host.classList.contains('expanded'),false);assert.match(error.message,/cannot hide its address bar/);assert.equal(wall.tiles.length,1);
});

test('Safari native Done returns to guide and retains the stream',async()=>{
 const {wall,calls}=fixture();await wall.add({id:'a'});const video=wall.selected.video;video.readyState=1;video.webkitEnterFullscreen=()=>{};
 await wall.fullscreen();video.listeners.webkitendfullscreen();
 assert.equal(wall.host.classList.contains('expanded'),false);assert.equal(wall.tiles.length,1);assert.equal(calls.filter(x=>x.method==='DELETE').length,0);
});
