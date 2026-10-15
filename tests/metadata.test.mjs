import test from 'node:test';
import assert from 'node:assert/strict';
import {safeArtwork,metadataPanel,guideMetadata,metadataQuery,programmeMetadataKey,episodeLabel} from '../server/web/metadata.js';
test('metadata artwork accepts only fixed TMDB hosts and paths',()=>{assert.equal(safeArtwork('https://image.tmdb.org/t/p/w342/poster.jpg'),'https://image.tmdb.org/t/p/w342/poster.jpg');for(const url of ['javascript:alert(1)','https://evil.example/poster.jpg','https://image.tmdb.org/t/p/w342/../secret.jpg'])assert.equal(safeArtwork(url),'')});
test('provider description appears immediately without waiting for TMDB',()=>{globalThis.document={createElement:tag=>({tag})};const host={children:[],replaceChildren(){this.children=[]},append(e){this.children.push(e)}};let calls=0;const panel=metadataPanel(async()=>{calls++;throw Error('offline')},host);const p={title:'Example',description:'Provider information'};panel.show(p);assert.equal(host.children[0].textContent,p.description);assert.equal(calls,0);panel.reset();assert.equal(calls,0)});

function fakeElement(tag='button'){
 const e={tag,children:[],dataset:{},isConnected:true,title:'Provider title · schedule',textContent:'',append(...items){for(const item of items){if(item.parent)item.parent.children=item.parent.children.filter(c=>c!==item);item.parent=this;this.children.push(item)}},prepend(item){item.parent=this;this.children.unshift(item)},remove(){if(this.parent)this.parent.children=this.parent.children.filter(c=>c!==this)}};
 Object.defineProperty(e,'firstChild',{get(){return this.children[0]}});return e;
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('visible guide enriches duplicate programmes once without replacing buttons or schedule',async()=>{
 globalThis.document={createElement:fakeElement};let calls=0;
 const guide=guideMetadata(async()=>{calls++;return {data:{poster:'https://image.tmdb.org/t/p/w342/poster.jpg',year:'2020',genres:['Drama'],overview:'TMDB summary'}}},{wait:async()=>{}});
 const p={title:'Example',description:'Episode-specific provider summary'},a=fakeElement(),b=fakeElement();const title=fakeElement('span');title.textContent=p.title;a.append(title);
 guide.show([{p,element:a},{p,element:b}]);await tick();
 assert.equal(calls,1);assert.equal(a.dataset.enriched,'true');assert.equal(a.children[0].tag,'img');assert.equal(a.children[1].firstChild,title);assert.match(a.title,/schedule/);assert.match(a.title,/Episode-specific provider summary/);assert.doesNotMatch(a.title,/TMDB summary/);
 const replacement=fakeElement();guide.show([{p,element:replacement}]);await tick();assert.equal(calls,1);assert.equal(replacement.dataset.enriched,'true');guide.reset();
});
test('departed guide cells and reset requests cannot apply stale artwork',async()=>{
 globalThis.document={createElement:fakeElement};let resolve;const guide=guideMetadata(()=>new Promise(r=>resolve=r),{wait:async()=>{}});const element=fakeElement();guide.show([{p:{title:'Example'},element}]);await tick();guide.reset();resolve({data:{poster:'https://image.tmdb.org/t/p/w342/poster.jpg'}});await tick();assert.equal(element.dataset.enriched,undefined);
});
test('unmatched guide entries stay unchanged and outages back off',async()=>{
 globalThis.document={createElement:fakeElement};let calls=0;const guide=guideMetadata(async()=>{calls++;throw Error('offline')},{wait:async()=>{}});const element=fakeElement(),p={title:'Example'};guide.show([{p,element}]);await tick();guide.show([{p,element}]);await tick();assert.equal(calls,1);assert.equal(element.dataset.enriched,undefined);guide.reset();
});

test('episode lookups and cache identities retain explicit provider episode numbers',()=>{
 const p={title:'The Example',year:'2025',season:2,episode:3,episodeTitle:'The Arrival'};
 assert.deepEqual(metadataQuery(p),p);assert.notEqual(programmeMetadataKey(p),programmeMetadataKey({...p,episode:4}));assert.equal(episodeLabel(p),'S02E03 · The Arrival');assert.equal(episodeLabel({...p,season:0}),'S00E03 · The Arrival');assert.equal(episodeLabel({}), '');
});
