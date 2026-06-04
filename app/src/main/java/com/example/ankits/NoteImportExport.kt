package com.example.ankits

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object NoteImportExport {

    fun noteToMarkdown(note: Note): String {
        val sb = StringBuilder()
        sb.appendLine("# ${note.title}")
        sb.appendLine()
        sb.append(note.content)
        return sb.toString()
    }

    fun markdownToNote(markdown: String): Note {
        val trimmed = markdown.trimStart()
        val title: String
        val content: String
        if (trimmed.startsWith("# ")) {
            val newlineIdx = trimmed.indexOf('\n')
            if (newlineIdx > 0) {
                title = trimmed.substring(2, newlineIdx).trim()
                content = trimmed.substring(newlineIdx + 1).trimStart()
            } else {
                title = trimmed.substring(2).trim()
                content = ""
            }
        } else {
            title = NoteStorage.deriveTitle(trimmed)
            content = trimmed
        }
        return Note(
            title = title.ifBlank { "Untitled" },
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun exportNote(context: Context, note: Note, uri: Uri): Boolean {
        return try {
            val md = noteToMarkdown(note)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(md.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun exportNotesAsZip(context: Context, notes: List<Note>, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    for (note in notes) {
                        val filename = "${sanitizeFilename(note.title)}.md"
                        zip.putNextEntry(ZipEntry(filename))
                        val md = noteToMarkdown(note)
                        zip.write(md.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun importMarkdown(context: Context, uri: Uri): Note? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { inp ->
                inp.readBytes().toString(Charsets.UTF_8)
            } ?: return null
            markdownToNote(content)
        } catch (e: Exception) {
            null
        }
    }

    fun importZip(context: Context, uri: Uri): List<Note> {
        val notes = mutableListOf<Note>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                ZipInputStream(inp).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (!entry.name.endsWith(".md", ignoreCase = true)) continue
                        val content = zip.readBytes().toString(Charsets.UTF_8)
                        notes.add(markdownToNote(content))
                        zip.closeEntry()
                    }
                }
            }
        } catch (_: Exception) {}
        return notes
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "note" }
    }

    private fun ZipInputStream.readBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var len: Int
        while (this.read(buf).also { len = it } != -1) {
            bos.write(buf, 0, len)
        }
        return bos.toByteArray()
    }
}
