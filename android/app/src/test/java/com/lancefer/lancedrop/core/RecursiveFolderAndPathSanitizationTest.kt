package com.lancefer.lancedrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecursiveFolderAndPathSanitizationTest {

    @Test
    fun testSimpleFileName() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("photo.jpg")
        assertEquals("", subDir)
        assertEquals("photo.jpg", fileName)
    }

    @Test
    fun testNestedRelativeDirectoryPreservation() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("documents/projects/2026/spec.pdf")
        assertEquals("documents/projects/2026", subDir)
        assertEquals("spec.pdf", fileName)
    }

    @Test
    fun testWindowsBackslashPathSeparators() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("backup\\code\\main.rs")
        assertEquals("backup/code", subDir)
        assertEquals("main.rs", fileName)
    }

    @Test
    fun testAntiPathTraversalProtection() {
        // Must neutralize any .. components
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("../../../etc/shadow")
        assertEquals("etc", subDir)
        assertEquals("shadow", fileName)
        assertTrue("Subdir should not contain dot-dot", !subDir.contains(".."))
    }

    @Test
    fun testSanitizationOfSpecialCharacters() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("folder:special/file*name?.txt")
        assertEquals("folder_special", subDir)
        assertEquals("file_name_.txt", fileName)
    }

    @Test
    fun testEmptyOrPureDotsPath() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("../../..")
        assertEquals("", subDir)
        assertTrue(fileName.startsWith("file_"))
    }

    @Test
    fun testResolveUniqueNameWithExtension() {
        val existing = setOf("document.pdf", "document (1).pdf")
        val uniqueName = SocketTransferServer.resolveUniqueName("document.pdf") { candidate ->
            existing.contains(candidate)
        }
        assertEquals("document (2).pdf", uniqueName)
    }

    @Test
    fun testResolveUniqueNameWithoutExtension() {
        val existing = setOf("archive", "archive (1)")
        val uniqueName = SocketTransferServer.resolveUniqueName("archive") { candidate ->
            existing.contains(candidate)
        }
        assertEquals("archive (2)", uniqueName)
    }

    @Test
    fun testUnicodeAndAccentedFilenamesPreservation() {
        val (subDir, fileName) = SocketTransferServer.sanitizeRelativePath("dossier_août/rapport_août_2026.pdf")
        assertEquals("dossier_août", subDir)
        assertEquals("rapport_août_2026.pdf", fileName)
    }
}
