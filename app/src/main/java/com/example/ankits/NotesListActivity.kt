package com.example.ankits

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ActivityNotesListBinding
import com.example.ankits.databinding.ItemNoteCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotesListBinding
    private lateinit var adapter: NoteAdapter
    private var allNotes: List<NoteMeta> = emptyList()
    private var searchQuery = ""
    private var isSelectMode = false
    private val selectedIds = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { exportZip(it) } }

    private val exportSingleLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { exportSingle(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFile(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleWindowInsets()

        adapter = NoteAdapter { noteMeta ->
            if (isSelectMode) {
                toggleSelection(noteMeta.id)
            } else {
                openEditor(noteMeta.id)
            }
        }
        adapter.onLongClick = { noteMeta ->
            if (!isSelectMode) enterSelectMode(noteMeta.id)
        }

        binding.noteList.layoutManager = LinearLayoutManager(this)
        binding.noteList.adapter = adapter

        binding.backBtn.setOnClickListener { finish() }
        binding.searchBtn.setOnClickListener { toggleSearch() }
        binding.moreBtn.setOnClickListener { showMoreMenu() }
        binding.cancelSearchBtn.setOnClickListener { closeSearch() }
        binding.newNoteFab.setOnClickListener { createNewNote() }
        binding.exportSelectedBtn.setOnClickListener { exportSelected() }
        binding.deleteSelectedBtn.setOnClickListener { confirmDeleteSelected() }

        setupSearch()
        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun loadNotes() {
        allNotes = NoteStorage.listNotes(this)
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (searchQuery.isBlank()) {
            allNotes
        } else {
            val q = searchQuery.lowercase()
            allNotes.filter { it.title.lowercase().contains(q) }
        }
        adapter.updateNotes(filtered)

        if (allNotes.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.noteList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.noteList.visibility = View.VISIBLE
        }
    }

    private fun openEditor(noteId: String) {
        val intent = Intent(this, NoteEditorActivity::class.java)
        intent.putExtra("note_id", noteId)
        startActivity(intent)
    }

    private fun createNewNote() {
        val note = Note()
        NoteStorage.saveNote(this, note)
        openEditor(note.id)
    }

    private fun toggleSearch() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            closeSearch()
        } else {
            binding.searchBar.visibility = View.VISIBLE
            binding.searchInput.requestFocus()
        }
    }

    private fun closeSearch() {
        binding.searchBar.visibility = View.GONE
        binding.searchInput.text?.clear()
        searchQuery = ""
        applyFilter()
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    searchQuery = s?.toString() ?: ""
                    applyFilter()
                }
                handler.postDelayed(searchRunnable!!, 200)
            }
        })
    }

    private fun enterSelectMode(initialId: String) {
        isSelectMode = true
        selectedIds.clear()
        selectedIds.add(initialId)
        binding.selectToolbar.visibility = View.VISIBLE
        updateSelectUI()
    }

    private fun exitSelectMode() {
        isSelectMode = false
        selectedIds.clear()
        binding.selectToolbar.visibility = View.GONE
        updateSelectUI()
    }

    private fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        if (selectedIds.isEmpty()) exitSelectMode()
        else updateSelectUI()
    }

    private fun updateSelectUI() {
        binding.selectCount.text = getString(R.string.notes_selected_count, selectedIds.size)
        adapter.notifyDataSetChanged()
    }

    private fun confirmDeleteSelected() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_delete_title))
            .setMessage(getString(R.string.notes_delete_confirm, selectedIds.size))
            .setPositiveButton(getString(R.string.pomodoro_confirm_yes)) { _, _ ->
                NoteStorage.deleteNotes(this, selectedIds.toSet())
                exitSelectMode()
                loadNotes()
            }
            .setNegativeButton(getString(R.string.pomodoro_confirm_no), null)
            .show()
    }

    private fun exportSelected() {
        val notes = selectedIds.mapNotNull { NoteStorage.loadNote(this, it) }
        if (notes.isEmpty()) return
        if (notes.size == 1) {
            exportSingleNote(notes.first())
        } else {
            exportLauncher.launch("notes_backup.zip")
        }
    }

    private fun exportZip(uri: Uri) {
        val notes = selectedIds.mapNotNull { NoteStorage.loadNote(this, it) }
        val ok = NoteImportExport.exportNotesAsZip(this, notes, uri)
        Toast.makeText(this,
            if (ok) getString(R.string.notes_export_success) else getString(R.string.notes_export_failed),
            Toast.LENGTH_SHORT).show()
        if (ok) { exitSelectMode(); loadNotes() }
    }

    private fun exportSingleNote(note: Note) {
        val filename = "${note.title.replace(Regex("""[\\/:*?"<>|]"""), "_")}.md"
        currentExportNote = note
        exportSingleLauncher.launch(filename)
    }

    private var currentExportNote: Note? = null

    private fun exportSingle(uri: Uri) {
        val note = currentExportNote ?: return
        currentExportNote = null
        val ok = NoteImportExport.exportNote(this, note, uri)
        Toast.makeText(this,
            if (ok) getString(R.string.notes_export_success) else getString(R.string.notes_export_failed),
            Toast.LENGTH_SHORT).show()
        if (ok) { exitSelectMode(); loadNotes() }
    }

    private fun importFile(uri: Uri) {
        var imported = 0
        val notes = if (uri.lastPathSegment?.endsWith(".zip", true) == true) {
            NoteImportExport.importZip(this, uri)
        } else {
            val note = NoteImportExport.importMarkdown(this, uri)
            if (note != null) listOf(note) else emptyList()
        }
        for (note in notes) {
            NoteStorage.saveNote(this, note)
            imported++
        }
        Toast.makeText(this,
            getString(R.string.notes_imported, imported),
            Toast.LENGTH_SHORT).show()
        if (imported > 0) loadNotes()
    }

    private fun showMoreMenu() {
        val items = arrayOf(
            getString(R.string.notes_import),
            getString(R.string.notes_export_all)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> importLauncher.launch(arrayOf(
                        "text/markdown", "application/zip", "application/octet-stream"))
                    1 -> exportAllNotes()
                }
            }
            .show()
    }

    private fun exportAllNotes() {
        val notes = allNotes.mapNotNull { NoteStorage.loadNote(this, it.id) }
        if (notes.isEmpty()) {
            Toast.makeText(this, getString(R.string.notes_empty_title), Toast.LENGTH_SHORT).show()
            return
        }
        if (notes.size == 1) {
            currentExportNote = notes.first()
            exportSingleLauncher.launch("${notes.first().title}.md")
        } else {
            exportLauncher.launch("notes_backup.zip")
        }
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}

