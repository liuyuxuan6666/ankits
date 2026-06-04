package com.example.ankits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ChatStorage {

    data class SessionMeta(
        val id: String,
        val title: String,
        val updatedAt: Long
    )

    @Synchronized
    fun listSessions(context: Context): List<SessionMeta> {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) return emptyList()
        val json = JSONArray(indexFile.readText())
        val list = mutableListOf<SessionMeta>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            list.add(SessionMeta(
                id = obj.getString("id"),
                title = obj.getString("title"),
                updatedAt = obj.getLong("updatedAt")
            ))
        }
        return list.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun loadSession(context: Context, sessionId: String): ChatSession? {
        val file = getSessionFile(context, sessionId)
        if (!file.exists()) return null
        return sessionFromJson(JSONObject(file.readText()))
    }

    @Synchronized
    fun saveSession(context: Context, session: ChatSession) {
        session.updatedAt = System.currentTimeMillis()
        val file = getSessionFile(context, session.id)
        file.writeText(sessionToJson(session).toString())
        updateIndex(context, SessionMeta(session.id, session.title, session.updatedAt))
    }

    @Synchronized
    fun deleteSession(context: Context, sessionId: String) {
        getSessionFile(context, sessionId).delete()
        removeFromIndex(context, sessionId)
    }

    fun createSession(context: Context): ChatSession {
        val session = ChatSession()
        saveSession(context, session)
        return session
    }

    private fun getSessionsDir(context: Context): File {
        val dir = File(context.filesDir, "chat_sessions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getSessionFile(context: Context, sessionId: String): File {
        return File(getSessionsDir(context), "$sessionId.json")
    }

    fun getIndexFile(context: Context): File {
        return File(getSessionsDir(context), "sessions_index.json")
    }

    // --- Index operations ---

    @Synchronized
    fun saveSessionIndex(context: Context, sessions: List<SessionMeta>) {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("updatedAt", s.updatedAt)
            arr.put(obj)
        }
        val indexFile = getIndexFile(context)
        val tmp = File(indexFile.parentFile, "sessions_index.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    private fun updateIndex(context: Context, meta: SessionMeta) {
        val sessions = listSessions(context).toMutableList()
        sessions.removeAll { it.id == meta.id }
        sessions.add(meta)
        saveSessionIndex(context, sessions)
    }

    private fun removeFromIndex(context: Context, sessionId: String) {
        val sessions = listSessions(context).toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessionIndex(context, sessions)
    }

    // --- JSON serialization ---

    private fun sessionToJson(session: ChatSession): JSONObject {
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("title", session.title)
        obj.put("createdAt", session.createdAt)
        obj.put("updatedAt", session.updatedAt)
        obj.put("systemPrompt", session.systemPrompt)
        obj.put("model", session.model)
        obj.put("maxContextTokens", session.maxContextTokens)
        val msgs = JSONArray()
        for (msg in session.messages) {
            msgs.put(messageToJson(msg))
        }
        obj.put("messages", msgs)
        return obj
    }

    private fun sessionFromJson(json: JSONObject): ChatSession? {
        return try {
            val msgs = mutableListOf<ChatMessage>()
            val msgsArr = json.getJSONArray("messages")
            for (i in 0 until msgsArr.length()) {
                val msg = messageFromJson(msgsArr.getJSONObject(i))
                if (msg != null) msgs.add(msg)
            }
            ChatSession(
                id = json.getString("id"),
                title = json.optString("title", "New Chat"),
                messages = msgs,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                systemPrompt = json.optString("systemPrompt", ""),
                model = json.optString("model", ""),
                maxContextTokens = json.optInt("maxContextTokens", 4000)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun messageToJson(msg: ChatMessage): JSONObject {
        val obj = JSONObject()
        obj.put("id", msg.id)
        obj.put("role", msg.role)
        obj.put("content", msg.content)
        obj.put("timestamp", msg.timestamp)
        return obj
    }

    private fun messageFromJson(json: JSONObject): ChatMessage? {
        return try {
            ChatMessage(
                id = json.getString("id"),
                role = json.getString("role"),
                content = json.getString("content"),
                imageBase64 = null,
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}
