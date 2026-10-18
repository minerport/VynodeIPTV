import {gameLive} from './sports-model.js';
export const scoreLeagues=['NFL','NBA','MLB','NHL','MLS'];
export const scorePositions=['top-left','top-center','top-right','bottom-left','bottom-center','bottom-right'];
export function scorePreferences(p={}){p=p||{};return {visible:p.visible===true,lastPlay:p.lastPlay!==false,delay:[0,15,30,60,90,120].includes(p.delay)?p.delay:0,mode:['sport','all','pinned'].includes(p.mode)?p.mode:'sport',league:scoreLeagues.includes(p.league)?p.league:'NFL',automatic:p.automatic!==false,position:scorePositions.includes(p.position)?p.position:'bottom-left',size:p.size==='large'?'large':'compact',opacity:[60,80,95].includes(p.opacity)?p.opacity:80,accent:['red','gold','ice'].includes(p.accent)?p.accent:'red',rows:[1,3,5].includes(p.rows)?p.rows:3,rotate:p.rotate===true,pins:[...new Set(Array.isArray(p.pins)?p.pins.filter(x=>typeof x==='string'&&/^(NFL|NBA|MLB|NHL|MLS):\d+$/.test(x)):[])].slice(0,12)}}
const words=s=>' '+String(s||'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim()+' ';
export function scoreContext(channel,title,catalog){const text=words((channel?.name||'')+' '+(channel?.categoryName||channel?.groupName||'')+' '+title);const leagues=scoreLeagues.filter(l=>text.includes(words(l)));const teams=catalog.filter(t=>text.includes(words(t.name)));const found=[...new Set([...leagues,...teams.map(t=>t.league)])];return found.length===1?found[0]:null}
export function watchedGame(game,title,catalog){const text=words(title);if(/\b(replay|classic|highlights|encore)\b/i.test(title||''))return false;return game.teams.length===2&&game.teams.every(t=>{const team=catalog.find(x=>x.id===t.id);return team&&[team.name,...team.aliases].some(a=>text.includes(words(a)))})}
export function overlayGames(games,p,league,title,catalog,now=Date.now()){return games.filter(g=>scoreAvailable(g,now)&&(p.mode==='pinned'?p.pins.includes(g.id):p.mode==='all'||g.league===league)&& (p.mode==='pinned'||!watchedGame(g,title,catalog))).sort((a,b)=>p.mode==='pinned'?p.pins.indexOf(a.id)-p.pins.indexOf(b.id):a.league.localeCompare(b.league)||a.start-b.start)}
export function scorePage(games,page,rows){const pages=Math.max(1,Math.ceil(games.length/rows)),index=((page%pages)+pages)%pages;return {games:games.slice(index*rows,(index+1)*rows),index,pages}}

// Delay whole observations, including game status and disappearance, never just text.
export class ScoreHistory {
 constructor(){this.entries=[]}
 add(league,games,at=Date.now()){this.entries=this.entries.filter(e=>at-e.at<=300000);this.entries.push({league,games,at});if(this.entries.length>160)this.entries.shift()}
 games(delay=0,now=Date.now()){const picked=new Map();for(const e of this.entries)if(e.at<=now-delay*1000&&now-e.at<=300000)picked.set(e.league,e.games);return [...picked.values()].flat()}
 ready(delay=0,now=Date.now()){return this.entries.some(e=>e.at<=now-delay*1000&&now-e.at<=300000)}
}
export function scoreAge(updated,now=Date.now()){const age=Math.max(0,Math.floor((now-updated)/1000));return 'Updated '+(age<60?age+'s':Math.floor(age/60)+'m '+age%60+'s')+' ago'}

export function scoreAvailable(g,now=Date.now()){return g.source!=='schedule'&&Number.isFinite(g.updated)&&now-g.updated>=0&&now-g.updated<=300000&&g.state==='in'&&!/DELAY|SUSPEND|POSTPON|CANCEL|FINAL/i.test(g.status)}
