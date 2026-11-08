package com.lancefer.lancedrop.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryManagerTest {

    @Test
    fun testScrubbingStoragePaths() {
        val raw = "Fichier /storage/emulated/0/Download/document_confidentiel.pdf introuvable"
        val clean = TelemetryManager.scrub(raw)

        assertFalse("Le chemin de stockage réel ne doit pas fuiter", clean.contains("/Download/document_confidentiel.pdf"))
        assertTrue("Le token [STORAGE_ROOT] doit être présent", clean.contains("[STORAGE_ROOT]"))
    }

    @Test
    fun testScrubbingAppPrivateDir() {
        val raw = "Erreur lecture /data/user/0/com.lancefer.lancedrop/databases/transfers.db"
        val clean = TelemetryManager.scrub(raw)

        assertFalse("Le package privé de l'app ne doit pas fuiter", clean.contains("/com.lancefer.lancedrop"))
        assertTrue("Le token [APP_PRIVATE_DIR] doit être présent", clean.contains("[APP_PRIVATE_DIR]"))
    }

    @Test
    fun testScrubbingAesKeyAndPin() {
        val rawKey = "Clé session: a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90 pour le flux"
        val cleanKey = TelemetryManager.scrub(rawKey)

        assertFalse(cleanKey.contains("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"))
        assertTrue(cleanKey.contains("[REDACTED_KEY_256]"))

        val rawPin = "Appairage avec pin: 456789 en attente"
        val cleanPin = TelemetryManager.scrub(rawPin)

        assertFalse(cleanPin.contains("456789"))
        assertTrue(cleanPin.contains("[REDACTED_PIN]"))
    }

    @Test
    fun testScrubbingPrivateIpAddresses() {
        val raw1 = "Pair détecté sur 192.168.43.15:42424"
        val clean1 = TelemetryManager.scrub(raw1)
        assertFalse(clean1.contains("192.168.43.15"))
        assertTrue(clean1.contains("192.168.*.*"))

        val raw2 = "Passerelle sur 10.1.2.3"
        val clean2 = TelemetryManager.scrub(raw2)
        assertFalse(clean2.contains("10.1.2.3"))
        assertTrue(clean2.contains("10.*.*.*"))
    }

    @Test
    fun testBreadcrumbsBuffer() {
        for (i in 1..60) {
            TelemetryManager.addBreadcrumb("test", "Message $i", mapOf("index" to i))
        }

        val crumbs = TelemetryManager.getRecentBreadcrumbs()
        assertTrue("Les breadcrumbs doivent être bornés à 50", crumbs.size <= 50)
        assertEquals("Message 60", crumbs.last().message)
    }

    @Test
    fun testClarityConfig() {
        val cfg = TelemetryManager.getClarityConfig()
        assertTrue(cfg.containsKey("projectId"))
        assertEquals("true", cfg["maskSensitive"])
    }
}
