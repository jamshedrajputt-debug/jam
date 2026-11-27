package com.flyexj.assistant

import android.app.*
import android.content.Intent
import android.os.*
import android.content.pm.ServiceInfo
import android.speech.SpeechRecognizer
import java.util.concurrent.Executors

class WakeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var input: VoiceInputManager
    private lateinit var voice: VoiceReply
    private val worker = Executors.newSingleThreadExecutor()
    private var running = false
    private var awaiting = false
    private var busy = false
    private var generation = 0
    private var language = "ur-PK"
    private val wake = Regex("(?i)(hey\\s*)?(fly\\s*ex\\s*j|flyxj|fly\\s*ex\\s*jay|فلائی\\s*ایکس\\s*جے|فلائی\\s*ایکس\\s*جی)")
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        voice = VoiceReply(this)
        input = VoiceInputManager(this, { heard ->
            if (running && !busy) {
                val match = wake.find(heard)
                if (match != null || awaiting) {
                    val command = if (match != null) heard.substring(match.range.last+1).trim() else heard.trim()
                    awaiting = command.isBlank()
                    if (awaiting) {
                        voice.speak(if(language == "ur-PK") "جی، بتائیں" else "Yes, I'm listening")
                        schedule(1800)
                    } else {
                        respond(command)
                    }
                } else schedule(500)
            }
        }, { state ->
            if (running && !busy && !state.startsWith("Listening") && !state.startsWith("Hearing") && !state.startsWith("Processing")) {
                awaiting = false
                schedule(1500)
            }
        })
    }
    private fun schedule(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (running && !busy) input.start(language) }, delay)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        language = intent?.getStringExtra("language") ?: "ur-PK"
        voice.setLanguage(language)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wake", "FlyexJ hands-free microphone", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this,"wake").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("FlyexJ hands-free listening").setContentText("Say Hey FlyexJ • Tap Stop to disable")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(41,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(41,notification)
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        running = true
        schedule(250)
        return START_NOT_STICKY
    }
    private fun respond(command: String) {
        busy = true
        input.stop()
        voice.stop()
        val id = ++generation
        val router = LocalCommandRouter(this)
        val result = if (router.isCall(command)) null else router.handle(command)
        if (router.isCall(command)) {
            finishReply(id, if(language == "ur-PK") "کال کرنے کے لیے نوٹیفیکیشن سے ایپ کھولیں۔" else "Open FlyexJ from the notification to confirm a call.")
            return
        }
        if (result?.handled == true) { finishReply(id,result.message); return }
        val token = getSharedPreferences("flyexj_ai",MODE_PRIVATE).getString("token","") ?: ""
        if (token.length < 24) { finishReply(id,"Set your AI access token in FlyexJ settings."); return }
        worker.execute {
            val instruction = if(language == "ur-PK") "Reply briefly in natural Pakistani Urdu script." else "Reply briefly in English."
            val answer = CloudAssistant.ask(token, "$"+"instruction\nUser: $"+"command")
            handler.post { finishReply(id,answer) }
        }
    }
    private fun finishReply(id: Int, answer: String) {
        if (!running || id != generation) return
        voice.speak(answer)
        busy = false
        schedule((answer.length * 85L).coerceIn(2000L,25000L))
    }
    override fun onDestroy() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        input.destroy()
        voice.shutdown()
        worker.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
