package com.flyexj.assistant
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val prefs by lazy { getSharedPreferences("flyexj_ai", MODE_PRIVATE) }
    private var language = "ur-PK"
    private var requestId = 0
    private val history = ArrayDeque<String>()
    private fun cloud(text: String, test: Boolean = false) {
        val token = prefs.getString("token", "") ?: ""
        val callback = voiceCallback
        if (token.length < 24) { callback?.invoke(text,"Set your private access token in AI Settings first."); return }
        val id = ++requestId
        callback?.invoke(text,"Connecting to AI…")
        lifecycleScope.launch {
            val context = history.joinToString("\n").takeLast(1800)
            val instruction = if (language == "ur-PK") "Reply in natural Pakistani Urdu script for spoken pronunciation." else "Reply in English."
            val answer = withContext(Dispatchers.IO) { CloudAssistant.ask(token, "$instruction\nRecent conversation:\n$context\nCurrent user question: $text") }
            if (id == requestId) {
                callback?.invoke(text,answer)
                if (!test) {
                    if (!answer.startsWith("AI error") && !answer.startsWith("Cloud connection failed")) {
                        history.addLast("User: $text\nAssistant: $answer")
                        while (history.size > 4) history.removeFirst()
                        voiceReply.speak(answer)
                    }
                }
            }
        }
    }
    private var pendingCommand = ""
    private val contactPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val result = commandRouter.handle(pendingCommand)
        voiceCallback?.invoke(pendingCommand, result.message)
        voiceReply.speak(result.message)
    }
    private fun execute(text: String) {
        requestId++
        if (text.startsWith("AI:",true)) { cloud(text.substringAfter(':').trim()); return }
        if (commandRouter.isCall(text) && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingCommand = text
            contactPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
            return
        }
        val result = commandRouter.handle(text)
        if (!result.handled) { cloud(text); return }
        voiceReply.speak(result.message)
        voiceCallback?.invoke(text, result.message)
    }
    private var voiceCallback: ((String, String) -> Unit)? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceInput.start(language) else voiceCallback?.invoke("", "Microphone permission denied")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commandRouter = LocalCommandRouter(this)
        voiceReply = VoiceReply(this)
        voiceInput = VoiceInputManager(this, { text ->
            execute(text)
        }, { state -> voiceCallback?.invoke("", state) })
        setContent {
            FlyexJApp(initialToken = prefs.getString("token","") ?: "", onSaveToken = { prefs.edit().putString("token",it).apply() }, onTest = { callback -> voiceCallback = callback; cloud("Reply with a short greeting in Urdu.",true) }, onLanguage = { language = it; voiceReply.setLanguage(it) }, onTyped = { text, callback -> voiceCallback = callback; execute(text) }, onListen = { callback ->
                voiceCallback = callback
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voiceInput.start(language)
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
fun FlyexJApp(initialToken: String, onSaveToken: (String) -> Unit, onTest: ((String,String) -> Unit) -> Unit, onLanguage: (String) -> Unit, onTyped: (String, (String, String) -> Unit) -> Unit, onListen: ((String, String) -> Unit) -> Unit, onStop: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(initialToken) }
    var typed by remember { mutableStateOf("") }
    var urdu by remember { mutableStateOf(true) }
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Tap Start talking and speak naturally") }
    var status by remember { mutableStateOf("Ready when you are") }
    val bg = Color(0xFF070B13)
    val cyan = Color(0xFF53D9FF)
    MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = bg, surface = Color(0xFF111927))) {
        Column(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, Color(0xFF101C31))))
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("FlyexJ", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = { showSettings = true }) { Text("AI Settings / ترتیبات") }
            if (showSettings) {
                AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Cloud AI connection") }, text = {
                    Column {
                        Text("GPT via OpenRouter, with Gemini and Claude fallback. Unmatched commands are sent to the cloud. Prefix AI: to ask directly.")
                        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Private access token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    }
                }, confirmButton = { TextButton(onClick = { onSaveToken(token.trim()); showSettings = false; status = "Token saved. Test connection next." }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } })
            }
            TextButton(onClick = { onTest { _, reply -> status = reply } }) { Text("Test AI connection") }
            Text("PERSONAL VOICE ASSISTANT", fontSize = 11.sp, letterSpacing = 3.sp, color = cyan)
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(156.dp).background(Color(0x223BD4FF), CircleShape))
                Box(Modifier.size(136.dp).background(Color(0x333BD4FF), CircleShape))
                Box(Modifier.size(116.dp).background(Brush.radialGradient(listOf(Color(0xFF5BE4FF), Color(0xFF2668C9), Color(0xFF101B35))), CircleShape))
                Text(if (listening) "LISTENING" else "FLYEXJ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(26.dp))
            Text(status, color = Color(0xFFB7C7DC), fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text(transcript, color = Color(0xFF8296B1), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = urdu, onClick = { urdu = true; onLanguage("ur-PK") }, label = { Text("اردو") })
                FilterChip(selected = !urdu, onClick = { urdu = false; onLanguage("en-GB") }, label = { Text("English") })
            }
            OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type a command / حکم لکھیں") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { if (typed.isNotBlank()) { val command = typed; typed = ""; onTyped(command) { text, reply -> transcript = text; status = reply; listening = false } } }, modifier = Modifier.fillMaxWidth()) { Text("Send command") }
            Text("Try: Ammi ko call karo • ٹارچ آن کرو", color = cyan, fontSize = 12.sp)
            Text("Calls • Contacts • Phone controls • Search", color = Color(0xFF8296B1), fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}