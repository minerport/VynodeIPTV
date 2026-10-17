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
