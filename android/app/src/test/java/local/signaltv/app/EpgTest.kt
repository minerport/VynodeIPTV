package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class EpgTest {
    @Test fun liveMetadataIgnoresHistoryFutureAndExpiredProgrammes(){
        val past=Programme("Past",0,50);val current=Programme("Current",50,150);val future=Programme("Future",150,200)
        val entries=listOf(past,future,current)
        assertEquals(current,currentProgramme(entries,100))
        assertEquals(future,currentProgramme(entries,150))
        assertNull(currentProgramme(entries,200))
        assertNull(currentProgramme(listOf(past,future),100))
    }

    @Test fun fullGuideIsPreferredAndShortGuideIsFallback()=kotlinx.coroutines.runBlocking {
        val calls=mutableListOf<String>()
        val history=Programme("Past",0,50);val current=Programme("Current",50,150);val future=Programme("Future",150,200)
        assertEquals(listOf(history,current,future),providerEpg(100){calls.add(it);listOf(history,current,future)})
        assertEquals(listOf("get_simple_data_table"),calls)
        calls.clear()
        assertEquals(listOf(current),providerEpg(100){calls.add(it);if(it=="get_simple_data_table")error("Unsupported");listOf(current)})
        assertEquals(listOf("get_simple_data_table","get_short_epg"),calls)
        assertEquals(listOf(history,current),providerEpg(100){if(it=="get_simple_data_table")listOf(history)else listOf(current)})
    }
    @Test fun pastPresentFutureAndGapsCoverEveryMinute(){
        val programmes=listOf(Programme("Past",0,20),Programme("Current",30,60),Programme("Future",80,100))
        val cells=guideCells(programmes,10,120)
        assertEquals(listOf("Past",null,"Current",null,"Future",null),cells.map{it.programme?.title})
        assertEquals(110L,cells.sumOf{it.end-it.start})
        assertTrue(cells.zipWithNext().all{(a,b)->a.end==b.start})
    }
    @Test fun missingAndInvalidListingsStillCoverWindow(){
        assertEquals(listOf(GuideCell(10,50,null)),guideCells(listOf(Programme("Bad",30,20)),10,50))
        assertTrue(guideCells(emptyList(),50,10).isEmpty())
    }
    @Test fun duplicateAndOverlappingListingsDoNotHideGaps(){
        val cells=guideCells(listOf(Programme("B",15,40),Programme("A",0,20),Programme("A",0,20)),0,60)
        assertEquals(listOf(0L,20L,40L),cells.map{it.start})
        assertEquals(listOf("A","B",null),cells.map{it.programme?.title})
    }
    @Test fun timestampFormatsRespectProviderTimezone(){
        val zone=ZoneId.of("America/New_York")
        val expected=java.time.Instant.parse("2026-10-12T16:00:00Z").toEpochMilli()
        assertEquals(expected,epgTime((expected/1000).toString(),"",zone))
        assertEquals(expected,epgTime(expected.toString(),"",zone))
        assertEquals(expected,epgTime("","2026-10-12 12:00:00",zone))
        assertEquals(0,epgTime("","invalid",zone))
    }
    @Test fun plainAndEncodedTitlesRemainReadable(){
        assertEquals("Live news",decodeEpgText("TGl2ZSBuZXdz"))
        assertEquals("News",decodeEpgText("News"))
        assertEquals("",decodeEpgText(""))
    }
}
