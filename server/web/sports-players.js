// ESPN column names are stable keys; totals and values are aligned to each category.
export const playerCategories={
 NFL:[['Passing','passing.passingYards'],['Rushing','rushing.rushingYards'],['Receiving','receiving.receivingYards'],['Defense','defensive.totalTackles'],['Kicking','kicking.fieldGoalsMade']],
 NBA:[['Scoring','offensive.avgPoints'],['Rebounds','general.avgRebounds'],['Assists','offensive.avgAssists'],['Defense','defensive.avgSteals']],
 MLB:[['Batting','batting.avg'],['Home runs','batting.homeRuns'],['Pitching','pitching.ERA']],
 NHL:[['Skaters','offensive.points'],['Goals','offensive.goals'],['Goaltending','defensive.savePct']],
 MLS:[['Scoring','offensive.goals']]
};
export function playerTable(data){
 const columns=new Map(),players=[];
 if(Array.isArray(data.athletes)){
  for(const c of data.categories||[])for(const [i,n] of (c.names||[]).entries())columns.set(c.name+'.'+n,{key:c.name+'.'+n,label:c.labels?.[i]||n,title:c.displayNames?.[i]||n,category:c.name});
  for(const p of data.athletes){const a=p.athlete||{},stats=[];for(const c of p.categories||[]){const def=(data.categories||[]).find(x=>x.name===c.name);for(const [i,n] of (def?.names||[]).entries())stats.push({key:c.name+'.'+n,value:c.totals?.[i]??'—',numeric:Number.isFinite(c.values?.[i])?c.values[i]:null})}players.push({id:a.id,name:a.displayName||'',team:a.teams?.[0]?.abbreviation||a.teamShortName||'',position:a.position?.abbreviation||'',stats})}
 }else{
  const people=new Map();for(const c of data.results||[])for(const p of c.leaders||[]){const a=p.athlete||{};if(!a.id)continue;const row=people.get(a.id)||{id:a.id,name:a.displayName||'',team:data.team?.abbreviation||'',position:a.position?.abbreviation||'',stats:[]};for(const block of p.statistics||[])for(const s of block.stats||[]){const key=block.name+'.'+s.name;columns.set(key,{key,label:s.abbreviation||s.shortDisplayName||s.name,title:s.displayName||s.name,category:block.name});if(!row.stats.some(x=>x.key===key))row.stats.push({key,value:s.displayValue??'—',numeric:Number.isFinite(s.value)?s.value:null})}people.set(a.id,row)}players.push(...people.values());
 }
 return {columns:[...columns.values()],players,page:data.pagination?.page||1,pages:data.pagination?.pages||1,count:data.pagination?.count??players.length};
}
export const preferredPlayerColumns={
 offensive:['avgPoints','avgAssists','fieldGoalPct','threePointFieldGoalPct','freeThrowPct','avgTurnovers','points','goals','assists','powerPlayGoals','shotsTotal'],
 general:['avgRebounds','gamesPlayed','avgMinutes','doubleDouble','tripleDouble','games','wins','losses','plusMinus','timeOnIcePerGame'],
 passing:['passingYards','completions','passingAttempts','completionPct','passingTouchdowns','interceptions','QBRating'],
 rushing:['rushingYards','rushingAttempts','yardsPerRushAttempt','rushingTouchdowns','longRushing','rushingYardsPerGame'],
 receiving:['receivingYards','receptions','receivingTargets','yardsPerReception','receivingTouchdowns','longReception'],
 defensive:['totalTackles','soloTackles','assistTackles','sacks','passesDefended','avgSteals','avgBlocks','savePct','avgGoalsAgainst','saves'],
 batting:['avg','homeRuns','RBIs','hits','runs','onBasePct','OPS'],
 pitching:['ERA','wins','losses','innings','strikeouts','WHIP','saves']
};
export function visiblePlayerColumns(table,metric){const category=metric.split('.')[0];return [...new Set([metric,...(preferredPlayerColumns[category]||[]).map(k=>category+'.'+k),...table.columns.filter(c=>c.category===category).map(c=>c.key)])].map(k=>table.columns.find(c=>c.key===k)).filter(Boolean).slice(0,7)}
