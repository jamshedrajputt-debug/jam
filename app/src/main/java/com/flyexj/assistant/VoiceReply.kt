package com.flyexj.assistant
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
class VoiceReply(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val google = "com.google.android.tts"
    private val tts = TextToSpeech(app, this, google)
    private var ready = false
    private var language = "ur-PK"
    fun setLanguage(tag: String) { language = tag }
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS }
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        val clean = text.replace(Regex(" \\[[^\\]]+\\]$"), "").replace("*", "").replace("#", "")
        val urdu = clean.any { it in '\u0600'..'\u06FF' }
        val locale = if (urdu) Locale.forLanguageTag("ur-PK") else Locale.forLanguageTag(language)
        val available = tts.voices.orEmpty().filter { it.locale.language == locale.language && !it.features.contains("notInstalled") }
        val best = available.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == locale.country }.thenByDescending { it.quality }.thenBy { it.latency }).firstOrNull()
        if (best != null) tts.voice = best else if (tts.setLanguage(locale) < 0) tts.language = Locale.UK
        tts.setSpeechRate(0.97f)
        tts.setPitch(1.05f)
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "flyexj-reply")
    }
    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}
