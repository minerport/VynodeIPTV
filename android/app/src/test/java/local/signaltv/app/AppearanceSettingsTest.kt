package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class AppearanceSettingsTest {
 @Test fun invalidValuesReturnDefaults(){assertEquals(AppearanceSettings(),AppearanceSettings(layout="invalid",palette="invalid",surface="invalid",font="url",scale=Float.NaN,density="tiny",corners="giant",borders="none",focus="invisible",artwork="bad").normalized())}
 @Test fun acceptedChoicesRoundTrip(){val s=AppearanceSettings(layout="cinema",palette="pearl",surface="frosted",font="readable",scale=1.15f,density="large",corners="rounded",borders="strong",focus="fill",artwork="header");assertEquals(s,s.normalized());assertEquals(14,s.radius);assertEquals(GuideDensity.LARGE,s.guideDensity)}
 @Test fun scaleCannotHideControls(){for(scale in listOf(0f,-1f,Float.POSITIVE_INFINITY,8f))assertEquals(1f,AppearanceSettings(scale=scale).normalized().scale)}
}
