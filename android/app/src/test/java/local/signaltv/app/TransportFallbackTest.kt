package local.signaltv.app
import org.junit.Test
import org.junit.Assert.*
class TransportFallbackTest{
 @Test fun preservesEncodedCredentialsAndHost(){assertEquals("http://provider.example/path?password=a%26b",fallbackAddress("https://provider.example:443/path?password=a%26b"));assertEquals("http://provider.example:8443/path",fallbackAddress("https://provider.example:8443/path"));assertNull(fallbackAddress("http://provider.example/path"));assertNull(fallbackAddress("https://user:password@provider.example/path"))}
 @Test fun onlyNetworkFailuresQualify(){assertTrue(transportFailure(javax.net.ssl.SSLHandshakeException("fixture")));assertTrue(transportFailure(java.net.ConnectException()));assertFalse(transportFailure(IllegalArgumentException("HTTP 401")));assertFalse(Profile("direct","https://provider").httpFallback)}
}
