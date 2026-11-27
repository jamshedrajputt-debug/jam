package com.flyexj.assistant
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
class LocalCommandRouter(private val context: Context) {
    data class Result(val handled: Boolean, val message: String)
    fun handle(raw: String): Result {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return Result(false, "I didn't hear a command.")
        fun has(vararg phrases: String) = phrases.any { phrase ->
            if (phrase.all { it.isLetterOrDigit() && it.code < 128 }) {
                Regex("(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])").containsMatchIn(text)
            } else text.contains(phrase)
        }
        if (has("wifi", "wi-fi", "wi fi", "وائی فائی")) return open(Intent(Settings.ACTION_WIFI_SETTINGS), "Opening Wi-Fi settings")
        if (has("bluetooth", "بلوٹوتھ")) return open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings")
        if (has("display settings", "screen settings", "display setting")) return open(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Opening display settings")
        if (has("settings", "setting", "سیٹنگز")) return open(Intent(Settings.ACTION_SETTINGS), "Opening Settings")
        if (has("whatsapp", "whats app", "واٹس ایپ")) return launchPackage("com.whatsapp", "WhatsApp")
        if (has("youtube", "یوٹیوب")) return launchPackage("com.google.android.youtube", "YouTube")
        if (has("chrome", "browser", "internet", "کروم")) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
            return open(launch, "Opening browser")
        }
        if (has("maps", "google maps", "map", "نقشہ")) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=nearby"))
            return open(launch, "Opening Maps")
        }
        if (has("camera", "cam", "کیمرہ")) return open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opening Camera")
        if (has("gallery", "photos", "تصاویر")) return open(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, "Opening Gallery")
        if (has("spotify", "music")) return launchPackage("com.spotify.music", "Spotify")
        return Result(false, "Command not supported yet: $raw")
    }
    private fun launchPackage(packageName: String, label: String): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result(true, "$label isn't installed or can't be opened.")
        return open(launch, "Opening $label")
    }
    private fun open(intent: Intent, message: String): Result = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result(true, message)
    } catch (_: Exception) { Result(true, "Couldn't open that app or screen.") }
}