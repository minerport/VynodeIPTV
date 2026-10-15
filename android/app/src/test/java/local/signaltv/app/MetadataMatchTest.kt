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

 @Test fun xmltvAndOnscreenEpisodeNumbering(){assertEquals(1 to 3,parseEpisodeNumber("xmltv_ns","0 . 2 ."));assertEquals(2 to 5,parseEpisodeNumber("xmltv_ns","1/9.4/20.0/1"));assertEquals(0 to 3,parseEpisodeNumber("onscreen","S00E03"));assertNull(parseEpisodeNumber("onscreen","S01E00"));assertNull(parseEpisodeNumber("xmltv_ns",".2."))}
 @Test fun episodeYearDoesNotSelectMovieOrWrongSeries(){val q=cleanMetadataQuery("The Example S02E03","2025");assertEquals("The Example",q.title);assertEquals(3,q.episode);val tv=MetadataCandidate(42,"tv","The Example",date="2010-01-01");assertEquals(42,chooseMetadata(q,listOf(movie,tv),1)?.id);assertNull(chooseMetadata(q,listOf(tv,tv.copy(id=43)),1))}
 @Test fun episodesHaveSeparateCacheKeys(){val a=Programme("The Example",1,2,season=2,episode=3);assertNotEquals(metadataKey(programmeQuery(a)),metadataKey(programmeQuery(a.copy(episode=4))));assertNotEquals(metadataKey(programmeQuery(a)),metadataKey(programmeQuery(a.copy(episodeTitle="Pilot"))))}
 @Test fun backgroundWindowIsBoundedAndDeduplicated(){val now=1000000L;val rows=(0..350).flatMap{val p=Programme("Show $it",now+it*60000L,now+(it+1)*60000L);listOf(p,p)}+Programme("Too far",now+25*3600000L,now+26*3600000L)+Programme("Finished",0,now);val result=upcomingMetadata(rows,now);assertEquals(300,result.size);assertEquals("Show 0",result.first().title)}
 @Test fun missingSeasonIsNotASpecial(){assertNull(explicitEpisode(null,"3"));assertNull(explicitEpisode("","3"));assertNull(explicitEpisode("null","3"));assertEquals(0 to 3,explicitEpisode("0","3"));assertEquals(2 to 3,explicitEpisode("2","3"));assertNull(explicitEpisode("-1","3"))}
 @Test fun shortApiKeysAreDistinguishedFromBearerTokens(){assertTrue(isTmdbApiKey("a".repeat(32)));assertTrue(isTmdbApiKey("ABCDEF0123456789ABCDEF0123456789"));assertFalse(isTmdbApiKey("eyJ.example.token"));assertFalse(isTmdbApiKey("a".repeat(31)))}
}
