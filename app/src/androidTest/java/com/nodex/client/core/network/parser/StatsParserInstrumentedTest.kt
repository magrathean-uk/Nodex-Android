package com.nodex.client.core.network.parser

import com.nodex.client.domain.model.AlertCategory
import com.nodex.client.domain.model.AlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsParserInstrumentedTest {

    @Test
    fun parseJournalEvents_parsesValidJsonLines() {
        val parser = StatsParser()
        val raw = """
{"MESSAGE":"Out of memory: Killed process 42","_SYSTEMD_UNIT":"kernel.service","PRIORITY":"2","__REALTIME_TIMESTAMP":"1710000000000000"}
{"MESSAGE":"nginx restarted","SYSLOG_IDENTIFIER":"nginx","PRIORITY":"6","__REALTIME_TIMESTAMP":"1710000001000000"}
""".trimIndent()

        val events = parser.parseJournalEvents(raw)

        assertEquals(2, events.size)
        assertEquals(AlertSeverity.CRITICAL, events[0].severity)
        assertEquals(AlertCategory.MEMORY, events[0].category)
        assertEquals("kernel.service", events[0].sourceService)
        assertEquals(AlertSeverity.INFO, events[1].severity)
        assertEquals(AlertCategory.SERVICE, events[1].category)
        assertEquals("nginx", events[1].sourceService)
    }
}