class NoteAdapter(
    private val onClick: (NoteMeta) -> Unit
) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {

    var onLongClick: ((NoteMeta) -> Unit)? = null
    var isSelectMode = false
    var selectedIds: Set<String> = emptySet()
    private var notes: List<NoteMeta> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorStrip: View = ItemNoteCardBinding.bind(view).colorStrip
        val noteTitle: TextView = ItemNoteCardBinding.bind(view).noteTitle
        val notePreview: TextView = ItemNoteCardBinding.bind(view).notePreview
        val noteDate: TextView = ItemNoteCardBinding.bind(view).noteDate
        val pinIcon: ImageView = ItemNoteCardBinding.bind(view).pinIcon
        val selectCheckbox: CheckBox = ItemNoteCardBinding.bind(view).selectCheckbox
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meta = notes[position]
        holder.noteTitle.text = meta.title
        holder.notePreview.text = meta.preview
        holder.noteDate.text = formatDate(meta.updatedAt)
        holder.pinIcon.visibility = if (meta.pinned) View.VISIBLE else View.GONE

        val colorRes = noteColors[meta.color % noteColors.size]
        val ctx = holder.itemView.context
        holder.colorStrip.setBackgroundColor(ContextCompat.getColor(ctx, colorRes))

        if (isSelectMode) {
            holder.selectCheckbox.visibility = View.VISIBLE
            holder.selectCheckbox.isChecked = selectedIds.contains(meta.id)
        } else {
            holder.selectCheckbox.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(meta) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(meta)
            true
        }
    }

    override fun getItemCount() = notes.size

    fun updateNotes(newNotes: List<NoteMeta>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    companion object {
        val noteColors = listOf(
            R.color.primary,
            R.color.error,
            android.R.color.holo_orange_dark,
            android.R.color.holo_green_dark,
            android.R.color.holo_purple,
            android.R.color.holo_blue_dark
        )
    }
}
