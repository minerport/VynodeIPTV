import test from 'node:test';
import assert from 'node:assert/strict';
import {addRecent,guideDensities} from '../server/web/discovery.js';
import {virtualRange} from '../server/web/guide.js';
test('recent tuning moves existing channels to front without duplicates',()=>{
 assert.deepEqual(addRecent(['a','b','c'],'b'),['b','a','c']);
 assert.equal(addRecent(Array.from({length:20},(_,i)=>String(i)),'new').length,20);
});
test('density virtualization uses actual row stride at every size',()=>{
 for(const height of Object.values(guideDensities)){
  const r=virtualRange(100,height*20,height*8,height);
  assert.equal(r.top,r.start*height);assert.equal(r.bottom,(100-r.end)*height);
  assert.ok(r.start<=20&&r.end>=28);
 }
});
