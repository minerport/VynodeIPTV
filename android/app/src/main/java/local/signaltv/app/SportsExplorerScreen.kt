package local.signaltv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 TextButton(onClick={open=true}){Text(label+": "+(choices.find{it.first==value}?.second?:value))}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text(label)},text={LazyColumn(Modifier.heightIn(max=320.dp)){items(choices,key={it.first}){(id,name)->TextButton(onClick={open=false;change(id)}){Text(name)}}}},confirmButton={TextButton(onClick={open=false}){Text("Close")}})
}
@Composable fun SportsExplorerScreen(store:SportsStore,catalog:InterestCatalog,favorites:List<String>,close:()->Unit){
 var league by remember{mutableStateOf(catalog.teams.find{it.id in favorites}?.league?:"NFL")};var season by remember{mutableStateOf("")};var tab by remember{mutableStateOf("Standings")};var query by remember{mutableStateOf("")};var group by remember{mutableStateOf("")};var metric by remember{mutableStateOf("wins")};var descending by remember{mutableStateOf(true)};var onlyFavorites by remember{mutableStateOf(false)};var category by remember{mutableStateOf("")};var picked by remember{mutableStateOf(favorites.filter{id->catalog.teams.any{it.id==id&&it.league==league}}.take(4))};var choosing by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)}
 var rows by remember{mutableStateOf(emptyList<SportsRow>())};var detailed by remember{mutableStateOf<Map<String,Pair<String,List<SportsStat>>>>(emptyMap())};var leaders by remember{mutableStateOf(emptyList<SportsLeader>())};var games by remember{mutableStateOf(emptyList<SportsGame>())};var athlete by remember{mutableStateOf<SportsLeader?>(null)};var athleteRows by remember{mutableStateOf(emptyList<SportsAthleteSeason>())};var busy by remember{mutableStateOf(true)};var notice by remember{mutableStateOf("")}
 val teamOptions=catalog.teams.filter{it.league==league}
 LaunchedEffect(league,season,tab,picked,revision,athlete){busy=true;notice="";rows=emptyList();detailed=emptyMap();leaders=emptyList();games=emptyList();athleteRows=emptyList()
 try{val base=store.data("standings",league,season=season);rows=parseStandings(base.data,league);notice=(if(base.stale)"Cached · " else "Updated · ")+Instant.ofEpochMilli(base.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d HH:mm"))+" · ESPN";val year=season.ifBlank{rows.firstOrNull()?.seasonYear?.takeIf{it>0}?.toString().orEmpty()}
 when(tab){
 "Compare teams","Team statistics"->{val results=mutableMapOf<String,Pair<String,List<SportsStat>>>();for(id in picked){try{val p=store.data("statistics",league,id,year);results[id]=(sportsSourceSeason(p.data)+(if(p.stale)" · cached" else "")) to sportsDetailed(p.data)}catch(e:CancellationException){throw e}catch(_:Exception){results[id]="Unavailable" to emptyList()}};detailed=results}
 "Players"->{if(athlete!=null){val p=store.data("athlete",league,athlete!!.id);athleteRows=sportsAthlete(p.data);notice+=" · Career statistics"}else{val p=store.data("leaders",league,season=year);leaders=sportsLeaders(p.data);if(category !in leaders.map{it.category})category=leaders.firstOrNull()?.category.orEmpty();notice+=" · "+sportsSourceSeason(p.data)}}
 "Schedule"->{val id=picked.firstOrNull()?:teamOptions.firstOrNull()?.id;if(id!=null){val p=store.data("schedule",league,id,year);games=sportsGames(p,league,true).sortedBy{it.start}}}
 }
 }catch(e:CancellationException){throw e}catch(_:Exception){notice="Sports data unavailable. Try Refresh. Guide and playback remain available."}finally{busy=false}}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
 Column(Modifier.fillMaxSize().background(TvPalette.Background).padding(18.dp)){
 Row{Text("Sports center",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));TextButton(onClick=close){Text("Back to For you")}}
 LazyRow{item{SportsChoice("League",league,sportsPaths.keys.map{it to it}){league=it;picked=emptyList();group="";category="";athlete=null}};item{SportsChoice("Season",season,listOf("" to "Automatic")+((LocalDate.now().year+1)downTo 2020).map{it.toString() to it.toString()}){season=it}};item{TextButton(onClick={revision++}){Text("Refresh")}}}
 LazyRow{items(listOf("Standings","Compare teams","Team statistics","Players","Schedule")){name->TextButton(onClick={tab=name;category="";athlete=null;query=""}){Text((if(tab==name)"• " else "")+name)}}}
 Text(notice,style=MaterialTheme.typography.bodySmall)
 if(busy){LinearProgressIndicator(Modifier.fillMaxWidth())}else{
 TvTextInput(query,{query=it},label={Text(if(tab=="Players"&&athlete==null)"Find player or team" else "Search teams or statistics")},singleLine=true)
 when(tab){
 "Standings"->{val metrics=rows.flatMap{it.stats}.filter{it.numeric!=null}.distinctBy{it.key}.map{it.key to it.label};LazyRow{item{SportsChoice("Conference",group,listOf("" to "All")+rows.map{it.group}.distinct().map{it to it}){group=it}};item{SportsChoice("Sort",metric,listOf("name" to "Team name")+metrics){metric=it}};item{TextButton(onClick={descending=!descending}){Text(if(descending)"Descending" else "Ascending")}};item{TextButton(onClick={onlyFavorites=!onlyFavorites}){Text(if(onlyFavorites)"My teams" else "All teams")}}}
 val filtered=sportsSort(rows.filter{(!onlyFavorites||it.id in favorites)&&(group.isBlank()||it.group==group)&&(it.name+" "+it.group).contains(query,true)},metric,descending)
 LazyColumn(Modifier.weight(1f)){items(filtered,key={it.id}){r->Column(Modifier.fillMaxWidth().padding(10.dp).focusable()){Text(r.name+" · "+r.group,style=MaterialTheme.typography.titleMedium);Text(r.season+" · "+r.phase,style=MaterialTheme.typography.bodySmall);Text((r.stats.find{it.key==metric}?.let{it.label+": "+it.value+" · "}?:"")+sportsCompact(r),style=MaterialTheme.typography.bodySmall)}}}
 }
 "Compare teams","Team statistics"->{TextButton(onClick={choosing=true}){Text("Select up to 4 teams")};val categories=detailed.values.flatMap{it.second}.map{it.category}.distinct();SportsChoice("Category",category,listOf("" to "All categories")+categories.map{it to it}){category=it}
 val stats=detailed.values.flatMap{it.second}.filter{(category.isBlank()||it.category==category)&&(it.label+" "+it.category).contains(query,true)}.distinctBy{it.key};if(picked.isEmpty())Text("Choose teams to compare their season statistics.")
 Row(Modifier.fillMaxWidth().weight(1f)){Column(Modifier.fillMaxWidth()){Row{Text("Statistic",Modifier.weight(1.3f));for(id in picked)Text((catalog.teams.find{it.id==id}?.name?:id)+"\n"+detailed[id]?.first.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)};LazyColumn{items(stats,key={it.key}){s->Row(Modifier.padding(vertical=8.dp).focusable()){Text(s.category+" · "+s.label,Modifier.weight(1.3f),style=MaterialTheme.typography.bodySmall);for(id in picked)Text(detailed[id]?.second?.find{it.key==s.key}?.value?:"—",Modifier.weight(1f))}}}}}
 }
 "Players"->{if(athlete!=null){TextButton(onClick={athlete=null;category=""}){Text("Back to leaders · "+athlete!!.name)};val categories=athleteRows.map{it.category}.distinct();SportsChoice("Category",category,listOf("" to "All")+categories.map{it to it}){category=it};LazyColumn(Modifier.weight(1f)){items(athleteRows.filter{(category.isBlank()||it.category==category)&&(season.isBlank()||it.year.toString()==season)}.sortedByDescending{it.year}){r->Column(Modifier.fillMaxWidth().padding(10.dp).focusable()){Text(r.season+" · "+r.team+" · "+r.category);for(s in r.stats.filter{it.label.contains(query,true)})Text(s.label+": "+s.value,style=MaterialTheme.typography.bodySmall)}}}}
 else{Text("ESPN leaders: up to 10 supplied per category. Select a player for career statistics.",style=MaterialTheme.typography.bodySmall);SportsChoice("Statistic",category,leaders.map{it.category}.distinct().map{it to it}){category=it};TextButton(onClick={descending=!descending}){Text("Reverse order")};val filtered=leaders.filter{it.category==category&&(it.name+" "+it.team).contains(query,true)}.sortedWith(compareBy<SportsLeader>{if(descending)-(it.numeric?:Double.NEGATIVE_INFINITY)else it.numeric?:Double.POSITIVE_INFINITY}.thenBy{it.name});if(filtered.isEmpty())Text("No leaders available for this league, season or filter.");LazyColumn(Modifier.weight(1f)){items(filtered,key={it.id}){p->TextButton(onClick={athlete=p;category="";query=""}){Text("#"+p.rank+" · "+p.name+" · "+p.team+" · "+p.value)}}}}
 }
 "Schedule"->{SportsChoice("Team",picked.firstOrNull()?:teamOptions.firstOrNull()?.id.orEmpty(),teamOptions.map{it.id to it.name}){picked=listOf(it)};SportsChoice("Status",category,listOf("" to "All","pre" to "Scheduled","in" to "In progress","post" to "Finished")){category=it};LazyColumn(Modifier.weight(1f)){items(games.filter{(category.isBlank()||it.state==category)&&it.name.contains(query,true)},key={it.id}){g->Column(Modifier.fillMaxWidth().padding(10.dp).focusable()){Text(g.name);Text((if(g.timeValid)Instant.ofEpochMilli(g.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d HH:mm"))else "Time TBD")+" · "+sportsStatus(g,System.currentTimeMillis()));Text(g.teams.joinToString(" / "){it.name+" "+it.score},style=MaterialTheme.typography.bodySmall)}}}}
 }
 }
 }
 }
 if(choosing){var draft by remember{mutableStateOf(picked)};AlertDialog(onDismissRequest={choosing=false},title={Text("Compare teams")},text={LazyColumn(Modifier.height(300.dp)){items(teamOptions,key={it.id}){t->Row{Checkbox(checked=t.id in draft,onCheckedChange={on->if(!on||draft.size<4)draft=if(on)draft+t.id else draft-t.id});Text(t.name)}}}},confirmButton={TextButton(onClick={picked=draft;choosing=false}){Text("Apply")}},dismissButton={TextButton(onClick={choosing=false}){Text("Cancel")}})}
}
