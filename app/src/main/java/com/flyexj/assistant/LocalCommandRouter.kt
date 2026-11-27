package com.flyexj.assistant
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
class LocalCommandRouter(private val context: Context) {
    data class Result(val handled: Boolean, val message: String)
    fun isCall(raw: String): Boolean = Regex("(?i)\\b(call|dial|phone|fon)\\b|کال|فون|ملاؤ|ملائیں").containsMatchIn(raw)
    private fun dial(raw: String): Result {
        val name = raw.replace(Regex("(?i)\\b(call|dial|phone|fon|karo|kar|ko|mila|do|please|sim\\s*[12]|se)\\b|کال|فون|کرو|کریں|کو|ملا دو|ملائیں|سے|سم\\s*[۱۲12]"), " ").trim().replace(Regex("\\s+"), " ")
        if (name.isBlank()) return Result(true, "Please say a contact name or number.")
        val number = if (name.matches(Regex("[+0-9 ()-]{3,}"))) name else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return Result(true, "Contacts permission required.")
            val p = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val matches = mutableListOf<Pair<String,String>>()
            context.contentResolver.query(p, arrayOf("display_name","data1"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val n = cursor.getString(0) ?: continue
                    val v = cursor.getString(1) ?: continue
                    if (n.equals(name,true) || n.contains(name,true)) matches.add(n to v)
                }
            }
            val exact = matches.filter { it.first.equals(name,true) }.ifEmpty { matches }.distinctBy { it.second }
            if (exact.isEmpty()) return Result(true, "Contact not found: $name")
            if (exact.size > 1) return Result(true, "Multiple numbers found. Please say the full contact name or number.")
            exact.first().second
        }
        // Use the system dialer so the user can confirm the number and select SIM.
        return open(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null)), "Dialling $name. Confirm the call and SIM in your phone dialer.")
    }
    fun handle(raw: String): Result {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return Result(false, "I didn't hear a command.")
        fun has(vararg phrases: String) = phrases.any { phrase ->
            if (phrase.all { it.isLetterOrDigit() && it.code < 128 }) {
                Regex("(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])").containsMatchIn(text)
            } else text.contains(phrase)
        }
        if (isCall(text) && !has("whatsapp", "واٹس ایپ")) return dial(raw)
        if (has("torch","flashlight","ٹارچ","فلیش")) {
            return try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    ?: return Result(true,"Flashlight not available")
                val off = has("off","band","بند")
                cm.setTorchMode(id,!off)
                Result(true, if(off) "Flashlight off" else "Flashlight on")
            } catch (_: Exception) { Result(true,"Flashlight unavailable") }
        }
        if (has("battery","بیٹری")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            return Result(true,"Battery: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent")
        }
        if (has("volume","آواز","والیوم")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, if(has("down","kam","کم")) -1 else 1, android.media.AudioManager.FLAG_SHOW_UI)
            return Result(true,"Volume adjusted")
        }
        if (has("search","تلاش","سرچ","dhundo")) {
            val q = raw.replace(Regex("(?i)search|تلاش|سرچ|dhundo|karo|کرو")," ").trim()
            val url = if(has("youtube","یوٹیوب")) "https://www.youtube.com/results?search_query=" else "https://www.google.com/search?q="
            return open(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url+android.net.Uri.encode(q))),"Searching $q")
        }
        if (has("alarm","الارم")) return open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),"Opening alarms")
        if (has("timer","ٹائمر")) return open(Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS),"Opening timers")
        if (has("wifi", "wi-fi", "wi fi", "وائی فائی", "وائیفائی")) return open(Intent(Settings.ACTION_WIFI_SETTINGS), "Opening Wi-Fi settings")
        if (has("bluetooth", "بلوٹوتھ")) return open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings")
        if (has("display settings", "screen settings", "display setting")) return open(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Opening display settings")
        if (has("settings", "setting", "سیٹنگز", "ترتیبات")) return open(Intent(Settings.ACTION_SETTINGS), "Opening Settings")
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
        if (has("camera", "cam", "کیمرہ", "کیمرا")) return open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opening Camera")
        if (has("gallery", "photos", "تصاویر")) return open(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, "Opening Gallery")
        if (has("spotify", "music")) return launchPackage("com.spotify.music", "Spotify")
        return Result(false, "Try a call, app name, battery, torch, volume or search. / دوبارہ کوشش کریں")
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