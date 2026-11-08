package com.lancefer.lancedrop.utils

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object SoundEffectManager {
    var isSoundEnabled: Boolean = true

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w("SoundEffectManager", "ToneGenerator could not be initialized", e)
        }
    }

    fun playSuccessChime() {
        if (!isSoundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
        } catch (e: Exception) {
            Log.w("SoundEffectManager", "Failed to play success tone", e)
        }
    }

    fun playPairingTone() {
        if (!isSoundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
        } catch (e: Exception) {
            Log.w("SoundEffectManager", "Failed to play pairing tone", e)
        }
    }

    fun playErrorAlert() {
        if (!isSoundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 350)
        } catch (e: Exception) {
            Log.w("SoundEffectManager", "Failed to play error tone", e)
        }
    }
}
