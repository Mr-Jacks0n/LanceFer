package com.lancefer.lancedrop.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class WcagAaaTokensTest {

    private fun parseHex(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    private fun getRelativeLuminance(r: Int, g: Int, b: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val rLin = channel(r)
        val gLin = channel(g)
        val bLin = channel(b)
        return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
    }

    private fun getContrastRatio(hex1: String, hex2: String): Double {
        val (r1, g1, b1) = parseHex(hex1)
        val (r2, g2, b2) = parseHex(hex2)
        val l1 = getRelativeLuminance(r1, g1, b1)
        val l2 = getRelativeLuminance(r2, g2, b2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testLightModeTextTokensMeetWcagAaaThreshold() {
        val whiteCard = "#FFFFFF"
        val lightCanvas = "#F0F4F9"

        // Tokens définis dans Color.kt pour le thème clair
        val textTokens = mapOf(
            "LanceDropTextMainLightAAA" to "#0B1220",
            "LanceDropTextSecondaryLightAAA" to "#334155",
            "LanceDropTextMutedLightAAA" to "#384656",
            "LanceDropBlueLightAAA" to "#0B4CA5",
            "LanceDropSuccessTextLightAAA" to "#14532D"
        )

        for ((name, hex) in textTokens) {
            val ratioOnCard = getContrastRatio(hex, whiteCard)
            assertTrue(
                "Light mode $name ($hex) on $whiteCard must achieve >= 7.0:1 (got $ratioOnCard)",
                ratioOnCard >= 7.0
            )

            val ratioOnCanvas = getContrastRatio(hex, lightCanvas)
            assertTrue(
                "Light mode $name ($hex) on $lightCanvas must achieve >= 7.0:1 (got $ratioOnCanvas)",
                ratioOnCanvas >= 7.0
            )
        }
    }

    @Test
    fun testDarkModeTextTokensMeetWcagAaaThreshold() {
        val darkCard = "#161F30"
        val darkCanvas = "#0B0F17"

        // Tokens définis dans Color.kt pour le thème sombre
        val textTokens = mapOf(
            "LanceDropTextMainDarkAAA" to "#F8FAFC",
            "LanceDropTextSecondaryDarkAAA" to "#E2E8F0",
            "LanceDropTextMutedDarkAAA" to "#CBD5E1",
            "LanceDropBlueDarkAAA" to "#00D2FF"
        )

        for ((name, hex) in textTokens) {
            val ratioOnCard = getContrastRatio(hex, darkCard)
            assertTrue(
                "Dark mode $name ($hex) on $darkCard must achieve >= 7.0:1 (got $ratioOnCard)",
                ratioOnCard >= 7.0
            )

            val ratioOnCanvas = getContrastRatio(hex, darkCanvas)
            assertTrue(
                "Dark mode $name ($hex) on $darkCanvas must achieve >= 7.0:1 (got $ratioOnCanvas)",
                ratioOnCanvas >= 7.0
            )
        }
    }

    @Test
    fun testActionButtonsMeetWcagAaaThreshold() {
        // Bouton primaire Light: blanc sur bleu #0B4CA5
        val lightButtonRatio = getContrastRatio("#FFFFFF", "#0B4CA5")
        assertTrue("Light button label on background must achieve >= 7.0:1 (got $lightButtonRatio)", lightButtonRatio >= 7.0)

        // Bouton primaire Dark: texte sombre #070C14 sur Electric Blue #00D2FF
        val darkButtonRatio = getContrastRatio("#070C14", "#00D2FF")
        assertTrue("Dark button label on background must achieve >= 7.0:1 (got $darkButtonRatio)", darkButtonRatio >= 7.0)
    }

    @Test
    fun testUiComponentsMeetWcagGraphicalThreshold() {
        val darkCard = "#161F30"
        val whiteCard = "#FFFFFF"

        // Bordures et icônes >= 3.0:1
        val borderRatio = getContrastRatio("#64748B", whiteCard)
        assertTrue("Interactive border on white card must achieve >= 3.0:1", borderRatio >= 3.0)

        val darkBorderRatio = getContrastRatio("#64748B", darkCard)
        assertTrue("Interactive border on dark card must achieve >= 3.0:1", darkBorderRatio >= 3.0)
    }
}
