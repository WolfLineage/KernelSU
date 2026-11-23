package com.vorthas.yusu.data.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun archiveEntriesCannotEscapeTarget() {
        assertNull(FileManagerRepository.sanitizeEntry("/etc/passwd"))
        assertNull(FileManagerRepository.sanitizeEntry("../"))
        assertEquals("etc/passwd", FileManagerRepository.sanitizeEntry("../../etc/passwd"))
        assertEquals("dir/file.txt", FileManagerRepository.sanitizeEntry("dir\\file.txt"))
    }
}
