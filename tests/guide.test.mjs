import test from 'node:test';
import assert from 'node:assert/strict';
import {providerGroups,inGroup,clipProgramme} from '../server/web/guide.js';
test('provider categories are namespaced and custom groups select exact channels',()=>{const channels=[{id:'a:1',providerId:'a',category:'1',categoryName:'News',name:'One'},{id:'b:1',providerId:'b',category:'1',categoryName:'News',name:'Two'}];assert.equal(providerGroups(channels).length,2);assert.deepEqual(inGroup(channels,{channelIds:['b:1']},'two'),[channels[1]]);assert.equal(inGroup(channels,{channelIds:[]}).length,0)});
test('guide clips programmes crossing both edges without extending cells',()=>{const start=Date.parse('2026-10-12T12:00:00Z'),duration=3600000;assert.deepEqual(clipProgramme({start:'2026-10-12T11:30:00Z',end:'2026-10-12T12:30:00Z'},start,duration),{left:0,width:50});assert.deepEqual(clipProgramme({start:'2026-10-12T12:30:00Z',end:'2026-10-12T14:00:00Z'},start,duration),{left:50,width:50});assert.equal(clipProgramme({start:'bad',end:'bad'},start,duration),null);assert.equal(clipProgramme({start:'2026-10-12T13:00:00Z',end:'2026-10-12T14:00:00Z'},start,duration),null)});
import {orderedGroups,virtualRange} from '../server/web/guide.js';
import {guideCells} from '../server/web/guide.js';
test('timeline includes past, present, future and explicit gaps without overlaps',()=>{
 const p=(title,start,end)=>({title,start:new Date(start).toISOString(),end:new Date(end).toISOString()});
 const cells=guideCells([p('Future',80,100),p('Past',0,20),p('Current',30,60),p('Current',30,60),p('Invalid',70,65)],10,110);
 assert.deepEqual(cells.map(c=>c.programme?.title||null),['Past',null,'Current',null,'Future',null]);
 assert.equal(cells.reduce((sum,c)=>sum+c.end-c.start,0),110);
 assert.ok(cells.slice(1).every((c,i)=>cells[i].end===c.start));
 assert.deepEqual(guideCells([],10,50),[{start:10,end:60,programme:null}]);
});
test('saved group order preserves new groups at the end',()=>{assert.deepEqual(orderedGroups([{id:'a'},{id:'b'},{id:'new'}],['gone','b','a']).map(g=>g.id),['b','a','new'])});
test('continuous guide windows cover scroll position without rendering a whole catalog',()=>{for(const y of [0,620,185333,385330]){const r=virtualRange(6225,y,620);assert.ok(r.end-r.start<=20);assert.equal(r.top+(r.end-r.start)*62+r.bottom,6225*62);assert.ok(r.start<=Math.floor(y/62));assert.ok(r.end>Math.floor(y/62));}assert.deepEqual(virtualRange(0,0,620),{start:0,end:0,top:0,bottom:0});const bottom=virtualRange(12,999999,620);assert.equal(bottom.end,12);assert.ok(bottom.bottom>=0)});
test('initial groups use alphabetical names, independent of provider order',()=>{assert.deepEqual(orderedGroups([{id:'z',name:'Sports'},{id:'b',name:'documentaries'},{id:'a',name:'All channels'},{id:'n',name:'News'}]).map(g=>g.name),['All channels','documentaries','News','Sports'])});
