import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
test('guide can return from future but cannot browse before now',()=>{
 const source=readFileSync(new URL('../server/web/app.js',import.meta.url),'utf8');
 const fn=source.match(/function shiftGuide\(delta\)\{[^\n]+\}/)[0];
 const context=vm.createContext({Date:{now:()=>10000000},start:10000000,renderGuide(){},scheduleGuide(){}});
 vm.runInContext(fn+';shiftGuide(-1800000)',context);assert.equal(context.start,10000000);
 vm.runInContext('shiftGuide(1800000)',context);assert.equal(context.start,11800000);
 vm.runInContext('shiftGuide(-1800000)',context);assert.equal(context.start,10000000);
});

import {guideStartOnTick} from '../server/web/guide.js';
test('live guide advances after ticks and overnight suspension',()=>{
 assert.equal(guideStartOnTick(1000,60000),60000);
 assert.equal(guideStartOnTick(1000,86400000),86400000);
});
test('future browsing stays put until real time catches up',()=>{
 assert.equal(guideStartOnTick(120000,60000),120000);
 assert.equal(guideStartOnTick(120000,120001),120001);
});

test('browser clock refreshes visible guide and pauses while hidden or logged out',()=>{
 const source=readFileSync(new URL('../server/web/app.js',import.meta.url),'utf8');
 const fn=source.match(/function tickGuide\(\)\{[^\n]+\}/)[0];
 const context=vm.createContext({guideStartOnTick,Date:{now:()=>60000},start:1000,token:'test',document:{hidden:false},$:()=>({hidden:false}),renders:0,loads:0,renderGuide(){this.renders++},scheduleGuide(){this.loads++}});
 // Use global counters so the test does not depend on function receiver binding.
 vm.runInContext('renderGuide=()=>{renders++};scheduleGuide=()=>{loads++};'+fn+';tickGuide()',context);
 assert.equal(context.start,60000);assert.equal(context.renders,1);assert.equal(context.loads,1);
 vm.runInContext('document.hidden=true;start=1000;tickGuide()',context);assert.equal(context.start,1000);assert.equal(context.renders,1);
 vm.runInContext('document.hidden=false;tickGuide()',context);assert.equal(context.start,60000);
 vm.runInContext("token='';tickGuide()",context);assert.equal(context.renders,2);
});
