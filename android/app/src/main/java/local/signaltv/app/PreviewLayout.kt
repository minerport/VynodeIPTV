package local.signaltv.app
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
@Composable fun PreviewLayout(side:Boolean,modifier:Modifier=Modifier,content:@Composable ()->Unit){
 Layout(content=content,modifier=modifier){children,c->
  val gap=16.dp.roundToPx();val first=children[0].measure(c.copy(minWidth=0,minHeight=0));val width=if(side)c.maxWidth else (c.maxWidth-first.width-gap).coerceAtLeast(0)
  val second=children[1].measure(Constraints(minWidth=width,maxWidth=width,maxHeight=c.maxHeight))
  layout(c.maxWidth,if(side)first.height+gap+second.height else maxOf(first.height,second.height)){first.placeRelative(0,0);second.placeRelative(if(side)0 else first.width+gap,if(side)first.height+gap else 0)}
 }
}
