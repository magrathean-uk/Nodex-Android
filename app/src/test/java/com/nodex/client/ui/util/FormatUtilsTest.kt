package com.nodex.client.ui.util

import com.nodex.client.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `formatBytes handles zero`() {
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `formatBytes converts KB`() {
        assertEquals("1.0 KB", formatBytes(1024))
    }

    @Test
    fun `formatBytes converts MB`() {
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
    }

    @Test
    fun `formatBytes converts GB`() {
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `formatUptime formats days`() {
        assertEquals("1d 2h 3m", formatUptime(93780))
    }

    @Test
    fun `formatUptime formats hours only`() {
        assertEquals("5h 30m", formatUptime(19800))
    }

    @Test
    fun `formatUptime formats minutes only`() {
        assertEquals("15m", formatUptime(900))
    }

    @Test
    fun `formatPercent formats correctly`() {
        assertEquals("50%", formatPercent(50.0))
        assertEquals("100%", formatPercent(99.6))
        assertEquals("0%", formatPercent(0.0))
    }

    @Test
    fun `percentOf handles zero total`() {
        assertEquals(0.0, percentOf(100, 0), 0.01)
    }

    @Test
    fun `percentOf calculates correctly`() {
        assertEquals(50.0, percentOf(500, 1000), 0.01)
    }

    @Test
    fun `formatBytesPerSec handles zero`() {
        assertEquals("0 B/s", formatBytesPerSec(0.0))
    }

    @Test
    fun `formatBytesPerSec converts MB per sec`() {
        val result = formatBytesPerSec(1024.0 * 1024.0)
        assertEquals("1.0 MB/s", result)
    }
}
