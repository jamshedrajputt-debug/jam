package com.flyexj.assistant
import android.content.Context
import android.speech.tts.TextToSpeech
class VoiceReply(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS }
    fun speak(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flyexj-reply")
    }
    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}