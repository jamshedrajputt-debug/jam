package com.flyexj.assistant
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
object CloudAssistant {
    fun ask(token: String, prompt: String): String {
        if (prompt.isBlank()) return "Please enter a question."
        var connection: HttpsURLConnection? = null
        return try {
            val c = URL("https://ihetpirvwybpfszfhhgs.supabase.co/functions/v1/flyexj-ai").openConnection() as HttpsURLConnection
            connection = c
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 55000
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("prompt",prompt.take(4000)).toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val body = (if(code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(body)
            if(code in 200..299) json.optString("text","Empty AI response") + " [" + json.optString("provider","AI") + "]"
            else "AI error (" + code + "): " + json.optString("error","Connection failed") + " " + (json.optJSONArray("providers")?.toString() ?: "")
        } catch (_: Exception) { "Cloud connection failed. Check internet and try again." } finally { connection?.disconnect() }
    }
}