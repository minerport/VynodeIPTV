export function providerGroups(channels){
 const groups=new Map();
 for(const c of channels){const id='provider:'+c.providerId+':'+c.category;if(!groups.has(id))groups.set(id,{id,name:c.categoryName||'Other channels',channelIds:[]});groups.get(id).channelIds.push(c.id)}
 return [...groups.values()];
}
export function inGroup(channels,group,query=''){
 const ids=group?new Set(group.channelIds):null;
 return channels.filter(c=>(!ids||ids.has(c.id))&&c.name.toLocaleLowerCase().includes(query.toLocaleLowerCase()));
}
export function clipProgramme(p,start,duration){
 const from=Date.parse(p.start),to=Date.parse(p.end);
 if(!Number.isFinite(from)||!Number.isFinite(to)||to<=from||to<=start||from>=start+duration)return null;
 return {left:Math.max(0,(from-start)/duration)*100,width:(Math.min(start+duration,to)-Math.max(start,from))/duration*100};
}

export function orderedGroups(groups,order=[]){const ranks=new Map(order.map((id,i)=>[id,i]));return [...groups].sort((a,b)=>(ranks.get(a.id)??Infinity)-(ranks.get(b.id)??Infinity)||String(a.name||a.id).localeCompare(String(b.name||b.id),undefined,{sensitivity:"base",numeric:true}));}
export function virtualRange(count,scrollTop,height,rowHeight=62,overscan=5){const first=Math.max(0,Math.min(Math.max(0,count-1),Math.floor(scrollTop/rowHeight)));const start=Math.max(0,first-overscan);const end=Math.min(count,first+Math.ceil(height/rowHeight)+overscan);return {start,end,top:start*rowHeight,bottom:(count-end)*rowHeight};}
