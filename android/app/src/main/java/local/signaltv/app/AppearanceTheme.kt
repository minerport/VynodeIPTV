package local.signaltv.app

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

fun appearanceFont(name:String)=when(name){"modern"->FontFamily(Font(R.font.inter_variable));"readable"->FontFamily(Font(R.font.atkinson_variable));else->FontFamily.SansSerif}
@Composable fun AppearanceTheme(content:@Composable ()->Unit){
 val settings=Appearance.current;val density=LocalDensity.current
 val family=remember(settings.font){appearanceFont(settings.font)}
 val typography=remember(family){
 val t=Typography()
 fun TextStyle.font()=copy(fontFamily=family)
 Typography(displayLarge=t.displayLarge.font(),displayMedium=t.displayMedium.font(),displaySmall=t.displaySmall.font(),headlineLarge=t.headlineLarge.font(),headlineMedium=t.headlineMedium.font(),headlineSmall=t.headlineSmall.font(),titleLarge=t.titleLarge.font(),titleMedium=t.titleMedium.font(),titleSmall=t.titleSmall.font(),bodyLarge=t.bodyLarge.font(),bodyMedium=t.bodyMedium.font(),bodySmall=t.bodySmall.font(),labelLarge=t.labelLarge.font(),labelMedium=t.labelMedium.font(),labelSmall=t.labelSmall.font())}
 val scheme=if(settings.palette=="pearl")lightColorScheme() else darkColorScheme()
 CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*settings.scale)){
 MaterialTheme(typography=typography,colorScheme=scheme.copy(primary=TvPalette.Accent,onPrimary=TvPalette.OnAccent,secondary=TvPalette.Accent,onSecondary=TvPalette.OnAccent,background=TvPalette.Background,onBackground=TvPalette.Text,surface=TvPalette.Surface,onSurface=TvPalette.Text,surfaceVariant=TvPalette.Track,onSurfaceVariant=TvPalette.Muted,surfaceTint=TvPalette.Accent,primaryContainer=TvPalette.Live,onPrimaryContainer=TvPalette.Text,secondaryContainer=TvPalette.Surface,onSecondaryContainer=TvPalette.Text,outline=TvPalette.GuideBorder),content=content)
 }
}
/** Low-cost glass treatment. No video blur or per-cell offscreen rendering. */
fun Modifier.appearancePanel():Modifier {
 val s=Appearance.current;val shape=RoundedCornerShape(s.radius.dp)
 val colors=when(s.surface){"frosted"->listOf(TvPalette.Surface.copy(alpha=.94f),TvPalette.Glow.copy(alpha=.86f));"smoked"->listOf(TvPalette.Surface,TvPalette.Background.copy(alpha=.93f));else->listOf(TvPalette.Surface,TvPalette.Surface)}
 return clip(shape).background(Brush.linearGradient(colors)).border(if(s.borders=="strong")2.dp else 1.dp,TvPalette.Line,shape)
}
