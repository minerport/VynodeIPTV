package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test

class MetadataMatchTest {
 private val movie=MetadataCandidate(1,"movie","The Example",date="2020-01-01")
 @Test fun ambiguousRemakesStayUnmatched(){assertNull(chooseMetadata(MetadataQuery("The Example"),listOf(movie,movie.copy(id=2,date="1990-01-01")),1))}
 @Test fun yearDisambiguatesWithoutChangingProviderTitle(){val title="The Example (2020)";assertEquals(1,chooseMetadata(cleanMetadataQuery(title),listOf(movie,movie.copy(id=2,date="1990-01-01")),1)?.id);assertEquals("The Example (2020)",title)}
 @Test fun genericSportsNewsAndIncompleteSearchStayUnmatched(){for(title in listOf("News","NFL Football","No EPG data","Paid programming"))assertFalse(metadataAllowed(MetadataQuery(title)));assertNull(chooseMetadata(MetadataQuery("The Example"),listOf(movie),2));assertNull(chooseMetadata(MetadataQuery("The Examples"),listOf(movie),1))}
 @Test fun adultAndPeopleAreExcluded(){assertNull(chooseMetadata(MetadataQuery("The Example"),listOf(movie.copy(adult=true)),1));assertNull(chooseMetadata(MetadataQuery("The Example"),listOf(movie.copy(kind="person")),1))}
 @Test fun onlyTmdbArtworkIsAccepted(){assertTrue(safeMetadataArtwork("https://image.tmdb.org/t/p/w342/test.jpg"));assertFalse(safeMetadataArtwork("https://evil.example/test.jpg"));assertFalse(safeMetadataArtwork("https://image.tmdb.org/t/p/w342/../test.jpg"))}
}
