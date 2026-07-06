package com.example

import com.example.data.models.ByteFormatting
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormattingTest {

    @Test
    fun test_formatByteCount_negative() {
        assertEquals("0 B", ByteFormatting.formatByteCount(-100))
        assertEquals("0 B", ByteFormatting.formatByteCount(-1))
    }

    @Test
    fun test_formatByteCount_bytes() {
        assertEquals("0 B", ByteFormatting.formatByteCount(0))
        assertEquals("500 B", ByteFormatting.formatByteCount(500))
        assertEquals("999 B", ByteFormatting.formatByteCount(999))
    }

    @Test
    fun test_formatByteCount_kb() {
        assertEquals("1.0 KB", ByteFormatting.formatByteCount(1000))
        assertEquals("1.5 KB", ByteFormatting.formatByteCount(1500))
        assertEquals("999.0 KB", ByteFormatting.formatByteCount(999000))
    }

    @Test
    fun test_formatByteCount_mb() {
        assertEquals("1.0 MB", ByteFormatting.formatByteCount(1000000))
        assertEquals("1.5 MB", ByteFormatting.formatByteCount(1500000))
    }

    @Test
    fun test_formatByteCount_gb() {
        assertEquals("1.0 GB", ByteFormatting.formatByteCount(1000000000L))
        assertEquals("12.5 GB", ByteFormatting.formatByteCount(12500000000L))
    }

    @Test
    fun test_formatByteCount_tb() {
        assertEquals("1.00 TB", ByteFormatting.formatByteCount(1000000000000L))
        assertEquals("2.54 TB", ByteFormatting.formatByteCount(2540000000000L))
    }
}
