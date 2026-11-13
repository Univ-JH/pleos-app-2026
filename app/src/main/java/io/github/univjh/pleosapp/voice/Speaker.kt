package io.github.univjh.pleosapp.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Speaks short confirmations through the car's voice (Pleos TextToSpeech).
 * Voice is an extra: any failure is logged and the screen keeps working without it.
 * The emulator's on-device engine fails with ON_DEVICE_ENGINE_ERROR (docs/qa/SPIKE.md).
 */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun say(text: String) {
        runCatching {
            val engine = tts
            if (engine != null && ready) { engine.speak(text, true); return }
            val created = TextToSpeech(context, Mode.ON_DEVICE)
            tts = created
            created.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                    runCatching { created.setLocale(Locale.KOREAN); created.speak(text, true) }
                        .onFailure { Log.w(TAG, "speak failed: $it") }
                }
                override fun onFailure(error: ErrorType) { Log.w(TAG, "init failed: $error") }
            })
        }.onFailure { Log.w(TAG, "voice unavailable: $it") }
    }

    fun release() { runCatching { tts?.release() }; tts = null; ready = false }

    private companion object { const val TAG = "ChargeVoice" }
}
