package com.example.ankits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NoteMeta(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val color: Int
)

object NoteStorage {

    @Synchronized
    fun listNotes(context: Context): List<NoteMeta> {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) return emptyList()
        return try {
            val plain = NoteCrypto.decrypt(indexFile.readBytes())
            val json = JSONArray(String(plain))
            val list = mutableListOf<NoteMeta>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(NoteMeta(
                    id = obj.getString("id"),
                    title = obj.optString("title", "Untitled"),
                    preview = obj.optString("preview", ""),
                    updatedAt = obj.optLong("updatedAt", 0),
                    pinned = obj.optBoolean("pinned", false),
                    color = obj.optInt("color", 0)
                ))
            }
            list.sortedWith(compareByDescending<NoteMeta> { it.pinned }.thenByDescending { it.updatedAt })
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun loadNote(context: Context, noteId: String): Note? {
        val file = getNoteFile(context, noteId)
        if (!file.exists()) return null
        return try {
            val plain = NoteCrypto.decrypt(file.readBytes())
            noteFromJson(JSONObject(String(plain)))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun saveNote(context: Context, note: Note) {
        val updated = note.copy(updatedAt = System.currentTimeMillis())
        val json = noteToJson(updated)
        val encrypted = NoteCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8))
        val file = getNoteFile(context, updated.id)
        val tmp = File(file.parentFile, "${updated.id}.tmp")
        tmp.writeBytes(encrypted)
        tmp.renameTo(file)
        updateIndex(context, NoteMeta(
            id = updated.id,
            title = updated.title,
            preview = derivePreview(updated.content),
            updatedAt = updated.updatedAt,
            pinned = updated.pinned,
            color = updated.color
        ))
    }

    @Synchronized
    fun deleteNote(context: Context, noteId: String) {
        getNoteFile(context, noteId).delete()
        removeFromIndex(context, noteId)
    }

    @Synchronized
    fun deleteNotes(context: Context, noteIds: Set<String>) {
        for (id in noteIds) {
            getNoteFile(context, id).delete()
        }
        val remaining = listNotes(context).filter { it.id !in noteIds }
        saveIndex(context, remaining)
    }

    fun getNotesDir(context: Context): File {
        val dir = File(context.filesDir, "notes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getNoteFile(context: Context, noteId: String): File {
        return File(getNotesDir(context), "$noteId.enc")
    }

    private fun getIndexFile(context: Context): File {
        return File(getNotesDir(context), "index.enc")
    }

    private fun updateIndex(context: Context, meta: NoteMeta) {
        val notes = listNotes(context).toMutableList()
        notes.removeAll { it.id == meta.id }
        notes.add(meta)
        saveIndex(context, notes)
    }

    private fun removeFromIndex(context: Context, noteId: String) {
        val notes = listNotes(context).toMutableList()
        notes.removeAll { it.id == noteId }
        saveIndex(context, notes)
    }

    @Synchronized
    private fun saveIndex(context: Context, notes: List<NoteMeta>) {
        val arr = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("preview", n.preview)
            obj.put("updatedAt", n.updatedAt)
            obj.put("pinned", n.pinned)
            obj.put("color", n.color)
            arr.put(obj)
        }
        val encrypted = NoteCrypto.encrypt(arr.toString().toByteArray(Charsets.UTF_8))
        val indexFile = getIndexFile(context)
        val tmp = File(indexFile.parentFile, "index.tmp")
        tmp.writeBytes(encrypted)
        tmp.renameTo(indexFile)
    }

    fun deriveTitle(content: String): String {
        if (content.isBlank()) return "Untitled"
        val firstLine = content.trimStart().lines().firstOrNull()?.trim() ?: return "Untitled"
        if (firstLine.startsWith("# ")) {
            return firstLine.removePrefix("# ").trim().ifBlank { "Untitled" }
        }
        if (firstLine.length > 50) {
            return firstLine.take(50) + "..."
        }
        return firstLine.ifBlank { "Untitled" }
    }

    fun derivePreview(content: String, maxLen: Int = 80): String {
        if (content.isBlank()) return ""
        val lines = content.trimStart().lines()
        val bodyLines = mutableListOf<String>()
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("# ") || t.startsWith("```") || t == "---") continue
            bodyLines.add(t)
            if (bodyLines.size >= 3) break
        }
        val preview = bodyLines.joinToString(" ")
        return if (preview.length > maxLen) preview.take(maxLen) + "..." else preview
    }

    private fun noteToJson(note: Note): JSONObject {
        val obj = JSONObject()
        obj.put("id", note.id)
        obj.put("title", note.title)
        obj.put("content", note.content)
        obj.put("createdAt", note.createdAt)
        obj.put("updatedAt", note.updatedAt)
        val tagsArr = JSONArray()
        for (t in note.tags) tagsArr.put(t)
        obj.put("tags", tagsArr)
        obj.put("pinned", note.pinned)
        obj.put("color", note.color)
        return obj
    }

    private fun noteFromJson(json: JSONObject): Note? {
        return try {
            val tagsArr = json.optJSONArray("tags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(i))
            }
            Note(
                id = json.getString("id"),
                title = json.optString("title", "Untitled"),
                content = json.optString("content", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                tags = tags,
                pinned = json.optBoolean("pinned", false),
                color = json.optInt("color", 0)
            )
        } catch (e: Exception) {
            null
        }
    }
}
