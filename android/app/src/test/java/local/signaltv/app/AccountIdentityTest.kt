package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class AccountIdentityTest {
 @Test fun pairingAndSwitchingPreserveStandaloneCredentials(){val direct=Profile("direct","https://provider",username="one",password="fixture");val companion=Profile("server","https://server",token="fixture-token");val paired=updatedAccounts(listOf(direct),companion,direct);assertEquals(listOf(direct,companion),paired);assertEquals(listOf(companion,direct),updatedAccounts(paired,direct,companion));assertNotEquals(direct.accountKey(),companion.accountKey())}
 @Test fun passwordEditUpdatesOnlyItsOwnAccount(){val first=Profile("direct","https://provider",username="one",password="old");val second=first.copy(username="two");val changed=first.copy(password="new");assertEquals(listOf(second,changed),updatedAccounts(listOf(first,second),changed,first));assertEquals(first.accountKey(),changed.accountKey())}
 @Test fun identitiesSeparateUsersAndProvidersAndSurviveTrailingSlash(){assertNotEquals(accountKey("direct","https://a","one"),accountKey("direct","https://a","two"));assertNotEquals(accountKey("direct","https://a","one"),accountKey("direct","https://b","one"));assertEquals(accountKey("direct","https://a/","one"),accountKey("direct","https://a","one"))}
 @Test fun existingPreferenceKeyIsPreserved(){val legacy=java.security.MessageDigest.getInstance("SHA-256").digest("direct|https://a|one".toByteArray()).joinToString(""){"%02x".format(it)};assertEquals(legacy,accountKey("direct","https://a","one"))}
 @Test fun xmltvTimesUseExplicitOffsets(){assertEquals(java.time.Instant.parse("2026-10-14T16:00:00Z").toEpochMilli(),xmltvTime("20261014120000 -0400"));assertEquals(0,xmltvTime("broken"))}
}
