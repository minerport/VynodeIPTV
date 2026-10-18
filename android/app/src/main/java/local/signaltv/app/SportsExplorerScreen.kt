package local.signaltv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun SportsChoice(label:String,value:String,choices:List<Pair<String,String>>,change:(String)->Unit){
 var open by remember{mutableStateOf(false)}
 TextButton(modifier=Modifier.height(34.dp),onClick={open=true}){Text(label+": "+(choices.find{it.first==value}?.second?:value),maxLines=1)}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text(label)},text={LazyColumn(Modifier.heightIn(max=320.dp)){items(choices,key={it.first}){(id,name)->TextButton(modifier=Modifier.height(34.dp),onClick={open=false;change(id)}){Text(name)}}}},confirmButton={TextButton(modifier=Modifier.height(34.dp),onClick={open=false}){Text("Close")}})
}
@Composable private fun SportsGrid(headers:List<String>,values:List<List<String>>,sort:(Int)->Unit={},choose:(Int)->Unit={}){
 val scroll=rememberScrollState();val width=240+((headers.size-1).coerceAtLeast(0)*82)
 Column(Modifier.fillMaxSize().horizontalScroll(scroll)){
  Row(Modifier.width(width.dp).background(Color.White.copy(alpha=.09f))){headers.forEachIndexed{i,h->TextButton(onClick={sort(i)},modifier=Modifier.width(if(i==0)240.dp else 82.dp).height(32.dp)){Text(h,maxLines=1,style=MaterialTheme.typography.labelMedium)}}}
  LazyColumn(Modifier.width(width.dp)){items(values.size){index->var focused by remember{mutableStateOf(false)};TextButton(onClick={choose(index)},shape=RoundedCornerShape(2.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),modifier=Modifier.fillMaxWidth().height(38.dp).onFocusChanged{focused=it.isFocused}.background(if(focused)TvPalette.Accent.copy(alpha=.25f)else Color.White.copy(alpha=if(index%2==0).025f else .055f))){Row(Modifier.fillMaxWidth()){values[index].forEachIndexed{i,v->Text(v,Modifier.width(if(i==0)228.dp else 82.dp),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=TvPalette.Text)}}}}}
 }
}
@Composable fun SportsExplorerScreen(store:SportsStore,catalog:InterestCatalog,favorites:List<String>,initialTab:String="Games",close:()->Unit){
 var league by remember{mutableStateOf(catalog.teams.find{it.id in favorites}?.league?:"NFL")}
 var season by remember{mutableStateOf("")};var tab by remember{mutableStateOf(initialTab)};var query by remember{mutableStateOf("")};var search by remember{mutableStateOf(false)};var group by remember{mutableStateOf("")};var metric by remember{mutableStateOf("wins")};var descending by remember{mutableStateOf(true)};var category by remember{mutableStateOf("")};var picked by remember{mutableStateOf(favorites.filter{id->catalog.teams.any{it.id==id&&it.league==league}})};var revision by remember{mutableIntStateOf(0)}
 var playerMetric by remember{mutableStateOf(sportsPlayerCategories.getValue(league).first().second)};var playerTeam by remember{mutableStateOf("")};var phase by remember{mutableStateOf("2")};var page by remember{mutableIntStateOf(1)}
 var clock by remember{mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(15000);clock=System.currentTimeMillis()}}
 var rows by remember{mutableStateOf(emptyList<SportsRow>())};var detailed by remember{mutableStateOf<Map<String,List<SportsStat>>>(emptyMap())};var players by remember{mutableStateOf(SportsPlayerTable())};var games by remember{mutableStateOf(emptyList<SportsGame>())};var athlete by remember{mutableStateOf<SportsPlayer?>(null)};var athleteRows by remember{mutableStateOf(emptyList<SportsAthleteSeason>())};var busy by remember{mutableStateOf(true)};var notice by remember{mutableStateOf("")};var gameFilter by remember{mutableStateOf("upcoming")}
 val teamOptions=catalog.teams.filter{it.league==league}
 fun name(id:String)=catalog.teams.find{it.id==id}?.name?:id
 LaunchedEffect(league,season,tab,picked,revision,athlete,playerMetric,playerTeam,phase,page,if(tab=="Players")descending else true){
  busy=true;notice="";rows=emptyList();detailed=emptyMap();players=SportsPlayerTable();games=emptyList();athleteRows=emptyList()
  try{
   if(tab=="Players"){
    if(athlete!=null){val p=store.data("athlete",league,athlete!!.id);athleteRows=sportsAthlete(p.data);notice=athlete!!.name+" · Career statistics · ESPN"}
    else{val p=store.data("players",league,playerTeam,season,playerMetric+if(descending)":desc" else ":asc",page,phase);players=sportsPlayers(p.data);notice=sportsSourceSeason(p.data)+" · ESPN"+(if(p.stale)" · Cached" else "")}
   }else if(tab=="Games"){
    val result=mutableListOf<SportsGame>();var available=false;var failed=false
    val board=try{val p=store.data("scoreboard",league);available=true;sportsGames(p,league)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
    for(id in picked){try{result+=sportsGames(store.data("schedule",league,id,season),league,true);available=true}catch(e:CancellationException){throw e}catch(_:Exception){failed=true}}
    check(available);games=sportsMerge(result,board).filter{g->picked.isEmpty()||g.teams.any{it.id in picked}};notice="ESPN · "+(if(picked.isEmpty())"Current league scoreboard — choose a team for full schedule" else "All supplied games for selected teams")+(if(failed)" · Some schedules unavailable" else "")
   }else{
    val p=store.data("standings",league,season=season);rows=parseStandings(p.data,league);notice=(rows.firstOrNull()?.let{it.season+" · "+it.phase}?:"")+" · ESPN"+(if(p.stale)" · Cached" else "");val year=season.ifBlank{rows.firstOrNull()?.seasonYear?.toString().orEmpty()}
    if(tab in listOf("Teams","Compare")){val ids=if(tab=="Teams")listOf(picked.firstOrNull()?:teamOptions.first().id)else picked.take(4).ifEmpty{listOf(teamOptions.first().id)};val results=mutableMapOf<String,List<SportsStat>>();for(id in ids){try{val d=store.data("statistics",league,id,year);results[id]=sportsDetailed(d.data);if(d.stale)notice+=" · Cached team data"}catch(e:CancellationException){throw e}catch(_:Exception){results[id]=emptyList();notice+=" · Team data unavailable"}};detailed=results}
   }
  }catch(e:CancellationException){throw e}catch(_:Exception){notice="Sports data unavailable. Try another season or Refresh."}finally{busy=false}
 }
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
 CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp){
 Column(Modifier.fillMaxSize().background(TvPalette.Background).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("SPORTS",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));SportsChoice("League",league,sportsPaths.keys.map{it to it}){league=it;picked=favorites.filter{id->catalog.teams.any{t->t.id==id&&t.league==it}};playerMetric=sportsPlayerCategories.getValue(it).first().second;playerTeam="";category="";group="";page=1;athlete=null};SportsChoice("Season",season,listOf("" to "Current")+(LocalDate.now().year downTo 2020).map{it.toString() to it.toString()}){season=it;page=1};TextButton(modifier=Modifier.height(34.dp),onClick=close){Text("Close")}}
  Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.04f))){for(t in listOf("Games","Teams","Standings","Players","Compare")){TextButton(modifier=Modifier.height(34.dp),onClick={tab=t;category="";query="";athlete=null},colors=ButtonDefaults.textButtonColors(containerColor=if(tab==t)Color.White.copy(alpha=.12f)else Color.Transparent)){Text(t)}};Spacer(Modifier.weight(1f));TextButton(modifier=Modifier.height(34.dp),onClick={search=true}){Text(if(query.isBlank())"Search" else "Search •")};TextButton(modifier=Modifier.height(34.dp),onClick={revision++}){Text("↻")}}
  Text(notice,style=MaterialTheme.typography.labelSmall,color=TvPalette.Muted,maxLines=1)
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())else when(tab){
   "Standings"->{
    val keys=listOf("wins","losses","ties","otLosses","points","winPercent","pointsFor","pointsAgainst","pointDifferential","streak").filter{k->rows.any{r->r.stats.any{it.key==k}}};val labels=mapOf("wins" to "W","losses" to "L","ties" to "T","otLosses" to "OTL","points" to "PTS","winPercent" to "PCT","pointsFor" to "FOR","pointsAgainst" to "AGAINST","pointDifferential" to "DIFF","streak" to "STRK")
    Row{SportsChoice("Conference",group,listOf("" to "All")+rows.map{it.group}.distinct().map{it to it}){group=it};SportsChoice("Following",category,listOf("" to "All teams","mine" to "My teams")){category=it};Text("Select a column to sort",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(12.dp))}
    val filtered=sportsSort(rows.filter{(category!="mine"||it.id in favorites)&&(group.isBlank()||it.group==group)&&it.name.contains(query,true)},metric,descending)
    SportsGrid(listOf("Team")+keys.map{labels[it]?:it},filtered.map{r->listOf(r.name)+keys.map{k->r.stats.find{it.key==k}?.value?:"—"}},sort={i->val key=if(i==0)"name" else keys[i-1];descending=if(metric==key)!descending else true;metric=key},choose={i->picked=listOf(filtered[i].id);tab="Teams";category=""})
   }
   "Teams","Compare"->{
    val ids=detailed.keys.toList();LazyRow{items(ids){id->SportsChoice(if(tab=="Teams")"Team" else "Compare",id,teamOptions.map{it.id to it.name}){v->picked=ids.map{if(it==id)v else it}.distinct()}};if(tab=="Compare"&&ids.size<4)item{TextButton(modifier=Modifier.height(34.dp),onClick={teamOptions.firstOrNull{it.id !in ids}?.let{picked=ids+it.id}}){Text("+ Team")}};if(tab=="Compare"&&ids.size>1)item{TextButton(modifier=Modifier.height(34.dp),onClick={picked=ids.dropLast(1)}){Text("Remove last")}}}
    if(tab=="Teams"){val t=catalog.teams.find{it.id==ids.firstOrNull()};val r=rows.find{it.id==t?.id};Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.05f)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){if(t!=null)ChannelLogo(t.logo,Modifier.size(44.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(t?.name.orEmpty(),style=MaterialTheme.typography.titleMedium);Text(r?.group.orEmpty(),style=MaterialTheme.typography.bodySmall)};for(k in listOf("overall","pointsFor","pointsAgainst")){r?.stats?.find{it.key==k}?.let{s->Column(Modifier.padding(horizontal=15.dp)){Text(s.value,style=MaterialTheme.typography.titleLarge);Text(s.label,style=MaterialTheme.typography.labelSmall)}}}}}
    val cats=detailed.values.flatten().map{it.category}.distinct();val selected=category.ifBlank{cats.firstOrNull().orEmpty()};SportsChoice("Category",selected,cats.map{it to it}){category=it};val stats=detailed.values.flatten().filter{it.category==selected&&it.label.contains(query,true)}.distinctBy{it.key}
    if(stats.isEmpty())Text("No statistics supplied for this category and season.")else SportsGrid(listOf("Statistic")+ids.map{catalog.teams.find{t->t.id==it}?.abbreviation?:it},stats.map{s->listOf(s.label)+ids.map{id->detailed[id]?.find{it.key==s.key}?.value?:"—"}})
   }
   "Players"->{
    if(athlete!=null){val cats=athleteRows.map{it.category}.distinct();val chosen=category.ifBlank{cats.firstOrNull().orEmpty()};Row{TextButton(modifier=Modifier.height(34.dp),onClick={athlete=null;category=""}){Text("‹ Players")};SportsChoice("Category",chosen,cats.map{it to it}){category=it}};val list=athleteRows.filter{it.category==chosen}.sortedByDescending{it.year};val cols=list.firstOrNull()?.stats.orEmpty();SportsGrid(listOf("Season · Team")+cols.map{it.label},list.map{r->listOf(r.season+" · "+r.team)+cols.map{s->r.stats.find{it.key==s.key}?.value?:"—"}})}else{
     LazyRow{items(sportsPlayerCategories.getValue(league)){(label,key)->TextButton(modifier=Modifier.height(34.dp),onClick={playerMetric=key;descending=!key.endsWith(".ERA");page=1}){Text((if(playerMetric==key)"• " else "")+label)}}}
     LazyRow{item{SportsChoice("Team",playerTeam,listOf("" to "League leaders")+teamOptions.map{it.id to it.name}){playerTeam=it;page=1}};item{SportsChoice("Phase",phase,listOf("1" to "Preseason","2" to "Regular","3" to "Postseason")){phase=it;page=1}};item{SportsChoice("Stat",playerMetric,players.columns.filter{it.category==playerMetric.substringBefore('.')}.map{it.key to it.title}){playerMetric=it;page=1}};item{TextButton(modifier=Modifier.height(34.dp),enabled=page>1,onClick={page--}){Text("‹")}};item{Text("$page / ${players.pages} · ${players.count} players",Modifier.padding(12.dp),style=MaterialTheme.typography.labelMedium)};item{TextButton(modifier=Modifier.height(34.dp),enabled=page<players.pages,onClick={page++}){Text("›")}}}
     val cols=sportsPlayerColumns(players,playerMetric);val filtered=players.players.filter{p->p.stats.any{it.key==playerMetric&&it.numeric!=null}&&(p.name+" "+p.team).contains(query,true)}.sortedWith(Comparator{a,b->val x=a.stats.find{it.key==playerMetric}?.numeric;val y=b.stats.find{it.key==playerMetric}?.numeric;when{x==null&&y==null->a.name.compareTo(b.name);x==null->1;y==null->-1;descending->y.compareTo(x);else->x.compareTo(y)}})
     if(filtered.isEmpty())Text("No player statistics supplied for this team, category or season.")else{Text("Select a column to sort • Select a player for career stats • Search filters this page",style=MaterialTheme.typography.labelSmall,color=TvPalette.Muted);SportsGrid(listOf("Player · Team")+cols.map{it.label+(if(it.key==playerMetric)if(descending)" ↓" else " ↑" else "")},filtered.map{p->listOf(p.name+" · "+p.team)+cols.map{c->p.stats.find{it.key==c.key}?.value?:"—"}},sort={i->if(i>0){val k=cols[i-1].key;descending=if(k==playerMetric)!descending else true;playerMetric=k;page=1}},choose={athlete=filtered[it];category=""})}
    }
   }
   "Games"->{
    Row{SportsChoice("Schedule",if(picked.size==1)picked.first() else "",listOf("" to "My teams")+teamOptions.map{it.id to it.name}){picked=if(it.isBlank())favorites.filter{id->teamOptions.any{t->t.id==id}}else listOf(it)};SportsChoice("Show",gameFilter,listOf("upcoming" to "Coming up","recent" to "Live & results","all" to "Full schedule")){gameFilter=it}}
    val now=clock;val list=games.filter{it.name.contains(query,true)&&(gameFilter=="all"||if(gameFilter=="upcoming")it.state=="pre"||it.start>now else it.state in listOf("in","post"))}.let{if(gameFilter=="recent")it.sortedByDescending{g->g.start}else it.sortedBy{g->g.start}}
    if(list.isEmpty())Text("No games supplied for this selection. Choose a team for its full schedule.")
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(list,key={it.id}){g->OutlinedCard(onClick={picked=g.teams.map{it.id};tab="Compare";category=""},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.width(165.dp)){Text(if(g.timeValid)Instant.ofEpochMilli(g.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE MMM d · h:mm a"))else "Time TBD",style=MaterialTheme.typography.labelMedium);Text(sportsStatus(g,now),style=MaterialTheme.typography.labelSmall)};Column(Modifier.weight(1f)){for(t in g.teams)Row(verticalAlignment=Alignment.CenterVertically){catalog.teams.find{it.id==t.id}?.let{ChannelLogo(it.logo,Modifier.size(28.dp))};Text(t.name,Modifier.weight(1f).padding(horizontal=10.dp));Text(if(g.state=="pre")"—" else t.score,style=MaterialTheme.typography.titleMedium)}};Text("Compare ›",Modifier.padding(start=20.dp),style=MaterialTheme.typography.labelMedium)}}}}
   }
  }
 }
 }
 if(search)AlertDialog(onDismissRequest={search=false},title={Text(if(tab=="Players")"Find on this page" else "Search")},text={TvTextInput(query,{query=it},label={Text("Name or statistic")},singleLine=true)},confirmButton={TextButton(modifier=Modifier.height(34.dp),onClick={search=false}){Text("Done")}},dismissButton={TextButton(modifier=Modifier.height(34.dp),onClick={query="";search=false}){Text("Clear")}})
 }
}
