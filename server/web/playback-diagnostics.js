// Explicit allowlist: never serialize session objects, messages, URLs or IDs.
export function playbackReport(tile,now=Date.now()){
 const number=v=>typeof v==='number'&&Number.isFinite(v)?Math.max(0,Math.round(v)):null;
 const profile=['browser','remux','data-saver'].includes(tile.session?.profile)?tile.session.profile:'unknown';
 const state=['starting','playing','recovering','failed','stopped'].includes(tile.session?.state)?tile.session.state:'unknown';
 const health=tile.session?.health||{};
 let buffer=0;const video=tile.video;
 for(let i=0;i<(video?.buffered?.length||0);i++){if(video.buffered.start(i)<=video.currentTime&&video.buffered.end(i)>=video.currentTime){buffer=video.buffered.end(i)-video.currentTime;break;}}
 const quality=video?.getVideoPlaybackQuality?.();
 return {profile,state,startupMs:tile.firstFrameAt?number(tile.firstFrameAt-tile.requestedAt):null,bufferingEvents:number(tile.bufferEvents),bufferingMs:number((tile.bufferMs||0)+(tile.bufferStarted?now-tile.bufferStarted:0)),bufferAheadMs:number(buffer*1000),reconnectAttempts:number(tile.reconnects),workerRestarts:number(health.restarts),outputAgeMs:number(health.segmentAgeMs),scratchBytes:number(health.scratchBytes),width:number(video?.videoWidth),height:number(video?.videoHeight),droppedFrames:number(quality?.droppedVideoFrames),decodedFrames:number(quality?.totalVideoFrames)};
}
