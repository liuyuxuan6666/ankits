package com.example.ankits

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityNoteEditorBinding
import java.io.File

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private var noteId: String? = null
    private var note: Note? = null
    private var currentMode = EditorMode.EDIT
    private val handler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null
    private var isDirty = false

    private enum class EditorMode { EDIT, SPLIT, PREVIEW }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { exportNote(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleWindowInsets()

        noteId = intent.getStringExtra("note_id")

        if (noteId != null) {
            note = NoteStorage.loadNote(this, noteId!!)
        }
        if (note == null) {
            note = Note()
            noteId = note!!.id
        }

        binding.toolbarTitle.text = note!!.title
        binding.noteContent.setText(note!!.content)

        binding.backBtn.setOnClickListener { saveAndFinish() }
        binding.pinBtn.setOnClickListener { togglePin() }
        binding.moreBtn.setOnClickListener { showMoreMenu() }

        binding.modeEditBtn.setOnClickListener { setMode(EditorMode.EDIT) }
        binding.modeSplitBtn.setOnClickListener { setMode(EditorMode.SPLIT) }
        binding.modePreviewBtn.setOnClickListener { setMode(EditorMode.PREVIEW) }

        binding.noteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                isDirty = true
                schedulePreviewUpdate()
            }
        })

        updatePinIcon()
        setMode(EditorMode.EDIT)
        updatePreview()
    }

    override fun onPause() {
        super.onPause()
        saveNote()
    }

    override fun onBackPressed() {
        saveAndFinish()
    }

    private fun saveAndFinish() {
        saveNote()
        finish()
    }

    private fun saveNote() {
        val current = note ?: return
        val content = binding.noteContent.text?.toString() ?: ""
        val title = NoteStorage.deriveTitle(content)
        val updated = current.copy(
            title = title,
            content = content
        )
        NoteStorage.saveNote(this, updated)
        note = updated
        isDirty = false
    }

    private fun schedulePreviewUpdate() {
        previewRunnable?.let { handler.removeCallbacks(it) }
        previewRunnable = Runnable { updatePreview() }
        handler.postDelayed(previewRunnable!!, 300)
    }

    private fun updatePreview() {
        val content = binding.noteContent.text?.toString() ?: ""
        if (content.isBlank()) {
            binding.notePreview.text = ""
            return
        }
        val formatted = MarkdownParser.formatMessage(content)
        binding.notePreview.text = formatted

        val title = NoteStorage.deriveTitle(content)
        binding.toolbarTitle.text = title
    }

    private fun setMode(mode: EditorMode) {
        currentMode = mode

        val primary = ContextCompat.getColor(this, R.color.primary)
        val onSurface = ContextCompat.getColor(this, R.color.on_surface)
        val outline = ContextCompat.getColor(this, R.color.outline)

        fun styleButton(btn: com.google.android.material.button.MaterialButton, active: Boolean) {
            if (active) {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_container))
                btn.setTextColor(primary)
            } else {
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btn.setTextColor(outline)
            }
        }

        styleButton(binding.modeEditBtn, mode == EditorMode.EDIT)
        styleButton(binding.modeSplitBtn, mode == EditorMode.SPLIT)
        styleButton(binding.modePreviewBtn, mode == EditorMode.PREVIEW)

        val editParams = binding.editScroll.layoutParams as android.widget.LinearLayout.LayoutParams
        val previewParams = binding.previewScroll.layoutParams as android.widget.LinearLayout.LayoutParams

        when (mode) {
            EditorMode.EDIT -> {
                editParams.weight = 1f
                binding.editScroll.visibility = View.VISIBLE
                binding.divider.visibility = View.GONE
                previewParams.weight = 0f
                binding.previewScroll.visibility = View.GONE
            }
            EditorMode.SPLIT -> {
                editParams.weight = 1f
                binding.editScroll.visibility = View.VISIBLE
                binding.divider.visibility = View.VISIBLE
                previewParams.weight = 1f
                binding.previewScroll.visibility = View.VISIBLE
            }
            EditorMode.PREVIEW -> {
                editParams.weight = 0f
                binding.editScroll.visibility = View.GONE
                binding.divider.visibility = View.GONE
                previewParams.weight = 1f
                binding.previewScroll.visibility = View.VISIBLE
            }
        }
        binding.editScroll.layoutParams = editParams
        binding.previewScroll.layoutParams = previewParams
    }

    private fun togglePin() {
        val current = note ?: return
        note = current.copy(pinned = !current.pinned)
        updatePinIcon()
        saveNote()
    }

    private fun updatePinIcon() {
        val pinned = note?.pinned ?: false
        if (pinned) {
            binding.pinBtn.setImageResource(R.drawable.ic_favorites)
            binding.pinBtn.setColorFilter(ContextCompat.getColor(this, R.color.primary))
        } else {
            binding.pinBtn.setImageResource(R.drawable.ic_favorite_border)
            binding.pinBtn.colorFilter = null
        }
    }

    private fun showMoreMenu() {
        val items = arrayOf(
            getString(R.string.notes_export),
            getString(R.string.notes_share),
            getString(R.string.notes_pick_color),
            getString(R.string.notes_delete)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportCurrentNote()
                    1 -> shareNote()
                    2 -> pickColor()
                    3 -> confirmDelete()
                }
            }
            .show()
    }

    private fun exportCurrentNote() {
        val current = note ?: return
        saveNote()
        val filename = "${note!!.title.replace(Regex("""[\\/:*?"<>|]"""), "_")}.md"
        exportLauncher.launch(filename)
    }

    private fun exportNote(uri: Uri) {
        val current = note ?: return
        val ok = NoteImportExport.exportNote(this, current, uri)
        Toast.makeText(this,
            if (ok) getString(R.string.notes_export_success) else getString(R.string.notes_export_failed),
            Toast.LENGTH_SHORT).show()
    }

    private fun shareNote() {
        saveNote()
        val current = note ?: return
        val md = NoteImportExport.noteToMarkdown(current)
        val file = File(cacheDir, "shared_notes")
        file.mkdirs()
        val mdFile = File(file, "${current.title.replace(Regex("""[\\/:*?"<>|]"""), "_")}.md")
        mdFile.writeText(md)
        val uri = FileProvider.getUriForFile(this,
            "${packageName}.fileprovider", mdFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.notes_share)))
    }

    private fun pickColor() {
        val colorNames = arrayOf("Blue", "Red", "Orange", "Green", "Purple", "Dark Blue")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_pick_color))
            .setItems(colorNames) { _, which ->
                note = note?.copy(color = which)
                saveNote()
            }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_delete_title))
            .setMessage(getString(R.string.notes_delete_single_confirm))
            .setPositiveButton(getString(R.string.pomodoro_confirm_yes)) { _, _ ->
                noteId?.let { NoteStorage.deleteNote(this, it) }
                finish()
            }
            .setNegativeButton(getString(R.string.pomodoro_confirm_no), null)
            .show()
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(navBar.bottom, ime.bottom)
            binding.rootLayout.setPadding(0, statusBar.top, 0, bottomInset)
            WindowInsetsCompat.CONSUMED
        }
    }
}
