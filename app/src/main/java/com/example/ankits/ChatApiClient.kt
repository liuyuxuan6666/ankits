package com.example.ankits

import android.os.Handler
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ChatApiClient(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val streaming: Boolean,
    private val handler: Handler
) {
    var onToken: ((String) -> Unit)? = null
    var onComplete: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile
    private var cancelFlag = false

    companion object {
        fun normalizeEndpoint(raw: String): String {
            var url = raw.trim().trimEnd('/')
            // Strip known path suffixes to avoid doubling
            if (url.endsWith("/v1/chat/completions")) {
                url = url.removeSuffix("/v1/chat/completions")
            } else if (url.endsWith("/v1")) {
                url = url.removeSuffix("/v1")
            }
            if (url.isNotEmpty() && !url.startsWith("http")) {
                url = "https://$url"
            }
            return url
        }
    }

    fun cancel() {
        cancelFlag = true
    }

    fun sendMessage(messages: List<ChatMessage>, systemPrompt: String) {
        cancelFlag = false
        Thread {
            try {
                val base = Companion.normalizeEndpoint(endpoint)
                val url = URL("$base/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }

                val jsonBody = buildJsonBody(messages, systemPrompt)
                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    } catch (e: Exception) {
                        "Unknown error"
                    }
                    handler.post { onError?.invoke("HTTP $responseCode: $errorBody") }
                    conn.disconnect()
                    return@Thread
                }

                if (streaming) {
                    handleStreamingResponse(conn)
                } else {
                    handleNonStreamingResponse(conn)
                }

            } catch (e: Exception) {
                handler.post { onError?.invoke(e.message ?: "Network error") }
            }
        }.start()
    }

    private fun handleStreamingResponse(conn: HttpURLConnection) {
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val fullContent = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            if (cancelFlag) {
                reader.close()
                conn.disconnect()
                return
            }
            if (line!!.startsWith("data: ")) {
                val data = line!!.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val content = delta?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) {
                            fullContent.append(content)
                            handler.post { onToken?.invoke(content) }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        reader.close()
        conn.disconnect()
        handler.post { onComplete?.invoke(fullContent.toString()) }
    }

    private fun handleNonStreamingResponse(conn: HttpURLConnection) {
        val responseText = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        try {
            val json = JSONObject(responseText)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                handler.post { onComplete?.invoke(content) }
            } else {
                handler.post { onError?.invoke("No response from model") }
            }
        } catch (e: Exception) {
            handler.post { onError?.invoke("Failed to parse response") }
        }
    }

    private fun buildJsonBody(messages: List<ChatMessage>, systemPrompt: String): JSONObject {
        val body = JSONObject()
        body.put("model", model)
        body.put("stream", streaming)

        val msgsArray = JSONArray()

        if (systemPrompt.isNotEmpty()) {
            msgsArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        for (msg in messages) {
            if (msg.imageBase64 != null && msg.role == "user") {
                val contentArray = JSONArray()
                if (msg.content.isNotEmpty()) {
                    contentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", msg.content)
                    })
                }
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,${msg.imageBase64}")
                    })
                })
                msgsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            } else {
                msgsArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        body.put("messages", msgsArray)
        return body
    }
}
