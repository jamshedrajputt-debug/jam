package com.flyexj.assistant
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var voiceInput: VoiceInputManager
    private lateinit var commandRouter: LocalCommandRouter
    private lateinit var voiceReply: VoiceReply
    private var voiceCallback: ((String, String) -> Unit)? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceInput.start() else voiceCallback?.invoke("", "Microphone permission denied")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commandRouter = LocalCommandRouter(this)
        voiceReply = VoiceReply(this)
        voiceInput = VoiceInputManager(this, { text ->
            val result = commandRouter.handle(text)
            val response = if (result.handled) result.message else "I heard: $text. ${result.message}"
            voiceReply.speak(response)
            voiceCallback?.invoke(text, response)
        }, { state -> voiceCallback?.invoke("", state) })
        setContent {
            FlyexJApp(onListen = { callback ->
                voiceCallback = callback
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voiceInput.start()
                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }, onStop = { voiceInput.stop(); voiceReply.stop() })
        }
    }
    override fun onDestroy() {
        if (::voiceInput.isInitialized) voiceInput.destroy()
        if (::voiceReply.isInitialized) voiceReply.shutdown()
        super.onDestroy()
    }
}
@Composable
fun FlyexJApp(onListen: ((String, String) -> Unit) -> Unit, onStop: () -> Unit) {
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Tap Start talking and speak naturally") }
    var status by remember { mutableStateOf("Ready when you are") }
    val bg = Color(0xFF070B13)
    val cyan = Color(0xFF53D9FF)
    MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = bg, surface = Color(0xFF111927))) {
        Column(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, Color(0xFF101C31))))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("FlyexJ", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("PERSONAL VOICE ASSISTANT", fontSize = 11.sp, letterSpacing = 3.sp, color = cyan)
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(236.dp).background(Color(0x223BD4FF), CircleShape))
                Box(Modifier.size(196.dp).background(Color(0x333BD4FF), CircleShape))
                Box(Modifier.size(156.dp).background(Brush.radialGradient(listOf(Color(0xFF5BE4FF), Color(0xFF2668C9), Color(0xFF101B35))), CircleShape))
                Text(if (listening) "LISTENING" else "FLYEXJ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(26.dp))
            Text(status, color = Color(0xFFB7C7DC), fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text(transcript, color = Color(0xFF8296B1), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (!listening) {
                    listening = true
                    status = "Listening…"
                    onListen { text, newStatus ->
                        if (text.isNotBlank()) { transcript = text; listening = false }
                        status = newStatus
                        if (!newStatus.startsWith("Listening") && !newStatus.startsWith("Hearing") && !newStatus.startsWith("Processing")) listening = false
                    }
                } else { listening = false; status = "Ready when you are"; onStop() }
            }, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6CC8))) {
                Text(if (listening) "Stop listening" else "Start talking", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Urdu • Roman Urdu • English", color = cyan, fontSize = 12.sp)
            Text("Local app opening • Basic phone settings", color = Color(0xFF8296B1), fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}