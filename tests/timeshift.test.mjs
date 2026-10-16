import test from 'node:test';import assert from 'node:assert/strict';
import {seekWithinWindow} from '../server/web/timeshift.js';
test('seeking stays within retained media and live jump has headroom',()=>{
 const v={currentTime:150,seekable:{length:1,start:()=>100,end:()=>200}};
 seekWithinWindow(v,-100);assert.equal(v.currentTime,100);
 seekWithinWindow(v,500);assert.equal(v.currentTime,199.9);
 seekWithinWindow(v,null);assert.equal(v.currentTime,197);
 assert.equal(seekWithinWindow({seekable:{length:0}},-30),false);
});
