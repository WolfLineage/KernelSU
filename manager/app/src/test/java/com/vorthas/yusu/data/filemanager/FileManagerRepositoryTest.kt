package com.vorthas.yusu.data.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerRepositoryTest {
    @Test
    fun parentPaths() {
        assertEquals("/sdcard", FileManagerRepository.parent("/sdcard/Download"))
        assertEquals("/", FileManagerRepository.parent("/"))
        assertEquals("/", FileManagerRepository.parent("/sdcard"))
    }

    @Test
    fun formatSizeUsesReadableUnits() {
        assertEquals("0 B", FileManagerRepository.formatSize(0))
        assertEquals("1.0 KB", FileManagerRepository.formatSize(1024))
        assertEquals("2.0 KB", FileManagerRepository.formatSize(2048))
    }

    @Test
    fun hexDumpUsesOffsetAndAsciiColumns() {
        val page = FilePage(
            offset = 0x10,
            totalBytes = 32,
            bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00),
            text = "",
            kind = FileKind.ARCHIVE,
        )
        val dump = page.hexDump()
        assertTrue(dump.startsWith("00000010  50 4B 03 04"))
        assertTrue(dump.contains("|PK"))
        assertEquals(1, dump.count { it == '\n' })
    }

    @Test
    fun pageKnowsWhenItReachesEnd() {
        val page = FilePage(100L, 104L, ByteArray(4), "", FileKind.BINARY)
        assertTrue(page.atEnd)
    }

    @Test
    fun archiveKindPrefersLongestSuffix() {
        assertEquals(FileViewerRepository.ArchiveKind.TAR, FileViewerRepository.archiveKind("/sdcard/a.tar.gz"))
        assertEquals(FileViewerRepository.ArchiveKind.GZIP, FileViewerRepository.archiveKind("/sdcard/a.gz"))
        assertEquals(FileViewerRepository.ArchiveKind.ZIP, FileViewerRepository.archiveKind("/sdcard/a.apk"))
        assertEquals(FileViewerRepository.ArchiveKind.SEVENZ, FileViewerRepository.archiveKind("/sdcard/a.7z"))
    }

    @Test
    fun archiveEntriesCannotEscapeTarget() {
        assertNull(FileManagerRepository.sanitizeEntry("/etc/passwd"))
        assertNull(FileManagerRepository.sanitizeEntry("../"))
        assertEquals("etc/passwd", FileManagerRepository.sanitizeEntry("../../etc/passwd"))
        assertEquals("dir/file.txt", FileManagerRepository.sanitizeEntry("dir\\file.txt"))
    }
}
