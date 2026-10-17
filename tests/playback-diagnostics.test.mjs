import test from 'node:test';
import assert from 'node:assert/strict';
import {playbackReport} from '../server/web/playback-diagnostics.js';
test('support export never includes arbitrary session fields or errors',()=>{
 const report=playbackReport({session:{profile:'https://secret',state:'token=secret',mediaUrl:'secret',health:{events:['secret'],restarts:2}},channel:{name:'secret'},status:'secret',requestedAt:100,firstFrameAt:300,bufferEvents:2,bufferMs:100,bufferStarted:800,reconnects:1},1000);
 assert.equal(JSON.stringify(report).includes('secret'),false);assert.equal(report.startupMs,200);assert.equal(report.bufferingMs,300);assert.equal(report.profile,'unknown');assert.equal(report.workerRestarts,2);
});
test('buffer measurement uses only the currently playing buffered range',()=>{
 const report=playbackReport({session:{profile:'remux',state:'playing'},video:{currentTime:12,buffered:{length:2,start:i=>i?10:0,end:i=>i?20:5},getVideoPlaybackQuality:()=>({droppedVideoFrames:4,totalVideoFrames:100})}});
 assert.equal(report.bufferAheadMs,8000);assert.equal(report.droppedFrames,4);assert.equal(report.startupMs,null);
});
