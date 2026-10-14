import test from 'node:test';
import assert from 'node:assert/strict';
import {safeArtwork,metadataPanel} from '../server/web/metadata.js';
test('metadata artwork accepts only fixed TMDB hosts and paths',()=>{assert.equal(safeArtwork('https://image.tmdb.org/t/p/w342/poster.jpg'),'https://image.tmdb.org/t/p/w342/poster.jpg');for(const url of ['javascript:alert(1)','https://evil.example/poster.jpg','https://image.tmdb.org/t/p/w342/../secret.jpg'])assert.equal(safeArtwork(url),'')});
test('provider description appears immediately without waiting for TMDB',()=>{globalThis.document={createElement:tag=>({tag})};const host={children:[],replaceChildren(){this.children=[]},append(e){this.children.push(e)}};let calls=0;const panel=metadataPanel(async()=>{calls++;throw Error('offline')},host);const p={title:'Example',description:'Provider information'};panel.show(p);assert.equal(host.children[0].textContent,p.description);assert.equal(calls,0);panel.reset();assert.equal(calls,0)});
